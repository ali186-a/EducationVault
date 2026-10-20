'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createEnv } = require('./harness');

// ---------- helpers ----------
const PDF = Buffer.from('%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n');
const PNG = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(32, 7)]);
const JPG = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(32, 9)]);
const EXE = Buffer.from('MZ\x90\x00this is not a document');

const up = (buf, mime, extra) =>
  Object.assign({ title: 'Matric Certificate', category: 'matric', mimeType: mime || 'application/pdf',
    fileName: 'scan.pdf', contentBase64: buf.toString('base64') }, extra || {});

function ok(res) { assert.equal(res.ok, true, 'expected ok, got ' + JSON.stringify(res)); return res.data; }
function fail(res, code) {
  assert.equal(res.ok, false, 'expected failure ' + code + ', got ' + JSON.stringify(res));
  assert.equal(res.error.code, code, 'got ' + JSON.stringify(res.error));
}
const plain = (x) => JSON.parse(JSON.stringify(x));

function setup(options) {
  // These tests cover the invitation system, which is an opt-in mode (REQUIRE_INVITE=true).
  const env = createEnv(Object.assign({}, options, { props: Object.assign({ REQUIRE_INVITE: 'true' }, (options || {}).props) }));
  const admin = env.addUser({ uid: 'adminUid001', email: 'Owner@Example.com' });
  const alice = env.addUser({ uid: 'aliceUid001', email: 'alice@example.com' });
  const bob = env.addUser({ uid: 'bobUid000002', email: 'bob@example.com' });
  const eve = env.addUser({ uid: 'eveUid000003', email: 'eve@example.com' });
  ok(env.callAs(admin, 'inviteUser', { email: 'Alice@Example.com ' }));
  ok(env.callAs(admin, 'inviteUser', { email: 'bob@example.com' }));
  return { env, admin, alice, bob, eve };
}

// ---------- request validation ----------
test('rejects invalid JSON, empty body, non-object JSON and unknown actions', () => {
  const { env } = setup();
  fail(env.call('{not json'), 'BAD_REQUEST');
  fail(env.call(''), 'BAD_REQUEST');
  fail(env.call('[1,2,3]'), 'BAD_REQUEST');
  fail(env.call('"hello"'), 'BAD_REQUEST');
  fail(env.call({ action: 'dropEverything' }), 'BAD_REQUEST');
  fail(env.call({}), 'BAD_REQUEST');
  fail(env.ctx.doPost({}) && JSON.parse(env.ctx.doPost({}).text), 'BAD_REQUEST');
});

test('rejects oversized request bodies', () => {
  const { env } = setup();
  fail(env.call('x'.repeat(17 * 1024 * 1024)), 'REQUEST_TOO_LARGE');
});

test('ping works without auth and reports configuration state', () => {
  assert.equal(ok(createEnv().call({ action: 'ping' })).configured, true);
  assert.equal(ok(createEnv({ noProps: true }).call({ action: 'ping' })).configured, false);
});

test('unconfigured backend refuses protected actions with NOT_CONFIGURED', () => {
  const env = createEnv({ noProps: true });
  fail(env.callAs('a'.repeat(100), 'listDocs'), 'NOT_CONFIGURED');
});

// ---------- authentication ----------
test('missing or malformed token is UNAUTHENTICATED', () => {
  const { env } = setup();
  fail(env.call({ action: 'listDocs' }), 'UNAUTHENTICATED');
  fail(env.callAs('short', 'listDocs'), 'UNAUTHENTICATED');
  fail(env.callAs(12345, 'listDocs'), 'UNAUTHENTICATED');
  fail(env.callAs('not.a.jwt-but-long-enough-string-xxxxxxxxxxxxxxxx', 'listDocs'), 'UNAUTHENTICATED');
});

test('a well-formed JWT that Firebase never issued is rejected (decoding alone is not trusted)', () => {
  const { env } = setup();
  const forged = env.forgeToken('aliceUid001', 'alice@example.com');
  fail(env.callAs(forged, 'listDocs'), 'UNAUTHENTICATED');
  const forgedAdmin = env.forgeToken('adminUid001', 'owner@example.com');
  fail(env.callAs(forgedAdmin, 'listInvites'), 'UNAUTHENTICATED');
  assert.ok(env.fetchCalls.length >= 2, 'backend must ask Firebase to verify');
});

test('token for a different Firebase project is rejected', () => {
  const { env } = setup();
  const other = env.addUser({ uid: 'otherUid001', email: 'alice@example.com', aud: 'someone-elses-project' });
  fail(env.callAs(other, 'listDocs'), 'UNAUTHENTICATED');
});

test('expired token -> TOKEN_EXPIRED; disabled user -> ACCOUNT_DISABLED', () => {
  const { env, alice } = setup();
  const bad = env.addUser({ uid: 'disabledU01', email: 'alice@example.com', disabled: true });
  fail(env.callAs(bad, 'listDocs'), 'ACCOUNT_DISABLED');
  env.expire(alice);
  fail(env.callAs(alice, 'listDocs'), 'TOKEN_EXPIRED');
  env.net.lookupOverride = { code: 400, body: { error: { message: 'USER_DISABLED' } } };
  fail(env.callAs(env.addUser({ uid: 'someUid0001', email: 'x@example.com' }), 'listDocs'), 'ACCOUNT_DISABLED');
});

test('Firebase outage -> UPSTREAM_ERROR (fails closed)', () => {
  const { env, alice } = setup();
  env.net.lookupOverride = { code: 503, body: {} };
  fail(env.callAs(alice, 'listDocs'), 'UPSTREAM_ERROR');
});

test('Firebase UID with path characters is refused', () => {
  const { env } = setup();
  const evil = env.addUser({ uid: '../evil/uid', email: 'alice@example.com' });
  fail(env.callAs(evil, 'listDocs'), 'UNAUTHENTICATED');
});

test('unverified email is refused; admin must always be verified', () => {
  const { env } = setup();
  const unverified = env.addUser({ uid: 'unverUid001', email: 'bob@example.com', emailVerified: false });
  fail(env.callAs(unverified, 'listDocs'), 'EMAIL_NOT_VERIFIED');
  const adminUnverified = env.addUser({ uid: 'adminUid999', email: 'owner@example.com', emailVerified: false });
  fail(env.callAs(adminUnverified, 'listInvites'), 'EMAIL_NOT_VERIFIED');

  const lax = setup({ props: { REQUIRE_EMAIL_VERIFIED: 'false' } });
  const bobLax = lax.env.addUser({ uid: 'bobLaxUid01', email: 'bob@example.com', emailVerified: false });
  ok(lax.env.callAs(bobLax, 'listDocs'));
  const adminLax = lax.env.addUser({ uid: 'adminLax001', email: 'owner@example.com', emailVerified: false });
  fail(lax.env.callAs(adminLax, 'listInvites'), 'EMAIL_NOT_VERIFIED'); // never relaxed for admin
});

// ---------- allowlist ----------
test('uninvited account cannot touch documents', () => {
  const { env, eve } = setup();
  fail(env.callAs(eve, 'listDocs'), 'NOT_INVITED');
  fail(env.callAs(eve, 'upload', up(PDF)), 'NOT_INVITED');
  fail(env.callAs(eve, 'listCategories'), 'NOT_INVITED');
  assert.equal(env.drive.findFolder('eveUid000003'), undefined, 'no folder created for uninvited user');
});

test('invited users work (email matching is case-insensitive); admin needs no invitation', () => {
  const { env, admin, alice } = setup();
  assert.equal(ok(env.callAs(alice, 'me')).isAdmin, false);
  const me = ok(env.callAs(admin, 'me'));
  assert.equal(me.isAdmin, true);
  assert.equal(me.email, 'owner@example.com');
  ok(env.callAs(admin, 'listDocs'));
});

test('revocation blocks immediately; re-invite restores access', () => {
  const { env, admin, alice } = setup();
  ok(env.callAs(alice, 'listDocs'));
  ok(env.callAs(admin, 'revokeUser', { email: 'alice@example.com' }));
  fail(env.callAs(alice, 'listDocs'), 'NOT_INVITED');
  fail(env.callAs(alice, 'upload', up(PDF)), 'NOT_INVITED');
  ok(env.callAs(admin, 'inviteUser', { email: 'alice@example.com' }));
  ok(env.callAs(alice, 'listDocs'));
});

test('invitation is bound to the first account that uses it', () => {
  const { env, alice } = setup();
  ok(env.callAs(alice, 'listDocs'));
  const impostor = env.addUser({ uid: 'impostor001', email: 'alice@example.com' });
  fail(env.callAs(impostor, 'listDocs'), 'FORBIDDEN');
});

// ---------- admin-only operations ----------
test('non-admins (even invited ones) cannot invite, revoke or list invitations', () => {
  const { env, alice, eve } = setup();
  fail(env.callAs(alice, 'inviteUser', { email: 'friend@example.com' }), 'FORBIDDEN');
  fail(env.callAs(alice, 'revokeUser', { email: 'bob@example.com' }), 'FORBIDDEN');
  fail(env.callAs(alice, 'listInvites'), 'FORBIDDEN');
  fail(env.callAs(eve, 'inviteUser', { email: 'eve@example.com' }), 'NOT_INVITED');
});

test('admin invitation validation and safeguards', () => {
  const { env, admin } = setup();
  fail(env.callAs(admin, 'inviteUser', { email: 'not-an-email' }), 'BAD_REQUEST');
  fail(env.callAs(admin, 'inviteUser', {}), 'BAD_REQUEST');
  fail(env.callAs(admin, 'inviteUser', { email: 'owner@example.com' }), 'BAD_REQUEST');
  fail(env.callAs(admin, 'revokeUser', { email: 'owner@example.com' }), 'BAD_REQUEST');
  fail(env.callAs(admin, 'revokeUser', { email: 'nobody@example.com' }), 'NOT_FOUND');
  const list = ok(env.callAs(admin, 'listInvites')).invites;
  assert.deepEqual(list.map((i) => i.email).sort(), ['alice@example.com', 'bob@example.com']);
});

// ---------- file ownership ----------
test("users only see their own documents; others' docIds are NOT_FOUND for every operation", () => {
  const { env, alice, bob } = setup();
  const docA = ok(env.callAs(alice, 'upload', up(PDF))).doc;
  const docB = ok(env.callAs(bob, 'upload', up(PNG, 'image/png', { title: 'Bob ID' }))).doc;

  assert.deepEqual(ok(env.callAs(alice, 'listDocs')).items.map((d) => d.id), [docA.id]);
  assert.deepEqual(ok(env.callAs(bob, 'listDocs')).items.map((d) => d.id), [docB.id]);

  fail(env.callAs(bob, 'getDoc', { docId: docA.id }), 'NOT_FOUND');
  fail(env.callAs(bob, 'download', { docId: docA.id }), 'NOT_FOUND');
  fail(env.callAs(bob, 'updateDoc', { docId: docA.id, title: 'hacked' }), 'NOT_FOUND');
  fail(env.callAs(bob, 'deleteDoc', { docId: docA.id }), 'NOT_FOUND');

  const aliceFolder = env.drive.findFolder('aliceUid001');
  const stored = env.drive.files(aliceFolder.id).filter((f) => f.name !== '_metadata.json');
  assert.equal(stored.length, 1);
  assert.equal(stored[0].trashed, false, "bob's delete attempt must not touch alice's file");
  assert.equal(ok(env.callAs(alice, 'getDoc', { docId: docA.id })).doc.title, 'Matric Certificate');
});

test('client-supplied Drive file IDs are never accepted', () => {
  const { env, alice, bob } = setup();
  ok(env.callAs(alice, 'upload', up(PDF)));
  const aliceFolder = env.drive.findFolder('aliceUid001');
  const driveFileId = env.drive.files(aliceFolder.id).find((f) => f.name.endsWith('.pdf')).id;
  fail(env.callAs(bob, 'download', { docId: driveFileId }), 'BAD_REQUEST');
  fail(env.callAs(bob, 'download', { fileId: driveFileId }), 'BAD_REQUEST');
  fail(env.callAs(bob, 'deleteDoc', { docId: driveFileId }), 'BAD_REQUEST');
  fail(env.callAs(bob, 'download', { docId: '../../etc/passwd' }), 'BAD_REQUEST');
});

test('defence in depth: tampered metadata pointing at another user\'s file is refused', () => {
  const { env, alice, bob } = setup();
  const docA = ok(env.callAs(alice, 'upload', up(PDF))).doc;
  ok(env.callAs(bob, 'upload', up(PNG, 'image/png', { title: 'Bob secret' })));

  const bobFolder = env.drive.findFolder('bobUid000002');
  const bobFile = env.drive.files(bobFolder.id).find((f) => f.name.endsWith('.png'));
  const aliceFolder = env.drive.findFolder('aliceUid001');
  const metaItem = env.drive.files(aliceFolder.id).find((f) => f.name === '_metadata.json');
  const meta = JSON.parse(metaItem.bytes.toString('utf8'));
  meta.docs[docA.id].fileId = bobFile.id;
  metaItem.bytes = Buffer.from(JSON.stringify(meta));

  fail(env.callAs(alice, 'download', { docId: docA.id }), 'NOT_FOUND');
  fail(env.callAs(alice, 'deleteDoc', { docId: docA.id }), 'NOT_FOUND');
  assert.equal(bobFile.trashed, false);
});

// ---------- upload validation ----------
test('files over the limit are rejected, never truncated, and nothing is stored', () => {
  const { env, alice } = setup({ props: { MAX_UPLOAD_BYTES: '1000' } });
  const big = Buffer.concat([PDF, Buffer.alloc(2000, 1)]);
  fail(env.callAs(alice, 'upload', up(big)), 'FILE_TOO_LARGE');
  const folder = env.drive.findFolder('aliceUid001');
  const stored = folder ? env.drive.files(folder.id).filter((f) => f.name !== '_metadata.json') : [];
  assert.equal(stored.length, 0);
  assert.equal(ok(env.callAs(alice, 'listDocs')).total, 0);
});

test('upload limit is configurable and capped at 10 MB', () => {
  const a = setup({ props: { MAX_UPLOAD_BYTES: '999999999' } });
  assert.equal(ok(a.env.callAs(a.alice, 'me')).maxUploadBytes, 10 * 1024 * 1024);
  const b = setup();
  assert.equal(ok(b.env.callAs(b.alice, 'me')).maxUploadBytes, 5 * 1024 * 1024);
  const c = setup({ props: { MAX_UPLOAD_BYTES: 'garbage' } });
  assert.equal(ok(c.env.callAs(c.alice, 'me')).maxUploadBytes, 5 * 1024 * 1024);
});

test('only PDF/JPG/PNG whose content matches the declared type are accepted', () => {
  const { env, alice } = setup();
  fail(env.callAs(alice, 'upload', up(EXE, 'application/pdf')), 'UNSUPPORTED_TYPE');
  fail(env.callAs(alice, 'upload', up(PNG, 'application/pdf')), 'UNSUPPORTED_TYPE');
  fail(env.callAs(alice, 'upload', up(PDF, 'image/png')), 'UNSUPPORTED_TYPE');
  fail(env.callAs(alice, 'upload', up(PDF, 'application/x-msdownload')), 'UNSUPPORTED_TYPE');
  fail(env.callAs(alice, 'upload', up(PDF, 'constructor')), 'UNSUPPORTED_TYPE');
  ok(env.callAs(alice, 'upload', up(JPG, 'image/jpeg')));
  ok(env.callAs(alice, 'upload', up(JPG, 'image/jpg')));
  ok(env.callAs(alice, 'upload', up(PNG, 'image/png')));
});

test('invalid base64 / empty content / missing fields are BAD_REQUEST', () => {
  const { env, alice } = setup();
  fail(env.callAs(alice, 'upload', up(PDF, null, { contentBase64: '%%%notbase64%%%' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { contentBase64: 'abc' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { contentBase64: '' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { contentBase64: 12345 })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { title: '' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { title: 'x'.repeat(121) })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { title: { a: 1 } })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { category: 'hackers' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { category: undefined })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { passingYear: 1800 })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { passingYear: 'abcd' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { issueDate: '2020-02-31' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { issueDate: '31/01/2020' })), 'BAD_REQUEST');
  fail(env.callAs(alice, 'upload', up(PDF, null, { notes: 'n'.repeat(1001) })), 'BAD_REQUEST');
  assert.equal(ok(env.callAs(alice, 'listDocs')).total, 0);
});

test('stored filename is server-generated; hostile original names are neutralised', () => {
  const { env, alice } = setup();
  const d1 = ok(env.callAs(alice, 'upload', up(PDF, null, { fileName: '../../etc/passwd' }))).doc;
  const d2 = ok(env.callAs(alice, 'upload', up(PDF, null, { fileName: '..\\..\\evil<>.pdf' }))).doc;
  assert.equal(d1.originalName, 'passwd');
  assert.equal(d2.originalName, 'evil.pdf');
  const folder = env.drive.findFolder('aliceUid001');
  const names = env.drive.files(folder.id).map((f) => f.name).filter((n) => n !== '_metadata.json');
  for (const n of names) assert.match(n, /^[0-9a-f-]{36}\.pdf$/);
});

// ---------- metadata, CRUD, pagination ----------
test('metadata serialises and parses losslessly; corrupt metadata is rejected', () => {
  const { env } = setup();
  const meta = { version: 1, docs: { x: { id: 'x', title: 'عنوان — اردو', year: 2020 } }, customCategories: [{ id: 'custom_ab12cd34', name: 'نیا' }] };
  assert.deepEqual(plain(env.ctx.parseMeta_(env.ctx.serializeMeta_(meta))), meta);
  assert.throws(() => env.ctx.parseMeta_('{bad'), (e) => e.code === 'METADATA_CORRUPT');
  assert.throws(() => env.ctx.parseMeta_('{"version":2,"docs":{},"customCategories":[]}'), (e) => e.code === 'METADATA_CORRUPT');
  assert.throws(() => env.ctx.parseMeta_('{"version":1,"docs":[],"customCategories":[]}'), (e) => e.code === 'METADATA_CORRUPT');
});

test('corrupt stored metadata fails closed and is never overwritten', () => {
  const { env, alice } = setup();
  ok(env.callAs(alice, 'upload', up(PDF)));
  const folder = env.drive.findFolder('aliceUid001');
  const metaItem = env.drive.files(folder.id).find((f) => f.name === '_metadata.json');
  metaItem.bytes = Buffer.from('{corrupted');
  fail(env.callAs(alice, 'listDocs'), 'METADATA_CORRUPT');
  fail(env.callAs(alice, 'upload', up(PDF)), 'METADATA_CORRUPT');
  assert.equal(metaItem.bytes.toString(), '{corrupted');
});

test('upload -> get -> download round trip preserves bytes; responses never leak Drive IDs', () => {
  const { env, alice } = setup();
  const res = env.callAs(alice, 'upload', up(PDF, null, { institution: 'جامعہ پنجاب', passingYear: 2019, issueDate: '2020-01-15', notes: 'line1\nline2' }));
  const doc = ok(res).doc;
  assert.equal(doc.size, PDF.length);
  assert.equal(doc.institution, 'جامعہ پنجاب');
  assert.equal(doc.passingYear, 2019);
  const folder = env.drive.findFolder('aliceUid001');
  const driveId = env.drive.files(folder.id).find((f) => f.name.endsWith('.pdf')).id;
  const dl = env.callAs(alice, 'download', { docId: doc.id });
  assert.deepEqual(Buffer.from(ok(dl).contentBase64, 'base64'), PDF);
  for (const r of [res, dl, env.callAs(alice, 'listDocs'), env.callAs(alice, 'getDoc', { docId: doc.id })]) {
    assert.ok(!JSON.stringify(r).includes(driveId), 'Drive file id leaked');
    assert.ok(!/drive\.google\.com/.test(JSON.stringify(r)), 'Drive URL leaked');
  }
});

test('updateDoc edits metadata with validation; needs at least one field', () => {
  const { env, alice } = setup();
  const doc = ok(env.callAs(alice, 'upload', up(PDF))).doc;
  const upd = ok(env.callAs(alice, 'updateDoc', { docId: doc.id, title: 'نیا عنوان', category: 'phd', passingYear: 2022 })).doc;
  assert.equal(upd.title, 'نیا عنوان');
  assert.equal(upd.category, 'phd');
  assert.equal(upd.passingYear, 2022);
  fail(env.callAs(alice, 'updateDoc', { docId: doc.id }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'updateDoc', { docId: doc.id, title: '' }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'updateDoc', { docId: doc.id, category: 'nope' }), 'BAD_REQUEST');
  assert.equal(ok(env.callAs(alice, 'getDoc', { docId: doc.id })).doc.title, 'نیا عنوان', 'failed updates change nothing');
});

test('deleteDoc trashes the Drive file and removes the entry', () => {
  const { env, alice } = setup();
  const doc = ok(env.callAs(alice, 'upload', up(PDF))).doc;
  const folder = env.drive.findFolder('aliceUid001');
  const file = env.drive.files(folder.id).find((f) => f.name.endsWith('.pdf'));
  assert.equal(ok(env.callAs(alice, 'deleteDoc', { docId: doc.id })).deleted, true);
  assert.equal(file.trashed, true);
  assert.equal(ok(env.callAs(alice, 'listDocs')).total, 0);
  fail(env.callAs(alice, 'download', { docId: doc.id }), 'NOT_FOUND');
  fail(env.callAs(alice, 'deleteDoc', { docId: doc.id }), 'NOT_FOUND');
});

test('listing: pagination, category filter and search', () => {
  const { env, alice } = setup();
  for (let i = 1; i <= 5; i++) {
    ok(env.callAs(alice, 'upload', up(PDF, null, {
      title: 'Doc ' + i, category: i % 2 ? 'matric' : 'cv', institution: i === 3 ? 'Punjab University' : '',
    })));
  }
  const p1 = ok(env.callAs(alice, 'listDocs', { limit: 2 }));
  assert.equal(p1.items.length, 2);
  assert.equal(p1.total, 5);
  assert.equal(p1.nextCursor, '2');
  const p2 = ok(env.callAs(alice, 'listDocs', { limit: 2, cursor: p1.nextCursor }));
  const p3 = ok(env.callAs(alice, 'listDocs', { limit: 2, cursor: p2.nextCursor }));
  assert.equal(p3.items.length, 1);
  assert.equal(p3.nextCursor, null);
  const ids = [...p1.items, ...p2.items, ...p3.items].map((d) => d.id);
  assert.equal(new Set(ids).size, 5, 'pages must not overlap');

  assert.equal(ok(env.callAs(alice, 'listDocs', { category: 'cv' })).total, 2);
  assert.equal(ok(env.callAs(alice, 'listDocs', { query: 'punjab' })).total, 1);
  assert.equal(ok(env.callAs(alice, 'listDocs', { query: 'doc 4' })).total, 1);
  fail(env.callAs(alice, 'listDocs', { limit: 0 }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'listDocs', { limit: 101 }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'listDocs', { cursor: '-1' }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'listDocs', { category: 'bogus' }), 'BAD_REQUEST');
});

test('categories: 12 built-in with Urdu labels, custom categories per user', () => {
  const { env, alice, bob } = setup();
  const cats = ok(env.callAs(alice, 'listCategories')).categories;
  assert.equal(cats.length, 12);
  assert.equal(cats[0].ur, 'میٹرک');
  const added = ok(env.callAs(alice, 'addCategory', { name: 'پروفیشنل کورسز' })).category;
  assert.match(added.id, /^custom_[0-9a-f]{8}$/);
  fail(env.callAs(alice, 'addCategory', { name: 'پروفیشنل کورسز' }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'addCategory', { name: 'Matric' }), 'BAD_REQUEST');
  fail(env.callAs(alice, 'addCategory', { name: '' }), 'BAD_REQUEST');
  ok(env.callAs(alice, 'upload', up(PDF, null, { category: added.id })));
  fail(env.callAs(bob, 'upload', up(PDF, null, { category: added.id })), 'BAD_REQUEST'); // not bob's category
});

// ---------- error handling & logging ----------
test('unexpected failures return INTERNAL without leaking details', () => {
  const env = createEnv();
  const admin = env.addUser({ uid: 'adminUid001', email: 'owner@example.com' });
  env.drive.state.breakCreateFolder = true;
  const res = env.callAs(admin, 'listDocs');
  fail(res, 'INTERNAL');
  assert.ok(!JSON.stringify(res).includes('boom'));
});

test('lock timeout surfaces as BUSY', () => {
  const { env, alice } = setup();
  const busy = createEnv({ lockFails: true });
  const t = busy.addUser({ uid: 'adminUid001', email: 'owner@example.com' });
  fail(busy.callAs(t, 'listDocs'), 'BUSY');
  assert.ok(alice);
});

test('logs never contain tokens, API key, passwords or file contents', () => {
  const { env, admin, alice } = setup();
  env.drive.state.breakCreateFolder = false;
  ok(env.callAs(alice, 'upload', up(PDF)));
  env.callAs(env.forgeToken('x'.repeat(10) + '1', 'a@example.com'), 'listDocs');
  env.callAs(alice, 'upload', up(EXE));
  const broken = createEnv();
  const t = broken.addUser({ uid: 'adminUid001', email: 'owner@example.com' });
  broken.drive.state.breakCreateFolder = true;
  broken.callAs(t, 'listDocs');
  const all = env.logs.concat(broken.logs).join('\n');
  for (const secret of [alice, admin, 'test-api-key', PDF.toString('base64'), t]) {
    assert.ok(!all.includes(secret), 'secret found in logs');
  }
});

// ---------- open registration (the default: no invitation needed) ----------
test('open registration: any verified account can use the app without an invitation', () => {
  const env = createEnv();
  const carol = env.addUser({ uid: 'carolUid0004', email: 'carol@example.com' });
  const me = ok(env.callAs(carol, 'me'));
  assert.equal(me.email, 'carol@example.com');
  assert.equal(me.isAdmin, false);
  const doc = ok(env.callAs(carol, 'upload', up(PDF))).doc;
  assert.equal(ok(env.callAs(carol, 'listDocs')).items.length, 1);
  assert.equal(ok(env.callAs(carol, 'download', { docId: doc.id })).doc.id, doc.id);
});

test('open registration: users still cannot see or touch each other\'s documents', () => {
  const env = createEnv();
  const carol = env.addUser({ uid: 'carolUid0004', email: 'carol@example.com' });
  const dave = env.addUser({ uid: 'daveUid00005', email: 'dave@example.com' });
  const doc = ok(env.callAs(carol, 'upload', up(PDF))).doc;
  assert.equal(ok(env.callAs(dave, 'listDocs')).items.length, 0);
  fail(env.callAs(dave, 'download', { docId: doc.id }), 'NOT_FOUND');
  fail(env.callAs(dave, 'deleteDoc', { docId: doc.id }), 'NOT_FOUND');
  assert.equal(ok(env.callAs(carol, 'listDocs')).items.length, 1);
});

test('open registration: an unverified email is still rejected', () => {
  const env = createEnv();
  const mallory = env.addUser({ uid: 'malloryUid06', email: 'mallory@example.com', emailVerified: false });
  fail(env.callAs(mallory, 'listDocs'), 'EMAIL_NOT_VERIFIED');
  fail(env.callAs(mallory, 'upload', up(PDF)), 'EMAIL_NOT_VERIFIED');
});

test('open registration: forged or missing tokens are still rejected', () => {
  const env = createEnv();
  fail(env.callAs('not-a-real-token-at-all-0123456789', 'listDocs'), 'UNAUTHENTICATED');
  fail(env.call({ action: 'listDocs' }), 'UNAUTHENTICATED');
});

test('open registration: the per-user document limit still applies', () => {
  const env = createEnv();
  const carol = env.addUser({ uid: 'carolUid0004', email: 'carol@example.com' });
  for (let i = 0; i < 100; i++) ok(env.callAs(carol, 'upload', up(PDF, null, { title: 'Doc ' + i })));
  fail(env.callAs(carol, 'upload', up(PDF)), 'LIMIT_REACHED');
});
