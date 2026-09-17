@REM Apache Maven Wrapper startup batch script, version 3.3.2
@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%HOME%"=="" set "HOME=%HOMEDRIVE%%HOMEPATH%"

set "MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%"
if not "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir
set "EXEC_DIR=%CD%"
set "WDIR=%EXEC_DIR%"
:findBaseDir
if exist "%WDIR%\.mvn" goto baseDirFound
cd ..
if "%WDIR%"=="%CD%" goto baseDirNotFound
set "WDIR=%CD%"
goto findBaseDir
:baseDirFound
set "MAVEN_PROJECTBASEDIR=%WDIR%"
cd /d "%EXEC_DIR%"
goto endDetectBaseDir
:baseDirNotFound
set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
cd /d "%EXEC_DIR%"
:endDetectBaseDir

if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" goto OkJHome
set "JAVA_HOME="
for /d %%j in ("C:\Program Files\Java\jdk-26*") do set "JAVA_HOME=%%j"
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" goto OkJHome
for /d %%j in ("C:\Program Files\Java\jdk-*") do set "JAVA_HOME=%%j"
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" goto OkJHome
echo Error: JAVA_HOME not found. Install JDK 26 and set JAVA_HOME. >&2
exit /b 1

:OkJHome
if exist "%JAVA_HOME%\bin\java.exe" goto init
echo Error: JAVA_HOME is invalid: "%JAVA_HOME%" >&2
exit /b 1

:init
set "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
if exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties" (
  for /f "usebackq tokens=1,2 delims==" %%A in ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") do (
    if /I "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
  )
)

if exist "%WRAPPER_JAR%" goto run
echo Downloading Maven Wrapper...
powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
if errorlevel 1 (
  echo Failed to download maven-wrapper.jar from %WRAPPER_URL% >&2
  exit /b 1
)
if not exist "%WRAPPER_JAR%" (
  echo maven-wrapper.jar missing after download: "%WRAPPER_JAR%" >&2
  exit /b 1
)

:run
"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CONFIG% %*
exit /b %ERRORLEVEL%
