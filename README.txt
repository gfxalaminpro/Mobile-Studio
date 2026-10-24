Mobile Studio Android Project

APK build without a PC (using GitHub Actions):
1. Create a new GitHub repository.
2. Extract this ZIP and upload all its contents to the repository root
   (the app, gradle, .github folders and Gradle files must be at the top level).
3. Open the repository's Actions tab.
4. Select "Build APK" and click "Run workflow" (or push to main/master).
5. When the workflow finishes, open its run and download the "MobileStudio-APK"
   artifact. Extract it to find app-debug.apk, then install it on your Android phone.

Requirements:
- The project needs internet access during the build to download Gradle/Android
  dependencies.
- Android may ask you to allow installation from this source.
- The app requests screen-capture and audio permissions when starting a stream.
- Streaming requires a valid RTMP server URL and stream key.

Note: This archive contains Android source code, not a prebuilt APK.
