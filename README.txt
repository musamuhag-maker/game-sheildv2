GAMESHIELD with admin approval (MongoDB + Mongoose + Express backend)

FOLDERS
  server/   Node.js + Express + Mongoose API (deploy this on Render)
  app/      Android app (built by GitHub Actions)

A) DATABASE (MongoDB Atlas, free)
1. Create a free M0 cluster at mongodb.com/atlas.
2. Database Access > add a user + password.
3. Network Access > Add IP Address > Allow access from anywhere (0.0.0.0/0).
4. Connect > Drivers > copy the connection string. Replace <password>, and put /gameshield before the "?".

B) SERVER (Render, free)
1. Push this whole folder to GitHub.
2. Render > New > Web Service > pick the repo.
3. Root Directory: server    Build: npm install    Start: npm start
4. Environment variables:
     MONGODB_URI     the Atlas string
     JWT_SECRET      a long random string
     ADMIN_EMAIL     musamuhag@gmail.com
     ADMIN_PASSWORD  your own admin password (set it ONLY here, never in code)
5. Deploy, then copy the URL (https://something.onrender.com).

C) APP
1. Open app/src/main/java/com/gameshield/app/Api.kt and set BASE to your Render URL.
2. Push to GitHub, wait for Actions > Build APK, install GameShield-apk.

D) USE
- Admin signs in with ADMIN_EMAIL / ADMIN_PASSWORD, uses the app normally, and has an "Admin panel" button.
- Anyone else taps "Request access". They wait on a waiting screen until the admin taps Approve.
- Call rejecting only works for approved accounts (and the admin).
- Free Render sleeps when idle: the first login after a while can take up to a minute. Call blocking itself never needs the server.
