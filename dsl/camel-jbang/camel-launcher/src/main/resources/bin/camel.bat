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

@REM --- Shared Java 17+ discovery contract ---
@REM Candidates in order: JAVACMD, %JAVA_HOME%\bin\java.exe, java.exe on PATH, CAMEL_FALLBACK_JAVA.
set CAMEL_MIN_JAVA=17

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

@REM Find the launcher JAR
set "LAUNCHER_JAR="
for %%i in ("%BASEDIR%\camel-launcher-*.jar") do set "LAUNCHER_JAR=%%i"

@REM Execute Camel CLI, preserving arguments and the child exit code
"%JAVACMD%" %JAVA_OPTS% -jar "%LAUNCHER_JAR%" %*
set ERROR_CODE=%ERRORLEVEL%

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
set "_RAW="
for /f "tokens=3" %%v in ('""%_CAND%" -version" ^<NUL 2^>^&1 ^| findstr /i "version"') do if not defined _RAW set "_RAW=%%v"
if not defined _RAW exit /b 1
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
