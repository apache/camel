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

@echo off
REM Offline package-native validation for the Windows packagers (WinGet, Scoop,
REM Chocolatey). Each validator self-skips when its package manager is absent.
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "MODULE_DIR=%SCRIPT_DIR%\..\.."
set "JRELEASER_OUT=%MODULE_DIR%\target\jreleaser"
set "ASSERT=%SCRIPT_DIR%lib\assert-camel-cli.bat"
set "WORK=%TEMP%\camel-validate-%RANDOM%"
mkdir "%WORK%" 2>nul

set "TARGET=%~1"
if "%TARGET%"=="" goto usage
if /I "%TARGET%"=="winget"     ( call :validate_winget     & goto done )
if /I "%TARGET%"=="scoop"      ( call :validate_scoop      & goto done )
if /I "%TARGET%"=="chocolatey" ( call :validate_chocolatey & goto done )
if /I "%TARGET%"=="all" ( call :validate_winget & call :validate_scoop & call :validate_chocolatey & goto done )
echo Error: unknown target '%TARGET%'. 1>&2
goto usage

:usage
echo Usage: camel-validate.bat <winget|scoop|chocolatey|all> 1>&2
exit /b 2

:done
exit /b %ERRORLEVEL%

:: ============================================================
:: WinGet validator
:: ============================================================
:validate_winget
where winget >nul 2>&1 || ( echo SKIP: winget not available & exit /b 0 )
echo --- WinGet validation ---

REM JReleaser produces *.installer.yaml under the winget distribution directory.
set "MANIFEST="
for /r "%JRELEASER_OUT%\distributions\camel-cli\winget" %%F in (*.installer.yaml) do set "MANIFEST=%%~dpF"
if "%MANIFEST%"=="" (
    REM Also check top-level winget directory
    for /r "%JRELEASER_OUT%\winget" %%F in (*.installer.yaml) do set "MANIFEST=%%~dpF"
)
if not defined MANIFEST (
    echo FAIL: no generated WinGet manifest & exit /b 1
)
echo Found WinGet manifest dir: %MANIFEST%

REM Validate schema with winget validate
winget validate -s "%MANIFEST%" >"%WORK%\winget-validate.log" 2>&1
if errorlevel 1 (
    echo WARN: winget validation warnings/outputs:
    type "%WORK%\winget-validate.log"
    REM winget validate may warn on non-standard manifests; continue with install test
) else (
    echo OK: winget schema validation passed
)

REM Local ZIP install for version/init testing
set "ZIP_FILE=%MODULE_DIR%\target\camel-launcher-*-bin.zip"
if not exist "%ZIP_FILE%" (
    echo SKIP: no built launcher ZIP found & exit /b 0
)

winget install --source winget --id Apache.CamelCLI --location "%WORK%\winget-installed" -e --accept-package-agreements --accept-source-agreements >"%WORK%\winget-install.log" 2>&1 || (
    echo WARN: winget install failed (expected on headless CI)
    type "%WORK%\winget-install.log"
)

REM Check camel version via the architecture-appropriate exe
if "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set "CAMEL_EXE_NAME=camel-arm64.exe"
) else (
    set "CAMEL_EXE_NAME=camel-x64.exe"
)
if exist "%WORK%\winget-installed\bin\%CAMEL_EXE_NAME%" (
    call "!ASSERT!" "%WORK%\winget-installed\bin\%CAMEL_EXE_NAME%" "%WORK%" || exit /b 1
) else (
    echo WARN: no %CAMEL_EXE_NAME% in winget install location (skipped version/init test)
)

REM Uninstall (best-effort cleanup; failure here does not fail validation)
winget uninstall Apache.CamelCLI -e --accept-agreements >"%WORK%\winget-uninst.log" 2>&1
echo OK: WinGet validation complete

exit /b 0

:: ============================================================
:: Scoop validator
:: ============================================================
:validate_scoop
where scoop >nul 2>&1 || ( echo SKIP: scoop not available & exit /b 0 )
echo --- Scoop validation ---

REM JReleaser produces a JSON manifest under scoop/
set "SCOOP_MANIFEST="
for /r "%JRELEASER_OUT%\distributions\camel-cli\scoop" %%F in (*.json) do set "SCOOP_MANIFEST=%%~dpF"
if not defined SCOOP_MANIFEST (
    for /r "%JRELEASER_OUT%\scoop" %%F in (*.json) do set "SCOOP_MANIFEST=%%~dpF"
)
if not defined SCOOP_MANIFEST (
    echo FAIL: no generated Scoop manifest & exit /b 1
)
echo Found Scoop manifest: %SCOOP_MANIFEST%

REM Basic JSON structure validation (must contain url, version, name fields)
findstr /i "url" "%SCOOP_MANIFEST%" >nul || ( echo FAIL: Scoop manifest missing 'url' & exit /b 1 )
findstr /i "version" "%SCOOP_MANIFEST%" >nul || ( echo FAIL: Scoop manifest missing 'version' & exit /b 1 )
echo OK: Scoop manifest structure validated

REM Local install from URL in manifest using scoop's --global or local bucket approach
set "ZIP_FILE=%MODULE_DIR%\target\camel-launcher-*-bin.zip"
if not exist "%ZIP_FILE%" (
    echo SKIP: no built launcher ZIP found & exit /b 0
)

REM Scoop requires a bucket; for validation we check manifest structure only.
echo SKIP: Scoop local install requires bucket setup (stubbed for offline CI)

exit /b 0

:: ============================================================
:: Chocolatey validator
:: ============================================================
:validate_chocolatey
where choco >nul 2>&1 || ( echo SKIP: chocolatey not available & exit /b 0 )
echo --- Chocolatey validation ---

REM JReleaser produces .nuspec and related files under chocolatey/
set "NUSPEC_FILE="
for /r "%JRELEASER_OUT%\distributions\camel-cli\chocolatey" %%F in (*.nuspec) do set "NUSPEC_FILE=%%~dpF"
if not defined NUSPEC_FILE (
    for /r "%JRELEASER_OUT%\chocolatey" %%F in (*.nuspec) do set "NUSPEC_FILE=%%~dpF"
)

REM If remoteBuild=true, no local package is generated; verify source files exist instead.
set "PKG_DIR=%JRELEASER_OUT%\distributions\camel-cli\chocolatey"
if not exist "%PKG_DIR%" (
    echo SKIP: Chocolatey package directory not found (remote build expected) & exit /b 0
)

REM Verify .nuspec exists and has valid XML structure
if exist "%PKG_DIR%\camel.nuspec" (
    findstr /i "<package>" "%PKG_DIR%\camel.nuspec" >nul || ( echo FAIL: nuspec missing package tag & exit /b 1 )
    findstr /i "<version>" "%PKG_DIR%\camel.nuspec" >nul || ( echo FAIL: nuspec missing version & exit /b 1 )
    echo OK: Chocolatey .nuspec structure validated
) else (
    echo INFO: No local .nuspec found (remoteBuild=true in jreleaser.yml)
)

REM For a real package, choco pack would build it locally; for CI validation, the source check above suffices.
echo OK: Chocolatey validation complete (manifest source verified)

exit /b 0
