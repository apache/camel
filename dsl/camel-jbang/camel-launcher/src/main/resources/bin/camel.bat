@echo off
@REM
@REM Licensed to the Apache Software Foundation (ASF) under one or more
@REM contributor license agreements.  See the NOTICE file distributed with
@REM this work for additional information regarding copyright ownership.
@REM The ASF licenses this file to You under the Apache License, Version 2.0
@REM (the "License"); you may not use this file except in compliance with
@REM the License.  You may obtain a copy of the License at
@REM
@REM      http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@REM ----------------------------------------------------------------------------
@REM Camel CLI Launcher Start Up Batch script
@REM ----------------------------------------------------------------------------

@REM Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@REM Find the project base dir
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set BASEDIR=%DIRNAME%

@REM --- Shared Java 21+ discovery contract ---
@REM Candidates in order: JAVACMD, %JAVA_HOME%\bin\java.exe, java.exe on PATH, CAMEL_FALLBACK_JAVA.
set CAMEL_MIN_JAVA=21

set "CAMEL_JAVACMD="
call :tryJava "%JAVACMD%"
if defined CAMEL_JAVACMD goto haveJava
if defined JAVA_HOME call :tryJava "%JAVA_HOME%\bin\java.exe"
if defined CAMEL_JAVACMD goto haveJava
for %%p in (java.exe) do call :tryJava "%%~$PATH:p"
if defined CAMEL_JAVACMD goto haveJava
call :tryJava "%CAMEL_FALLBACK_JAVA%"
if defined CAMEL_JAVACMD goto haveJava
goto noJava

:haveJava
set "JAVACMD=%CAMEL_JAVACMD%"

@REM Set JVM options if specified
if "%JAVA_OPTS%" == "" set JAVA_OPTS=-Xmx512m

@REM Find the launcher JAR in lib\ beside bin\, excluding sources and javadoc. Its manifest has the class path (the
@REM other JARs in lib\).
set "LAUNCHER_JAR="
for %%i in ("%BASEDIR%\..\lib\camel-launcher-*.jar") do (
  echo %%~nxi| findstr /i /e /l /c:"-sources.jar" /c:"-javadoc.jar" > nul
  if errorlevel 1 if not defined LAUNCHER_JAR set "LAUNCHER_JAR=%%~fi"
)

@REM Class data sharing: the JVM keeps the classes that a command loads in an archive (one per command and Camel
@REM version, in %LOCALAPPDATA%\camel-cli\cds), which it creates when the command runs the first time, or again when
@REM the JDK changed, so the command starts faster from then on. CAMEL_CDS=false turns it off, and so do CDS or AOT
@REM options of JAVA_OPTS. A JVM without CDS (OpenJ9) ignores the options.
set "CDS_FILE="
if /i "%CAMEL_CDS%"=="false" goto cdsDone
if not defined LAUNCHER_JAR goto cdsDone
if not defined LOCALAPPDATA goto cdsDone
setlocal EnableDelayedExpansion
set "_CDS_FILE="
if not "!JAVA_OPTS:Xshare=!"=="!JAVA_OPTS!" goto cdsEnd
if not "!JAVA_OPTS:SharedArchiveFile=!"=="!JAVA_OPTS!" goto cdsEnd
if not "!JAVA_OPTS:AOTCache=!"=="!JAVA_OPTS!" goto cdsEnd
if not "!JAVA_OPTS:AOTMode=!"=="!JAVA_OPTS!" goto cdsEnd
@REM the archive of the command, when the first argument is the name of one (letters, digits and -)
set "_CDS_CMD=camel"
set "_CDS_ARG=%~1"
if not defined _CDS_ARG goto cdsCommandDone
if "!_CDS_ARG:~0,1!"=="-" goto cdsCommandDone
@REM the dot is not removed, so the variable stays defined
set "_CDS_REST=.!_CDS_ARG!"
for %%c in (a b c d e f g h i j k l m n o p q r s t u v w x y z 0 1 2 3 4 5 6 7 8 9 -) do set "_CDS_REST=!_CDS_REST:%%c=!"
if "!_CDS_REST!"=="." set "_CDS_CMD=!_CDS_ARG!"
:cdsCommandDone
for %%j in ("!LAUNCHER_JAR!") do set "_CDS_VERSION=%%~nj"
set "_CDS_VERSION=!_CDS_VERSION:camel-launcher-=!"
set "_CDS_DIR=!LOCALAPPDATA!\camel-cli\cds\!_CDS_VERSION!"
@REM the JVM does not start when the directory of the archive is missing
if not exist "!_CDS_DIR!\" mkdir "!_CDS_DIR!" 2> nul
if not exist "!_CDS_DIR!\" goto cdsEnd
set "_CDS_FILE=!_CDS_DIR!\!_CDS_CMD!.jsa"
@REM the JVM does not replace an empty archive (one it stopped writing)
for %%f in ("!_CDS_FILE!") do if exist "%%~f" if "%%~zf"=="0" del "%%~f" 2> nul
:cdsEnd
endlocal & set "CDS_FILE=%_CDS_FILE%"
:cdsDone
set "CDS_OPTS="
if defined CDS_FILE set CDS_OPTS=-XX:+AutoCreateSharedArchive "-XX:SharedArchiveFile=%CDS_FILE%" "-Xlog:cds*=off"

@REM Execute Camel CLI, preserving arguments and the child exit code
for %%e in ("%JAVACMD%") do set "_JAVACMD_EXT=%%~xe"
if /i "%_JAVACMD_EXT%"==".bat" goto executeJavaBatch
if /i "%_JAVACMD_EXT%"==".cmd" goto executeJavaBatch
goto executeJavaNative

:executeJavaBatch
call "%JAVACMD%" %JAVA_OPTS% %CDS_OPTS% -jar "%LAUNCHER_JAR%" %*
set ERROR_CODE=%ERRORLEVEL%
goto javaExecuted

:executeJavaNative
"%JAVACMD%" %JAVA_OPTS% %CDS_OPTS% -jar "%LAUNCHER_JAR%" %*
set ERROR_CODE=%ERRORLEVEL%

:javaExecuted
@REM End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal & set ERROR_CODE=%ERROR_CODE%
exit /B %ERROR_CODE%

:noJava
echo. 1>&2
echo Error: no suitable Java runtime found. Camel CLI requires Java %CAMEL_MIN_JAVA% or newer. 1>&2
echo Checked: JAVACMD, %%JAVA_HOME%%\bin\java.exe, java.exe on PATH, CAMEL_FALLBACK_JAVA. 1>&2
echo Install a Java %CAMEL_MIN_JAVA%+ runtime and set JAVA_HOME or JAVACMD, or add java.exe to PATH. 1>&2
if "%OS%"=="Windows_NT" endlocal
exit /B 1

:tryJava
@REM %1 = candidate path (quoted). On success sets CAMEL_JAVACMD.
set "_CAND=%~1"
if "%_CAND%"=="" exit /b 1
if not exist "%_CAND%" exit /b 1
@REM The release file of the JDK (bin\..\release) has the version, which saves starting a JVM for -version.
set "_RAW="
for %%d in ("%_CAND%") do set "_RELEASE=%%~dpd..\release"
if exist "%_RELEASE%" for /f "tokens=2 delims==" %%v in ('findstr /b /l /c:"JAVA_VERSION=" "%_RELEASE%"') do if not defined _RAW set "_RAW=%%v"
set "_RELEASE="
if defined _RAW goto tryJavaParseVersion
for %%e in ("%_CAND%") do set "_CAND_EXT=%%~xe"
set "_PROBE_DIR="
if defined TEMP set "_PROBE_DIR=%TEMP%"
if not defined _PROBE_DIR if defined TMP set "_PROBE_DIR=%TMP%"
if not defined _PROBE_DIR set "_PROBE_DIR=%BASEDIR%"

:tryJavaProbePath
set "_PROBE=%_PROBE_DIR%\camel-java-probe-%RANDOM%-%RANDOM%.tmp"
if exist "%_PROBE%" goto tryJavaProbePath
if /i "%_CAND_EXT%"==".bat" goto tryJavaProbeBatch
if /i "%_CAND_EXT%"==".cmd" goto tryJavaProbeBatch
goto tryJavaProbeNative

:tryJavaProbeBatch
call "%_CAND%" -version <NUL >"%_PROBE%" 2>&1
set "_PROBE_RC=%ERRORLEVEL%"
goto tryJavaProbeComplete

:tryJavaProbeNative
"%_CAND%" -version <NUL >"%_PROBE%" 2>&1
set "_PROBE_RC=%ERRORLEVEL%"

:tryJavaProbeComplete
if "%_PROBE_RC%"=="0" goto tryJavaParseProbe
del /q "%_PROBE%" >NUL 2>&1
set "_PROBE="
exit /b 1

:tryJavaParseProbe
set "_RAW="
for /f "tokens=3" %%v in ('findstr /b /l /i /c:"java version " /c:"openjdk version " "%_PROBE%"') do if not defined _RAW set "_RAW=%%v"
del /q "%_PROBE%" >NUL 2>&1
set "_PROBE="
if not defined _RAW exit /b 1

:tryJavaParseVersion
set "_RAW=%_RAW:"=%"
set "_MAJOR="
for /f "tokens=1,2 delims=." %%a in ("%_RAW%") do (
  if "%%a"=="1" (set "_MAJOR=%%b") else (set "_MAJOR=%%a")
)
if not defined _MAJOR exit /b 1
@REM Reject non-numeric majors (e.g. early-access "17-ea") so the numeric compare below is safe.
for /f "delims=0123456789" %%z in ("%_MAJOR%") do exit /b 1
if %_MAJOR% LSS %CAMEL_MIN_JAVA% exit /b 1
set "CAMEL_JAVACMD=%_CAND%"
exit /b 0
