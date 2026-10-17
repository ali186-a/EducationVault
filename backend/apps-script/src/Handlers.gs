function publicDoc_(d) {
  // Never expose Drive file IDs, stored names or URLs to the client.
  return {
    id: d.id,
    title: d.title,
    category: d.category,
    institution: d.institution || '',
    passingYear: d.passingYear == null ? null : d.passingYear,
    issueDate: d.issueDate || null,
    notes: d.notes || '',
    mimeType: d.mimeType,
    size: d.size,
    originalName: d.originalName || '',
    createdAt: d.createdAt,
    updatedAt: d.updatedAt
  };
}

function parseCursor_(v) {
  if (v === undefined || v === null || v === '') return 0;
  if (!/^\d{1,7}$/.test(String(v))) throw new AppError('BAD_REQUEST', 'Invalid cursor');
  return parseInt(v, 10);
}

function parseLimit_(v) {
  if (v === undefined || v === null || v === '') return LIMITS.listDefault;
  const n = typeof v === 'number' ? v : (/^\d{1,4}$/.test(String(v)) ? parseInt(v, 10) : NaN);
  if (!Number.isInteger(n) || n < 1 || n > LIMITS.listMax) throw new AppError('BAD_REQUEST', 'limit must be 1-' + LIMITS.listMax);
  return n;
}

// ---------- general ----------
function handleMe_(user, cfg) {
  return { uid: user.uid, email: user.email, isAdmin: user.isAdmin, maxUploadBytes: cfg.maxUploadBytes };
}

function handleListCategories_(user) {
  const folder = getUserFolder_(user.uid);
  return { categories: allCategories_(loadMeta_(folder).data) };
}

function handleAddCategory_(user, req) {
  const name = cleanText_(req.name, { field: 'name', max: LIMITS.categoryNameMax, required: true });
  const folder = getUserFolder_(user.uid);
  return withLock_(function () {
    const holder = loadMeta_(folder);
    const meta = holder.data;
    if (meta.customCategories.length >= LIMITS.maxCustomCategories) throw new AppError('LIMIT_REACHED', 'Too many custom categories');
    const lower = name.toLowerCase();
    const taken = allCategories_(meta).some(function (c) { return c.ur.toLowerCase() === lower || c.en.toLowerCase() === lower; });
    if (taken) throw new AppError('BAD_REQUEST', 'A category with this name already exists');
    const cat = { id: 'custom_' + Utilities.getUuid().replace(/-/g, '').slice(0, 8), name: name };
    meta.customCategories.push(cat);
    saveMeta_(folder, holder);
    return { category: { id: cat.id, ur: cat.name, en: cat.name, custom: true } };
  });
}

// ---------- documents ----------
function handleUpload_(user, cfg, req) {
  const title = cleanText_(req.title, { field: 'title', max: LIMITS.titleMax, required: true });
  const institution = cleanText_(req.institution, { field: 'institution', max: LIMITS.textMax });
  const notes = cleanText_(req.notes, { field: 'notes', max: LIMITS.notesMax, multiline: true });
  const passingYear = validateYear_(req.passingYear);
  const issueDate = validateDate_(req.issueDate);
  const originalName = safeDisplayName_(req.fileName);
  const upload = decodeUpload_(req, cfg.maxUploadBytes);
  const folder = getUserFolder_(user.uid);

  return withLock_(function () {
    const holder = loadMeta_(folder);
    const meta = holder.data;
    const category = validateCategory_(req.category, meta);
    if (Object.keys(meta.docs).length >= LIMITS.maxDocsPerUser) throw new AppError('LIMIT_REACHED', 'Document limit reached');

    const docId = Utilities.getUuid();
    const storedName = docId + '.' + upload.ext;
    const file = folder.createFile(Utilities.newBlob(upload.bytes, upload.mimeType, storedName));
    const now = new Date().toISOString();
    meta.docs[docId] = {
      id: docId, fileId: file.getId(), storedName: storedName, title: title, category: category,
      institution: institution, passingYear: passingYear, issueDate: issueDate, notes: notes,
      mimeType: upload.mimeType, size: upload.size, originalName: originalName,
      createdAt: now, updatedAt: now
    };
    try {
      saveMeta_(folder, holder);
    } catch (e) {
      try { file.setTrashed(true); } catch (e2) { /* best effort rollback */ }
      throw new AppError('STORAGE_ERROR', 'Could not save document details; upload was rolled back.');
    }
    return { doc: publicDoc_(meta.docs[docId]) };
  });
}

function handleListDocs_(user, req) {
  const folder = getUserFolder_(user.uid);
  const meta = loadMeta_(folder).data;
  let items = Object.keys(meta.docs).map(function (k) { return meta.docs[k]; });

  if (req.category !== undefined && req.category !== null && req.category !== '') {
    const cat = validateCategory_(req.category, meta);
    items = items.filter(function (d) { return d.category === cat; });
  }
  if (req.query !== undefined && req.query !== null && req.query !== '') {
    const q = cleanText_(req.query, { field: 'query', max: 100 }).toLowerCase();
    if (q) {
      items = items.filter(function (d) {
        return [d.title, d.institution, d.notes, d.originalName].some(function (s) {
          return String(s || '').toLowerCase().indexOf(q) !== -1;
        });
      });
    }
  }
  items.sort(function (a, b) {
    if (a.createdAt !== b.createdAt) return a.createdAt < b.createdAt ? 1 : -1;
    return a.id < b.id ? 1 : -1;
  });

  const offset = parseCursor_(req.cursor);
  const limit = parseLimit_(req.limit);
  const page = items.slice(offset, offset + limit);
  return {
    items: page.map(publicDoc_),
    nextCursor: offset + limit < items.length ? String(offset + limit) : null,
    total: items.length
  };
}

function handleGetDoc_(user, req) {
  const folder = getUserFolder_(user.uid);
  const meta = loadMeta_(folder).data;
  return { doc: publicDoc_(getDocEntry_(meta, req.docId)) };
}

function handleDownload_(user, req) {
  const folder = getUserFolder_(user.uid);
  const meta = loadMeta_(folder).data;
  const r = resolveDoc_(folder, meta, req.docId);
  if (r.file.getSize() > LIMITS.hardUploadBytes) throw new AppError('FILE_TOO_LARGE', 'File is too large to download through the backend');
  return { doc: publicDoc_(r.entry), contentBase64: Utilities.base64Encode(r.file.getBlob().getBytes()) };
}

function handleUpdateDoc_(user, req) {
  const fields = ['title', 'category', 'institution', 'passingYear', 'issueDate', 'notes'];
  const changed = fields.filter(function (k) { return hasOwn_(req, k); });
  if (!changed.length) throw new AppError('BAD_REQUEST', 'Nothing to update');
  validateDocId_(req.docId);

  const patch = {};
  if (hasOwn_(req, 'title')) patch.title = cleanText_(req.title, { field: 'title', max: LIMITS.titleMax, required: true });
  if (hasOwn_(req, 'institution')) patch.institution = cleanText_(req.institution, { field: 'institution', max: LIMITS.textMax });
  if (hasOwn_(req, 'notes')) patch.notes = cleanText_(req.notes, { field: 'notes', max: LIMITS.notesMax, multiline: true });
  if (hasOwn_(req, 'passingYear')) patch.passingYear = validateYear_(req.passingYear);
  if (hasOwn_(req, 'issueDate')) patch.issueDate = validateDate_(req.issueDate);

  const folder = getUserFolder_(user.uid);
  return withLock_(function () {
    const holder = loadMeta_(folder);
    const entry = getDocEntry_(holder.data, req.docId);
    if (hasOwn_(req, 'category')) patch.category = validateCategory_(req.category, holder.data);
    Object.keys(patch).forEach(function (k) { entry[k] = patch[k]; });
    entry.updatedAt = new Date().toISOString();
    saveMeta_(folder, holder);
    return { doc: publicDoc_(entry) };
  });
}

function handleDeleteDoc_(user, req) {
  validateDocId_(req.docId);
  const folder = getUserFolder_(user.uid);
  return withLock_(function () {
    const holder = loadMeta_(folder);
    const entry = getDocEntry_(holder.data, req.docId);
    let file = null;
    try { file = DriveApp.getFileById(entry.fileId); } catch (e) { file = null; }
    if (file) {
      assertFileInFolder_(file, folder);   // refuse if the file is not in the caller's own folder
      file.setTrashed(true);               // moves to Drive trash (recoverable by owner ~30 days)
    }
    delete holder.data.docs[req.docId];
    saveMeta_(folder, holder);
    return { deleted: true };
  });
}

// ---------- admin: invitations ----------
function handleInvite_(user, cfg, req) {
  requireAdmin_(user);
  const email = normalizeEmail_(req.email);
  if (email === cfg.adminEmail) throw new AppError('BAD_REQUEST', 'The administrator is already authorized');
  const root = getRootFolder_();
  return withLock_(function () {
    const h = loadAllowlist_(root);
    const invites = h.data.invites;
    const now = new Date().toISOString();
    if (hasOwn_(invites, email)) {
      const e = invites[email];
      e.status = 'active'; e.invitedAt = now; e.invitedBy = user.email; delete e.revokedAt;
    } else {
      if (Object.keys(invites).length >= LIMITS.maxInvites) throw new AppError('LIMIT_REACHED', 'Too many invitations');
      invites[email] = { email: email, status: 'active', invitedAt: now, invitedBy: user.email, uid: null };
    }
    saveAllowlist_(root, h);
    return { invite: invites[email] };
  });
}

function handleRevoke_(user, cfg, req) {
  requireAdmin_(user);
  const email = normalizeEmail_(req.email);
  if (email === cfg.adminEmail) throw new AppError('BAD_REQUEST', 'The administrator cannot be revoked');
  const root = getRootFolder_();
  return withLock_(function () {
    const h = loadAllowlist_(root);
    if (!hasOwn_(h.data.invites, email)) throw new AppError('NOT_FOUND', 'No invitation for this email');
    const e = h.data.invites[email];
    e.status = 'revoked'; e.revokedAt = new Date().toISOString();
    saveAllowlist_(root, h);
    return { invite: e };
  });
}

function handleListInvites_(user) {
  requireAdmin_(user);
  const invites = loadAllowlist_(getRootFolder_()).data.invites;
  const list = Object.keys(invites).map(function (k) { return invites[k]; });
  list.sort(function (a, b) { return a.invitedAt < b.invitedAt ? -1 : 1; });
  return { invites: list };
}
