class AppError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'AppError';
    this.code = code;
  }
}

const EMAIL_RE_ = /^[a-z0-9._%+\-]+@[a-z0-9\-]+(\.[a-z0-9\-]+)*\.[a-z]{2,}$/;
const UID_RE_ = /^[A-Za-z0-9]{6,128}$/;
const DOC_ID_RE_ = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

// ---------- JSON responses (Apps Script cannot set HTTP status codes) ----------
function jsonOut_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
function ok_(data) { return jsonOut_({ ok: true, data: data }); }
function fail_(code, message) { return jsonOut_({ ok: false, error: { code: code, message: message } }); }

function handleError_(err) {
  if (err && err.name === 'AppError') return fail_(err.code, err.message);
  // Log only the error type: messages could contain user data.
  console.error('Unexpected error: ' + (err && err.name ? err.name : 'Error'));
  return fail_('INTERNAL', 'Unexpected server error');
}

// ---------- locking (re-entrant within one execution) ----------
var lockDepth_ = 0;
function withLock_(fn) {
  if (lockDepth_ > 0) return fn();
  const lock = LockService.getScriptLock();
  try { lock.waitLock(20000); } catch (e) { throw new AppError('BUSY', 'Server is busy, please retry.'); }
  lockDepth_++;
  try { return fn(); } finally { lockDepth_--; lock.releaseLock(); }
}

// ---------- generic helpers ----------
function isPlainObject_(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }
function hasOwn_(o, k) { return Object.prototype.hasOwnProperty.call(o, k); }

function normalizeEmail_(v) {
  if (typeof v !== 'string') throw new AppError('BAD_REQUEST', 'email is required');
  const s = v.trim().toLowerCase();
  if (s.length > 254 || !EMAIL_RE_.test(s)) throw new AppError('BAD_REQUEST', 'Invalid email address');
  return s;
}

function cleanText_(value, opts) {
  const o = opts || {};
  if (value === undefined || value === null || value === '') {
    if (o.required) throw new AppError('BAD_REQUEST', o.field + ' is required');
    return '';
  }
  if (typeof value !== 'string') throw new AppError('BAD_REQUEST', o.field + ' must be text');
  let s = value.replace(/\r\n?/g, '\n');
  s = o.multiline ? s.replace(/[\u0000-\u0009\u000B-\u001F\u007F]/g, ' ') : s.replace(/[\u0000-\u001F\u007F]/g, ' ');
  s = s.trim();
  if (o.required && !s) throw new AppError('BAD_REQUEST', o.field + ' is required');
  if (s.length > o.max) throw new AppError('BAD_REQUEST', o.field + ' is too long (max ' + o.max + ' characters)');
  return s;
}

// Display-only name; the stored Drive filename is always server-generated.
function safeDisplayName_(v) {
  if (typeof v !== 'string') return '';
  let s = v.split(/[\\\/]/).pop();
  s = s.replace(/[\u0000-\u001F\u007F<>:"|?*]/g, '').trim().replace(/^\.+/, '');
  return s.slice(0, LIMITS.nameMax);
}

function validateYear_(v) {
  if (v === undefined || v === null || v === '') return null;
  const n = typeof v === 'number' ? v : (/^\d{4}$/.test(String(v)) ? parseInt(v, 10) : NaN);
  if (!Number.isInteger(n) || n < 1950 || n > new Date().getFullYear() + 1) {
    throw new AppError('BAD_REQUEST', 'passingYear is invalid');
  }
  return n;
}

function validateDate_(v) {
  if (v === undefined || v === null || v === '') return null;
  if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(v)) throw new AppError('BAD_REQUEST', 'issueDate must be YYYY-MM-DD');
  const y = +v.slice(0, 4), m = +v.slice(5, 7), d = +v.slice(8, 10);
  const dt = new Date(Date.UTC(y, m - 1, d));
  if (y < 1900 || y > 2100 || dt.getUTCFullYear() !== y || dt.getUTCMonth() !== m - 1 || dt.getUTCDate() !== d) {
    throw new AppError('BAD_REQUEST', 'issueDate is not a real date');
  }
  return v;
}

function validateDocId_(v) {
  if (typeof v !== 'string' || !DOC_ID_RE_.test(v)) throw new AppError('BAD_REQUEST', 'Invalid document id');
  return v;
}

// ---------- upload validation ----------
function sniffMime_(b) {
  const u = function (i) { return b[i] & 0xFF; };
  if (b.length >= 5 && u(0) === 0x25 && u(1) === 0x50 && u(2) === 0x44 && u(3) === 0x46 && u(4) === 0x2D) return 'application/pdf';
  if (b.length >= 3 && u(0) === 0xFF && u(1) === 0xD8 && u(2) === 0xFF) return 'image/jpeg';
  if (b.length >= 8 && u(0) === 0x89 && u(1) === 0x50 && u(2) === 0x4E && u(3) === 0x47 &&
      u(4) === 0x0D && u(5) === 0x0A && u(6) === 0x1A && u(7) === 0x0A) return 'image/png';
  return null;
}

function decodeUpload_(p, maxBytes) {
  const b64 = p.contentBase64;
  if (typeof b64 !== 'string' || !b64.length) throw new AppError('BAD_REQUEST', 'contentBase64 is required');
  if (b64.length % 4 !== 0 || !/^[A-Za-z0-9+\/]+={0,2}$/.test(b64)) throw new AppError('BAD_REQUEST', 'contentBase64 is not valid base64');
  const pad = b64.endsWith('==') ? 2 : (b64.endsWith('=') ? 1 : 0);
  const size = (b64.length / 4) * 3 - pad;
  if (size > maxBytes) {
    throw new AppError('FILE_TOO_LARGE', 'File exceeds the limit of ' + maxBytes + ' bytes. It was NOT uploaded.');
  }
  let claimed = typeof p.mimeType === 'string' ? p.mimeType.trim().toLowerCase() : '';
  if (claimed === 'image/jpg') claimed = 'image/jpeg';
  if (!hasOwn_(ALLOWED_TYPES_, claimed)) throw new AppError('UNSUPPORTED_TYPE', 'Only PDF, JPG and PNG files are allowed');
  const bytes = Utilities.base64Decode(b64);
  if (bytes.length !== size) throw new AppError('BAD_REQUEST', 'contentBase64 is corrupt');
  if (sniffMime_(bytes) !== claimed) throw new AppError('UNSUPPORTED_TYPE', 'File content does not match its declared type');
  return { bytes: bytes, mimeType: claimed, ext: ALLOWED_TYPES_[claimed], size: size };
}
