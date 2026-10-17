'use strict';
// Loads the real Apps Script sources into a Node vm with in-memory fakes for
// DriveApp, UrlFetchApp (Firebase accounts:lookup), PropertiesService, etc.
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');

const SRC = path.join(__dirname, '..', 'src');
const FILES = ['Config.gs', 'Util.gs', 'Auth.gs', 'Storage.gs', 'Handlers.gs', 'Code.gs'];

function makeBlob(buf, mime, name) {
  return {
    getBytes: () => Array.from(buf, (b) => (b > 127 ? b - 256 : b)),
    getDataAsString: () => buf.toString('utf8'),
    getContentType: () => mime,
    getName: () => name,
  };
}

function makeDrive() {
  let seq = 0;
  const items = new Map();
  const wrappers = new Map();
  const state = { breakCreateFolder: false };

  function mk(kind, name, parentId, mime, buf) {
    const id = 'id_' + kind + '_' + ++seq;
    const it = {
      id, kind, name,
      mime: mime || (kind === 'folder' ? 'application/vnd.google-apps.folder' : 'application/octet-stream'),
      parents: parentId ? [parentId] : [],
      bytes: buf || Buffer.alloc(0),
      trashed: false,
    };
    items.set(id, it);
    return it;
  }
  const iter = (arr) => { let i = 0; return { hasNext: () => i < arr.length, next: () => arr[i++] }; };
  const children = (id, kind) =>
    [...items.values()].filter((x) => x.parents.includes(id) && (!kind || x.kind === kind) && !x.trashed);

  function wrap(it) {
    if (wrappers.has(it.id)) return wrappers.get(it.id);
    let w;
    if (it.kind === 'folder') {
      w = {
        getId: () => it.id,
        getName: () => it.name,
        isTrashed: () => it.trashed,
        createFolder: (name) => wrap(mk('folder', name, it.id)),
        createFile: (a, b, c) => {
          if (a && typeof a.getBytes === 'function') {
            const buf = Buffer.from(a.getBytes().map((x) => x & 0xff));
            return wrap(mk('file', a.getName(), it.id, a.getContentType(), buf));
          }
          return wrap(mk('file', a, it.id, c, Buffer.from(String(b), 'utf8')));
        },
        getFoldersByName: (n) => iter(children(it.id, 'folder').filter((x) => x.name === n).map(wrap)),
        getFilesByName: (n) => iter(children(it.id, 'file').filter((x) => x.name === n).map(wrap)),
      };
    } else {
      w = {
        getId: () => it.id,
        getName: () => it.name,
        getMimeType: () => it.mime,
        getSize: () => it.bytes.length,
        isTrashed: () => it.trashed,
        setTrashed: (v) => { it.trashed = !!v; return w; },
        getParents: () => iter(it.parents.map((p) => wrap(items.get(p)))),
        getBlob: () => makeBlob(it.bytes, it.mime, it.name),
        setContent: (s) => { it.bytes = Buffer.from(String(s), 'utf8'); return w; },
      };
    }
    wrappers.set(it.id, w);
    return w;
  }

  const DriveApp = {
    createFolder: (name) => {
      if (state.breakCreateFolder) throw new Error('boom');
      return wrap(mk('folder', name, null));
    },
    getFolderById: (id) => {
      const it = items.get(id);
      if (!it || it.kind !== 'folder') throw new Error('Folder not found');
      return wrap(it);
    },
    getFileById: (id) => {
      const it = items.get(id);
      if (!it || it.kind !== 'file') throw new Error('File not found');
      return wrap(it);
    },
  };

  return {
    DriveApp, items, state,
    findFolder: (name) => [...items.values()].find((i) => i.kind === 'folder' && i.name === name),
    files: (folderId) => children(folderId, 'file'),
    children,
  };
}

function createEnv(options = {}) {
  const drive = makeDrive();
  const store = options.noProps ? {} : Object.assign(
    { FIREBASE_API_KEY: 'test-api-key', FIREBASE_PROJECT_ID: 'test-project', ADMIN_EMAIL: 'owner@example.com' },
    options.props || {}
  );
  const logs = [];
  const fetchCalls = [];
  const accounts = new Map();
  const net = { lookupOverride: null };

  const enc = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  function makeJwt(payload) { return enc({ alg: 'RS256', typ: 'JWT' }) + '.' + enc(payload) + '.sig'; }

  function addUser({ uid, email, emailVerified = true, disabled = false, aud = 'test-project' }) {
    const token = makeJwt({
      iss: 'https://securetoken.google.com/' + aud, aud, user_id: uid, sub: uid, email,
      nonce: crypto.randomUUID(),
    });
    accounts.set(token, { localId: uid, email, emailVerified, disabled });
    return token;
  }
  // A token that decodes fine and looks right, but Firebase never issued it.
  function forgeToken(uid, email) {
    return makeJwt({
      iss: 'https://securetoken.google.com/test-project', aud: 'test-project',
      user_id: uid, sub: uid, email, nonce: crypto.randomUUID(),
    });
  }

  const sandbox = {
    console: {
      log: (...a) => logs.push(a.join(' ')),
      error: (...a) => logs.push(a.join(' ')),
      warn: (...a) => logs.push(a.join(' ')),
      info: (...a) => logs.push(a.join(' ')),
    },
    PropertiesService: {
      getScriptProperties: () => ({
        getProperty: (k) => (Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null),
        setProperty: (k, v) => { store[k] = String(v); },
      }),
    },
    DriveApp: drive.DriveApp,
    LockService: {
      getScriptLock: () => ({
        waitLock: () => { if (options.lockFails) throw new Error('lock timeout'); },
        releaseLock: () => {},
      }),
    },
    ContentService: {
      MimeType: { JSON: 'JSON' },
      createTextOutput: (text) => ({ text, setMimeType() { return this; } }),
    },
    Utilities: {
      base64Decode: (s) => Array.from(Buffer.from(s, 'base64'), (b) => (b > 127 ? b - 256 : b)),
      base64Encode: (bytes) => Buffer.from(Array.from(bytes, (x) => x & 0xff)).toString('base64'),
      newBlob: (bytes, mime, name) => makeBlob(Buffer.from(Array.from(bytes, (x) => x & 0xff)), mime, name),
      getUuid: () => crypto.randomUUID(),
    },
    UrlFetchApp: {
      fetch: (url, opts) => {
        const body = JSON.parse(opts.payload);
        fetchCalls.push({ url, idToken: body.idToken });
        const respond = (code, obj) => ({ getResponseCode: () => code, getContentText: () => JSON.stringify(obj) });
        if (net.lookupOverride) return respond(net.lookupOverride.code, net.lookupOverride.body);
        const rec = accounts.get(body.idToken);
        if (rec === 'EXPIRED') return respond(400, { error: { message: 'TOKEN_EXPIRED' } });
        if (!rec) return respond(400, { error: { message: 'INVALID_ID_TOKEN' } });
        return respond(200, { users: [rec] });
      },
    },
  };
  vm.createContext(sandbox);
  for (const f of FILES) {
    vm.runInContext(fs.readFileSync(path.join(SRC, f), 'utf8'), sandbox, { filename: f });
  }

  function call(body) {
    const contents = typeof body === 'string' ? body : JSON.stringify(body);
    const out = sandbox.doPost({ postData: { contents } });
    return JSON.parse(out.text);
  }

  return {
    ctx: sandbox, drive, store, logs, fetchCalls, net, call,
    addUser, forgeToken,
    expire: (token) => accounts.set(token, 'EXPIRED'),
    callAs: (token, action, params) => call(Object.assign({ action, idToken: token }, params || {})),
  };
}

module.exports = { createEnv };
