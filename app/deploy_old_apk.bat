REM start emulator from GUI
REM adb uninstall com.maknoon.audiocataloger; it will not allow to test upgrade since it will remove the old one. use -r install

adb install -r app-release.apk
REM adb install -r "E:\AudioCataloger Media\Archive.Android\1.2\app-release.apk"
adb shell am start -n "com.maknoon.audiocataloger/com.maknoon.audiocataloger.MainActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
pause