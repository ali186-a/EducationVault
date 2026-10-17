/**
 * Drive layout (all inside the deploying owner's Drive, never shared publicly):
 *   EducationVault/
 *     _allowlist.json
 *     users/<firebase uid>/
 *        _metadata.json
 *        <docId>.pdf|jpg|png     (server-generated names)
 */

function parseJsonStrict_(text, validFn) {
  let d;
  try { d = JSON.parse(text); } catch (e) { throw new AppError('METADATA_CORRUPT', 'Stored data is corrupt; refusing to overwrite it.'); }
  if (!validFn(d)) throw new AppError('METADATA_CORRUPT', 'Stored data has an unexpected format; refusing to overwrite it.');
  return d;
}

function loadJsonFile_(folder, name, emptyFn, validFn) {
  const it = folder.getFilesByName(name);
  if (!it.hasNext()) return { file: null, data: emptyFn() };
  const file = it.next();
  return { file: file, data: parseJsonStrict_(file.getBlob().getDataAsString('UTF-8'), validFn) };
}

function saveJsonFile_(folder, name, holder, data) {
  const text = JSON.stringify(data);
  if (holder.file) {
    holder.file.setContent(text);
  } else {
    holder.file = folder.createFile(name, text, 'application/json');
  }
}

// ---------- metadata ----------
function emptyMeta_() { return { version: 1, docs: {}, customCategories: [] }; }
function validMeta_(d) { return isPlainObject_(d) && d.version === 1 && isPlainObject_(d.docs) && Array.isArray(d.customCategories); }
function parseMeta_(text) { return parseJsonStrict_(text, validMeta_); }
function serializeMeta_(meta) { return JSON.stringify(meta); }

function loadMeta_(folder) { return loadJsonFile_(folder, META_FILE_NAME_, emptyMeta_, validMeta_); }
function saveMeta_(folder, holder) { saveJsonFile_(folder, META_FILE_NAME_, holder, holder.data); }

// ---------- folders ----------
function openRoot_(id) {
  let f;
  try { f = DriveApp.getFolderById(id); } catch (e) { throw new AppError('STORAGE_UNAVAILABLE', 'The EducationVault folder cannot be opened.'); }
  if (f.isTrashed()) throw new AppError('STORAGE_UNAVAILABLE', 'The EducationVault folder is in the Drive trash.');
  return f;
}

function getRootFolder_() {
  const props = PropertiesService.getScriptProperties();
  const existing = props.getProperty('ROOT_FOLDER_ID');
  if (existing) return openRoot_(existing);
  return withLock_(function () {
    const again = props.getProperty('ROOT_FOLDER_ID');
    if (again) return openRoot_(again);
    const folder = DriveApp.createFolder(ROOT_FOLDER_NAME_);
    props.setProperty('ROOT_FOLDER_ID', folder.getId());
    return folder;
  });
}

function findOrCreateChildFolder_(parent, name) {
  const it = parent.getFoldersByName(name);
  if (it.hasNext()) return it.next();
  return withLock_(function () {
    const again = parent.getFoldersByName(name);
    if (again.hasNext()) return again.next();
    return parent.createFolder(name);
  });
}

function getUserFolder_(uid) {
  if (typeof uid !== 'string' || !UID_RE_.test(uid)) throw new AppError('FORBIDDEN', 'Invalid account id');
  const users = findOrCreateChildFolder_(getRootFolder_(), USERS_FOLDER_NAME_);
  return findOrCreateChildFolder_(users, uid);
}

// ---------- ownership ----------
function assertFileInFolder_(file, folder) {
  const parents = file.getParents();
  const want = folder.getId();
  while (parents.hasNext()) {
    if (parents.next().getId() === want) return;
  }
  // Same answer as "missing": never reveal that the file exists elsewhere.
  throw new AppError('NOT_FOUND', 'Document not found');
}

function getDocEntry_(meta, docId) {
  validateDocId_(docId);
  if (!hasOwn_(meta.docs, docId)) throw new AppError('NOT_FOUND', 'Document not found');
  return meta.docs[docId];
}

/** Looks the doc up ONLY in the caller's own metadata, then double-checks the Drive parent. */
function resolveDoc_(folder, meta, docId) {
  const entry = getDocEntry_(meta, docId);
  let file;
  try { file = DriveApp.getFileById(entry.fileId); } catch (e) { throw new AppError('NOT_FOUND', 'Document file is missing'); }
  assertFileInFolder_(file, folder);
  if (file.isTrashed()) throw new AppError('NOT_FOUND', 'Document file is missing');
  return { entry: entry, file: file };
}

// ---------- categories ----------
function validateCategory_(id, meta) {
  if (typeof id !== 'string') throw new AppError('BAD_REQUEST', 'category is required');
  for (let i = 0; i < CATEGORIES.length; i++) if (CATEGORIES[i].id === id) return id;
  for (let j = 0; j < meta.customCategories.length; j++) if (meta.customCategories[j].id === id) return id;
  throw new AppError('BAD_REQUEST', 'Unknown category');
}

function allCategories_(meta) {
  const base = CATEGORIES.map(function (c) { return { id: c.id, ur: c.ur, en: c.en, custom: false }; });
  const custom = meta.customCategories.map(function (c) { return { id: c.id, ur: c.name, en: c.name, custom: true }; });
  return base.concat(custom);
}
