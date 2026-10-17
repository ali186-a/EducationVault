/**
 * Static (non-secret) configuration. Settings live in Script Properties:
 *   FIREBASE_API_KEY, FIREBASE_PROJECT_ID, ADMIN_EMAIL   (required)
 *   MAX_UPLOAD_BYTES (optional, default 5242880, hard cap 10485760)
 *   REQUIRE_EMAIL_VERIFIED (optional, default "true")
 *   ROOT_FOLDER_ID (written automatically on first use)
 */
const ROOT_FOLDER_NAME_ = 'EducationVault';
const USERS_FOLDER_NAME_ = 'users';
const META_FILE_NAME_ = '_metadata.json';
const ALLOWLIST_FILE_NAME_ = '_allowlist.json';

const CATEGORIES = [
  { id: 'matric', ur: 'میٹرک', en: 'Matric' },
  { id: 'intermediate', ur: 'انٹرمیڈیٹ', en: 'Intermediate / FA / FSc / ICS / ICom' },
  { id: 'bachelor14', ur: 'بی اے / بی ایس سی / 14 سالہ تعلیم', en: 'BA / BSc / 14 years' },
  { id: 'master16', ur: 'بی ایس / ایم اے / ایم ایس سی', en: 'BS / MA / MSc' },
  { id: 'mphil', ur: 'ایم فل / 18 سالہ تعلیم', en: 'MPhil / 18 years' },
  { id: 'phd', ur: 'پی ایچ ڈی', en: 'PhD' },
  { id: 'diploma', ur: 'ڈپلومہ اور ٹیکنیکل سرٹیفکیٹس', en: 'Diplomas & technical certificates' },
  { id: 'cv', ur: 'سی وی / ریزیومے', en: 'CV / Resume' },
  { id: 'experience', ur: 'تجربہ سرٹیفکیٹ / Experience Letter', en: 'Experience letter' },
  { id: 'employment', ur: 'ملازمت کے کاغذات', en: 'Employment papers' },
  { id: 'identity', ur: 'شناختی اور دیگر ضروری دستاویزات', en: 'ID and other essential documents' },
  { id: 'other', ur: 'دیگر دستاویزات', en: 'Other documents' }
];

const ALLOWED_TYPES_ = { 'application/pdf': 'pdf', 'image/jpeg': 'jpg', 'image/png': 'png' };

const LIMITS = {
  defaultUploadBytes: 5 * 1024 * 1024,
  hardUploadBytes: 10 * 1024 * 1024,
  titleMax: 120,
  textMax: 120,
  notesMax: 1000,
  nameMax: 100,
  categoryNameMax: 40,
  maxDocsPerUser: 500,
  maxInvites: 20,
  maxCustomCategories: 20,
  listDefault: 30,
  listMax: 100,
  requestMaxChars: 16 * 1024 * 1024
};

function getConfig_() {
  const p = PropertiesService.getScriptProperties();
  const apiKey = (p.getProperty('FIREBASE_API_KEY') || '').trim();
  const projectId = (p.getProperty('FIREBASE_PROJECT_ID') || '').trim();
  const adminEmail = (p.getProperty('ADMIN_EMAIL') || '').trim().toLowerCase();
  if (!apiKey || !projectId || !adminEmail || !EMAIL_RE_.test(adminEmail)) {
    throw new AppError('NOT_CONFIGURED', 'Backend Script Properties are missing or invalid.');
  }
  let max = parseInt(p.getProperty('MAX_UPLOAD_BYTES') || '', 10);
  if (!(max > 0)) max = LIMITS.defaultUploadBytes;
  if (max > LIMITS.hardUploadBytes) max = LIMITS.hardUploadBytes;
  const requireVerified = (p.getProperty('REQUIRE_EMAIL_VERIFIED') || 'true').trim().toLowerCase() !== 'false';
  return { apiKey: apiKey, projectId: projectId, adminEmail: adminEmail, maxUploadBytes: max, requireEmailVerified: requireVerified };
}

function isConfigured_() {
  try { getConfig_(); return true; } catch (e) { return false; }
}
