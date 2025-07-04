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
@REM Camel JBang Launcher Start Up Batch script
@REM ----------------------------------------------------------------------------

@REM Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@REM Find the project base dir
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set BASEDIR=%DIRNAME%

@REM Java command to use
if "%JAVA_HOME%" == "" goto noJavaHome
set JAVACMD=%JAVA_HOME%\bin\java.exe
goto checkJavaCmd

:noJavaHome
set JAVACMD=java.exe

:checkJavaCmd
if exist "%JAVACMD%" goto init

echo.
echo Error: JAVA_HOME is not defined correctly. >&2
echo Cannot execute "%JAVACMD%" >&2
echo.
goto error

:init
@REM Set JVM options if specified
if "%JAVA_OPTS%" == "" set JAVA_OPTS=-Xmx512m

@REM Find the JAR file
dir /b "%BASEDIR%\camel-launcher-*.jar" > nul 2>&1
if not errorlevel 1 (
  for /f "tokens=*" %%j in ('dir /b "%BASEDIR%\camel-launcher-*.jar"') do set LAUNCHER_JAR=%BASEDIR%\%%j
) else (
  for %%i in ("%BASEDIR%\camel-launcher-*.jar") do set LAUNCHER_JAR=%%i
)

@REM Execute Camel JBang
"%JAVACMD%" %JAVA_OPTS% -jar "%LAUNCHER_JAR%" %*
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@REM End local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" endlocal

exit /B %ERROR_CODE%
