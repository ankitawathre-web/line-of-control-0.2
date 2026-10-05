LOCATION LOGBOOK - APK kaise banayein (GitHub par, free)

1. github.com par free account banayein (agar nahi hai) aur login karein.
2. Upar "+" > "New repository" > naam: LocationLogbook > Create repository.
3. "uploading an existing file" link par click karein.
4. Is zip ko unzip karein. Andar ke SAARE items (app folder, .github folder,
   build.gradle.kts, settings.gradle.kts, gradle.properties) drag karke daalein.
   Note: ".github" folder hidden hota hai (Mac: Cmd+Shift+. dabayein).
   Ye folder zaroori hai, isi se APK banti hai.
5. "Commit changes" dabayein.
6. "Actions" tab kholein > "Build APK" ke chalne ka wait karein (3-6 minute).
   Green tick aane par us run par click karein.
7. Neeche "Artifacts" mein "LocationLogbook-APK" download karein (zip hai),
   unzip karke app-debug.apk phone mein bhej dein.
8. Phone mein APK kholein > "Install" (agar poochhe to "Install unknown apps"
   allow karein).
9. App kholein > "Tracking shuru karein" > Location allow karein ("While using
   the app" kaafi hai) aur Notification allow karein.
10. Phone Settings > Apps > Location Logbook > Battery > "Unrestricted" karein,
    warna kuch phones background mein app band kar dete hain.

Build fail ho to Actions mein error ka screenshot/text mujhe bhej dein.
