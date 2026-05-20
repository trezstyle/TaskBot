@echo off
setlocal enabledelayedexpansion

set ERROR_CODE=0

if not defined JAVA_HOME (
    echo Error: JAVA_HOME is not set.
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
    exit /b 1
)

set "DIRNAME=%~dp0"
if "%DIRNAME%" == "" set DIRNAME=.
set "APP_BASE_NAME=%~n0"
set "APP_HOME=%DIRNAME%"

for %%i in ("%APP_HOME%") do set "APP_HOME=%%~fi"

set "WRAPPER_JAR=%APP_HOME%\.mvn\wrapper\maven-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
    echo Maven wrapper jar not found: %WRAPPER_JAR%
    exit /b 1
)

set "MAVEN_OPTS=-Xmx1024m"

if exist "%APP_HOME%\.mvn\jvm.config" (
    for /f "usebackq delims=" %%p in ("%APP_HOME%\.mvn\jvm.config") do (
        set "JVM_CONFIG_MAVEN_PROPS=!JVM_CONFIG_MAVEN_PROPS! %%p"
    )
)

"%JAVA_HOME%\bin\java.exe" %JVM_CONFIG_MAVEN_PROPS% %MAVEN_OPTS% %MAVEN_DEBUG_OPTS% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%APP_HOME%" org.apache.maven.wrapper.MavenWrapperMain %*

exit /b %ERRORLEVEL%
