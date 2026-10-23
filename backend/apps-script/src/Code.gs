/**
 * EducationVault backend entry points.
 * Every request is POST with a JSON body: { "action": "...", "idToken": "...", ...params }
 * (the token travels in the body because Apps Script web apps cannot read custom headers).
 * Responses are always JSON: { ok: true, data } or { ok: false, error: { code, message } }.
 */
const ACTIONS_ = [
  'ping', 'me', 'listCategories', 'addCategory',
  'listDocs', 'getDoc', 'upload', 'download', 'updateDoc', 'deleteDoc',
  'inviteUser', 'revokeUser', 'listInvites',
  'getAvatar', 'setAvatar', 'deleteAvatar', 'appVersion'
];

function doGet(e) {
  return ok_({ service: 'EducationVault', configured: isConfigured_() });
}

function doPost(e) {
  try {
    return route_(parseRequest_(e));
  } catch (err) {
    return handleError_(err);
  }
}

function parseRequest_(e) {
  const raw = e && e.postData && e.postData.contents;
  if (typeof raw !== 'string' || !raw.length) throw new AppError('BAD_REQUEST', 'Empty request body');
  if (raw.length > LIMITS.requestMaxChars) throw new AppError('REQUEST_TOO_LARGE', 'Request is too large');
  let req;
  try { req = JSON.parse(raw); } catch (err) { throw new AppError('BAD_REQUEST', 'Invalid JSON'); }
  if (!isPlainObject_(req)) throw new AppError('BAD_REQUEST', 'Request must be a JSON object');
  if (typeof req.action !== 'string' || ACTIONS_.indexOf(req.action) === -1) throw new AppError('BAD_REQUEST', 'Unknown action');
  return req;
}

function route_(req) {
  if (req.action === 'ping') return ok_({ service: 'EducationVault', configured: isConfigured_() });
  // Public (no login): the app asks this at start to offer an update. Contains no private data.
  if (req.action === 'appVersion') return ok_(handleAppVersion_());

  const cfg = getConfig_();
  const user = authorize_(req.idToken, cfg);

  switch (req.action) {
    case 'me': return ok_(handleMe_(user, cfg));
    case 'listCategories': return ok_(handleListCategories_(user));
    case 'addCategory': return ok_(handleAddCategory_(user, req));
    case 'listDocs': return ok_(handleListDocs_(user, req));
    case 'getDoc': return ok_(handleGetDoc_(user, req));
    case 'upload': return ok_(handleUpload_(user, cfg, req));
    case 'download': return ok_(handleDownload_(user, req));
    case 'updateDoc': return ok_(handleUpdateDoc_(user, req));
    case 'deleteDoc': return ok_(handleDeleteDoc_(user, req));
    case 'inviteUser': return ok_(handleInvite_(user, cfg, req));
    case 'revokeUser': return ok_(handleRevoke_(user, cfg, req));
    case 'listInvites': return ok_(handleListInvites_(user));
    case 'getAvatar': return ok_(handleGetAvatar_(user));
    case 'setAvatar': return ok_(handleSetAvatar_(user, req));
    case 'deleteAvatar': return ok_(handleDeleteAvatar_(user));
    default: throw new AppError('BAD_REQUEST', 'Unknown action');
  }
}
