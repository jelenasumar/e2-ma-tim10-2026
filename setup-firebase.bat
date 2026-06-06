@echo off
echo ============================================
echo  Slagalica - Firebase setup (jednom)
echo ============================================
echo.
echo 1. Prijava u Firebase CLI (otvorice se browser):
echo.
call npx firebase-tools login
echo.
echo 2. Objavljivanje Firestore pravila...
echo.
call npx firebase-tools deploy --only firestore:rules --project slagalica-5e6d9
echo.
echo 3. U browseru ukljuci Anonymous prijavu:
echo    https://console.firebase.google.com/project/slagalica-5e6d9/authentication/providers
echo.
pause
