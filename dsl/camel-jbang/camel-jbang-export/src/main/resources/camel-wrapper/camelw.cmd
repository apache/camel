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
@REM Apache Camel Wrapper startup batch script
@REM
@REM Required ENV vars:
@REM   JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars:
@REM   CAMEL_OPTS - parameters passed to the Java VM when running Camel
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set "CAMEL_WRAPPER_DIR=%~dp0"

@REM Read properties file
set "CAMEL_PROPERTIES=%CAMEL_WRAPPER_DIR%.camel\camel-wrapper.properties"
if not exist "%CAMEL_PROPERTIES%" (
    echo Error: Could not find %CAMEL_PROPERTIES% >&2
    echo Please run 'camel wrapper' to set up the Camel wrapper. >&2
    exit /b 1
)

@REM Parse properties
set "CAMEL_VERSION="
set "DISTRIBUTION_URL="
for /f "usebackq tokens=1,* delims==" %%a in ("%CAMEL_PROPERTIES%") do (
    if "%%a"=="camel.version" set "CAMEL_VERSION=%%b"
    if "%%a"=="distributionUrl" set "DISTRIBUTION_URL=%%b"
)

if "%CAMEL_VERSION%"=="" (
    echo Error: camel.version not found in %CAMEL_PROPERTIES% >&2
    exit /b 1
)

if "%DISTRIBUTION_URL%"=="" (
    echo Error: distributionUrl not found in %CAMEL_PROPERTIES% >&2
    exit /b 1
)

@REM Find Java
if not "%JAVA_HOME%"=="" goto javaHomeSet
set "JAVACMD=java"
goto javaFound

:javaHomeSet
set "JAVACMD=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVACMD%" (
    echo Error: JAVA_HOME is set to an invalid directory: %JAVA_HOME% >&2
    echo Please set the JAVA_HOME variable in your environment to match the >&2
    echo location of your Java installation. >&2
    exit /b 1
)

:javaFound
@REM Set up cache directory
set "CAMEL_CACHE_DIR=%USERPROFILE%\.camel\wrapper"
set "CAMEL_LAUNCHER_JAR=%CAMEL_CACHE_DIR%\camel-launcher-%CAMEL_VERSION%.jar"

@REM Download launcher jar if needed
if exist "%CAMEL_LAUNCHER_JAR%" goto runCamel

if not exist "%CAMEL_CACHE_DIR%" mkdir "%CAMEL_CACHE_DIR%"
echo Downloading Camel Launcher %CAMEL_VERSION%...

@REM Try PowerShell download
powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%CAMEL_LAUNCHER_JAR%' }" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    @REM Try curl as fallback
    curl -fsSL -o "%CAMEL_LAUNCHER_JAR%" "%DISTRIBUTION_URL%" >nul 2>&1
    if %ERRORLEVEL% neq 0 (
        echo Error: Failed to download Camel Launcher from %DISTRIBUTION_URL% >&2
        del /f "%CAMEL_LAUNCHER_JAR%" >nul 2>&1
        exit /b 1
    )
)

echo Camel Launcher %CAMEL_VERSION% downloaded successfully.

:runCamel
"%JAVACMD%" %CAMEL_OPTS% -jar "%CAMEL_LAUNCHER_JAR%" %*

endlocal
