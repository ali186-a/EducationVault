/**
 * Authentication = Firebase Identity Toolkit accounts:lookup (server-side, authoritative).
 * The JWT payload is decoded ONLY for extra sanity checks (aud/iss/uid) AFTER Firebase
 * has accepted the token. A token that merely decodes as a JWT is never accepted.
 */
function decodeJwtPayload_(token) {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  try {
    let s = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    while (s.length % 4) s += '=';
    const json = Utilities.newBlob(Utilities.base64Decode(s)).getDataAsString('UTF-8');
    const obj = JSON.parse(json);
    return isPlainObject_(obj) ? obj : null;
  } catch (e) {
    return null;
  }
}

function verifyIdToken_(idToken, cfg) {
  if (typeof idToken !== 'string' || idToken.length < 20 || idToken.length > 4096) {
    throw new AppError('UNAUTHENTICATED', 'A valid idToken is required');
  }
  const claims = decodeJwtPayload_(idToken);
  if (!claims || claims.aud !== cfg.projectId || claims.iss !== 'https://securetoken.google.com/' + cfg.projectId) {
    throw new AppError('UNAUTHENTICATED', 'Token is not for this project');
  }

  const resp = UrlFetchApp.fetch(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' + encodeURIComponent(cfg.apiKey),
    { method: 'post', contentType: 'application/json', payload: JSON.stringify({ idToken: idToken }), muteHttpExceptions: true }
  );
  const status = resp.getResponseCode();
  let body = null;
  try { body = JSON.parse(resp.getContentText()); } catch (e) { body = null; }

  if (status !== 200) {
    const msg = String((body && body.error && body.error.message) || '');
    if (msg.indexOf('TOKEN_EXPIRED') === 0) throw new AppError('TOKEN_EXPIRED', 'Session expired, please sign in again');
    if (msg.indexOf('USER_DISABLED') === 0) throw new AppError('ACCOUNT_DISABLED', 'This account has been disabled');
    if (status >= 500 || status === 429) throw new AppError('UPSTREAM_ERROR', 'Authentication service unavailable, retry later');
    throw new AppError('UNAUTHENTICATED', 'Invalid or revoked token');
  }

  const user = body && body.users && body.users[0];
  if (!user || typeof user.localId !== 'string' || !UID_RE_.test(user.localId)) {
    throw new AppError('UNAUTHENTICATED', 'Invalid token');
  }
  if (user.disabled === true) throw new AppError('ACCOUNT_DISABLED', 'This account has been disabled');
  if ((claims.user_id || claims.sub) !== user.localId) throw new AppError('UNAUTHENTICATED', 'Token/user mismatch');

  return {
    uid: user.localId,
    email: String(user.email || '').trim().toLowerCase(),
    emailVerified: user.emailVerified === true
  };
}

// ---------- allowlist (stored in <root>/_allowlist.json; fails closed if corrupt) ----------
function validAllowlist_(d) { return isPlainObject_(d) && d.version === 1 && isPlainObject_(d.invites); }

function loadAllowlist_(root) {
  return loadJsonFile_(root, ALLOWLIST_FILE_NAME_, function () { return { version: 1, invites: {} }; }, validAllowlist_);
}
function saveAllowlist_(root, holder) {
  saveJsonFile_(root, ALLOWLIST_FILE_NAME_, holder, holder.data);
}

/**
 * Returns {uid, email, emailVerified, isAdmin} or throws.
 * Email verification is REQUIRED for the admin (always) and for everyone else by default,
 * because Firebase email/password sign-up does not prove ownership of an email address:
 * without this, anyone could register the admin's or an invitee's email before they do.
 */
function authorize_(idToken, cfg) {
  const id = verifyIdToken_(idToken, cfg);
  if (!id.email) throw new AppError('FORBIDDEN', 'Account has no email address');

  if (id.email === cfg.adminEmail) {
    if (!id.emailVerified) throw new AppError('EMAIL_NOT_VERIFIED', 'Please verify your email address first');
    return { uid: id.uid, email: id.email, emailVerified: true, isAdmin: true };
  }
  if (cfg.requireEmailVerified && !id.emailVerified) {
    throw new AppError('EMAIL_NOT_VERIFIED', 'Please verify your email address first');
  }

  // Open registration (default): every account with a verified email may use the app.
  // Each user still only ever reaches their own folder (see Storage.gs ownership checks).
  if (!cfg.requireInvite) {
    return { uid: id.uid, email: id.email, emailVerified: id.emailVerified, isAdmin: false };
  }

  const root = getRootFolder_();
  const invites = loadAllowlist_(root).data.invites;
  const entry = hasOwn_(invites, id.email) ? invites[id.email] : null;
  if (!entry || entry.status !== 'active') throw new AppError('NOT_INVITED', 'This account has not been invited');
  if (entry.uid && entry.uid !== id.uid) throw new AppError('FORBIDDEN', 'Invitation belongs to a different account');

  if (!entry.uid) {
    withLock_(function () {
      const h = loadAllowlist_(root);
      const e2 = hasOwn_(h.data.invites, id.email) ? h.data.invites[id.email] : null;
      if (e2 && e2.status === 'active' && !e2.uid) {
        e2.uid = id.uid;
        saveAllowlist_(root, h);
      }
    });
  }
  return { uid: id.uid, email: id.email, emailVerified: id.emailVerified, isAdmin: false };
}

function requireAdmin_(user) {
  if (!user || user.isAdmin !== true) throw new AppError('FORBIDDEN', 'Administrator access required');
}
