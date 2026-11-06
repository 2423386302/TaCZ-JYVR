@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
 echo Set JAVA_HOME to a full JDK 17 installation first.
 pause
 exit /b 1
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
 echo JAVA_HOME must contain bin\javac.exe.
 pause
 exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -version
echo Requires JDK 17. Build output is saved to build-vr.log.
call gradlew.bat build jarJar -PvrReleaseBuild --no-daemon --max-workers=1 --stacktrace > build-vr.log 2>&1
set "BUILD_RESULT=%ERRORLEVEL%"
type build-vr.log
echo Exit code: %BUILD_RESULT%
pause
exit /b %BUILD_RESULT%
