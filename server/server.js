require('dotenv').config();
const express = require('express');
const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const User = require('./models/User');

const { MONGODB_URI, JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD, PORT = 3000 } = process.env;
if (!MONGODB_URI || !JWT_SECRET) {
  console.error('Missing MONGODB_URI or JWT_SECRET (set them in .env or Render environment variables)');
  process.exit(1);
}

const app = express();
app.set('trust proxy', 1);
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '10kb' }));
app.use('/api/auth', rateLimit({ windowMs: 15 * 60 * 1000, max: 60 }));

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);
const sign = (u) => jwt.sign({ id: u._id }, JWT_SECRET, { expiresIn: '30d' });
const pub = (u) => ({ id: u._id, email: u.email, role: u.role, status: u.status });
const emailOk = (e) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(e);

// Loads the user from the database on EVERY request, so approvals/denials take effect immediately.
const auth = wrap(async (req, res, next) => {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'Not signed in' });
  let id;
  try {
    id = jwt.verify(token, JWT_SECRET).id;
  } catch (e) {
    return res.status(401).json({ error: 'Session expired, sign in again' });
  }
  const user = await User.findById(id);
  if (!user) return res.status(401).json({ error: 'Account not found' });
  req.user = user;
  next();
});

const adminOnly = (req, res, next) =>
  req.user.role === 'admin' ? next() : res.status(403).json({ error: 'Admins only' });

app.get('/', (req, res) => res.json({ ok: true, app: 'GameShield API' }));

// New user asks for access -> created as "pending"
app.post('/api/auth/register', wrap(async (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  if (!emailOk(email)) return res.status(400).json({ error: 'Enter a valid email' });
  if (password.length < 6) return res.status(400).json({ error: 'Password must be at least 6 characters' });
  if (await User.findOne({ email })) {
    return res.status(409).json({ error: 'That email already has an account. Sign in instead.' });
  }
  const user = await User.create({ email, passwordHash: await bcrypt.hash(password, 10) });
  res.status(201).json({ token: sign(user), user: pub(user) });
}));

app.post('/api/auth/login', wrap(async (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  const user = await User.findOne({ email });
  if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
    return res.status(401).json({ error: 'Wrong email or password' });
  }
  res.json({ token: sign(user), user: pub(user) });
}));

// The app calls this to check its current approval status
app.get('/api/me', auth, (req, res) => res.json({ user: pub(req.user) }));

// ---- admin only ----
app.get('/api/admin/users', auth, adminOnly, wrap(async (req, res) => {
  const users = await User.find().sort({ createdAt: -1 });
  res.json({ users: users.map(pub) });
}));

app.post('/api/admin/users/:id/status', auth, adminOnly, wrap(async (req, res) => {
  const status = req.body.status;
  if (!['approved', 'denied', 'pending'].includes(status)) {
    return res.status(400).json({ error: 'Invalid status' });
  }
  if (!mongoose.isValidObjectId(req.params.id)) return res.status(400).json({ error: 'Bad id' });
  const user = await User.findById(req.params.id);
  if (!user) return res.status(404).json({ error: 'User not found' });
  if (user.role === 'admin') return res.status(400).json({ error: 'Cannot change an admin account' });
  user.status = status;
  await user.save();
  res.json({ user: pub(user) });
}));

app.use((err, req, res, next) => {
  console.error(err);
  res.status(500).json({ error: 'Server error' });
});

// Admin account comes from environment variables, never from the app or the source code.
async function seedAdmin() {
  if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
    console.warn('ADMIN_EMAIL / ADMIN_PASSWORD not set: no admin account created');
    return;
  }
  const email = ADMIN_EMAIL.trim().toLowerCase();
  await User.findOneAndUpdate(
    { email },
    { email, passwordHash: await bcrypt.hash(ADMIN_PASSWORD, 10), role: 'admin', status: 'approved' },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );
  console.log('Admin account ready:', email);
}

mongoose
  .connect(MONGODB_URI)
  .then(seedAdmin)
  .then(() => app.listen(PORT, () => console.log('GameShield API on port ' + PORT)))
  .catch((e) => {
    console.error('Startup failed:', e.message);
    process.exit(1);
  });
