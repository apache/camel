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
setlocal enabledelayedexpansion
set "SCRIPT_DIR=%~dp0"
set "SUPPORTED_LTS=%SCRIPT_DIR%..\supported-lts.yml"

@REM Tests may point this at a synthetic LTS allowlist so expiry-date assertions don't ride on
@REM real production supportEnds dates (mirrors CAMEL_PACKAGE_TEST_VERSION further below).
if not "%CAMEL_PACKAGE_TEST_SUPPORTED_LTS%"=="" (
  if not "%CAMEL_PACKAGE_TEST_MODE%"=="true" (
    echo Error: CAMEL_PACKAGE_TEST_SUPPORTED_LTS requires CAMEL_PACKAGE_TEST_MODE=true. 1>&2
    exit /b 2
  )
  set "SUPPORTED_LTS=%CAMEL_PACKAGE_TEST_SUPPORTED_LTS%"
)

set "SUBCOMMAND=%~1"
if "%SUBCOMMAND%"=="" goto usage
shift
if /I not "%SUBCOMMAND%"=="prepare" if /I not "%SUBCOMMAND%"=="publish" (
  echo Error: unknown subcommand '%SUBCOMMAND%'. 1>&2
  goto usage
)

set "CHANNEL="
set "LTS_LINE="
set "PRINT_PLAN=0"
:parse
if "%~1"=="" goto parsed
if /I "%~1"=="--channel" ( set "CHANNEL=%~2" & shift & shift & goto parse )
if /I "%~1"=="--lts-line" ( set "LTS_LINE=%~2" & shift & shift & goto parse )
if /I "%~1"=="--print-plan" ( set "PRINT_PLAN=1" & shift & goto parse )
echo Error: unknown argument '%~1'. 1>&2
goto usage
:parsed

if /I "%CHANNEL%"=="stable" goto channelOk
if /I "%CHANNEL%"=="lts" goto channelOk
echo Error: --channel must be 'stable' or 'lts' (got '%CHANNEL%'). 1>&2
goto usage
:channelOk

if /I "%CHANNEL%"=="lts" if "%LTS_LINE%"=="" (
  echo Error: --channel lts requires --lts-line X.Y. 1>&2
  goto usage
)

if not "%LTS_LINE%"=="" (
  if not exist "%SUPPORTED_LTS%" (
    echo Error: supported LTS metadata is not readable: %SUPPORTED_LTS% 1>&2
    exit /b 1
  )
  findstr /r /c:"^[ ][ ]*-[ ][ ]*line:" /c:"^-[ ][ ]*line:" "%SUPPORTED_LTS%" >nul
  if errorlevel 1 (
    echo Error: supported LTS metadata is malformed: %SUPPORTED_LTS% 1>&2
    exit /b 1
  )
  set "SUPPORT_ENDS="
  for /f "usebackq tokens=1,2,3" %%a in ("%SUPPORTED_LTS%") do (
    if "%%a"=="-" if "%%b"=="line:" ( set "CUR=%%c" & set "CUR=!CUR:"=!" )
    if "%%a"=="supportEnds:" if "!CUR!"=="%LTS_LINE%" ( set "SE=%%b" & set "SUPPORT_ENDS=!SE:"=!" )
  )
  if "!SUPPORT_ENDS!"=="" (
    echo Error: '%LTS_LINE%' is not a supported LTS line ^(see supported-lts.yml^). 1>&2
    exit /b 2
  )
  REM wmic is deprecated/removed on newer Windows (24H2+); a missing wmic previously left TODAY
  REM empty and silently skipped this check instead of failing. PowerShell is present on every
  REM supported Windows version, and the findstr guard below fails loudly if it ever isn't.
  set "TODAY="
  for /f "usebackq delims=" %%d in (`powershell -NoProfile -Command "(Get-Date).ToString('yyyy-MM-dd')"`) do set "TODAY=%%d"
  echo !TODAY!| findstr /r "^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]$" >nul
  if errorlevel 1 (
    echo Error: could not resolve today's date via PowerShell ^(got '!TODAY!'^). 1>&2
    exit /b 1
  )
  if "!TODAY!" GTR "!SUPPORT_ENDS!" (
    echo Error: LTS line '%LTS_LINE%' support ended on !SUPPORT_ENDS!. 1>&2
    exit /b 2
  )
)

if /I "%CHANNEL%"=="stable" (
  set "PACKAGERS=brew,sdkman,winget,scoop,chocolatey"
  set "BREW_FORMULA=camel"
  set "BREW_CLASS=Camel"
  set "SDKMAN_DEFAULT=true"
  set "WEBSITE_LATEST=true"
  set "BREW_LTS_FORMULA="
  if not "%LTS_LINE%"=="" set "BREW_LTS_FORMULA=camel@%LTS_LINE%"
) else (
  REM Homebrew's own versioned-formula convention names the *file* "camel@X.Y.rb" but the
  REM Ruby *class* "CamelATxy" (dot removed) - e.g. real homebrew-core "python@3.11.rb"
  REM contains "class PythonAT311". As of the pinned JReleaser plugin version in pom.xml,
  REM JReleaser does not apply this convention itself
  REM (a literal formulaName "camel@4.20" renders invalid Ruby and a wrong output filename;
  REM BREW_CLASS below is passed as formulaName instead, and
  REM JReleaser's kebab-cased output file is renamed to the real "camel@X.Y.rb" after
  REM packaging - see the rename step below the mvn invocation.
  set "PACKAGERS=brew,sdkman,winget,chocolatey"
  set "BREW_FORMULA=camel@%LTS_LINE%"
  set "LTS_DIGITS=%LTS_LINE:.=%"
  set "BREW_CLASS=CamelAT!LTS_DIGITS!"
  set "SDKMAN_DEFAULT=false"
  set "WEBSITE_LATEST=false"
  set "BREW_LTS_FORMULA="
)

if "%PRINT_PLAN%"=="1" (
  echo CHANNEL=%CHANNEL%
  echo LTS_LINE=%LTS_LINE%
  echo PACKAGERS=!PACKAGERS!
  echo SDKMAN_CANDIDATE=camel
  echo SDKMAN_DEFAULT=!SDKMAN_DEFAULT!
  echo WEBSITE_VERSION_MANIFEST=true
  echo WEBSITE_LATEST=!WEBSITE_LATEST!
  if not "!BREW_LTS_FORMULA!"=="" echo BREW_LTS_FORMULA=!BREW_LTS_FORMULA!
  if not "!BREW_FORMULA!"=="" echo BREW_FORMULA=!BREW_FORMULA!
  if not "!BREW_CLASS!"=="" echo BREW_CLASS=!BREW_CLASS!
  exit /b 0
)

if /I "%SUBCOMMAND%"=="publish" (
  echo Error: 'publish' is not yet implemented ^(Phase 5^). 1>&2
  exit /b 2
)

set "MODULE_DIR=%SCRIPT_DIR%..\..\.."

@REM Resolve the release version. Production always reads Maven's project.version; tests/CI may
@REM override it, but only with both CAMEL_PACKAGE_TEST_MODE=true and CAMEL_PACKAGE_TEST_VERSION
@REM set, so production can never accidentally skip the real Maven version.
if not "%CAMEL_PACKAGE_TEST_VERSION%"=="" (
  if not "%CAMEL_PACKAGE_TEST_MODE%"=="true" (
    echo Error: CAMEL_PACKAGE_TEST_VERSION requires CAMEL_PACKAGE_TEST_MODE=true. 1>&2
    exit /b 2
  )
  set "PROJECT_VERSION=%CAMEL_PACKAGE_TEST_VERSION%"
) else (
  set "PROJECT_VERSION="
  for /f "usebackq delims=" %%v in (`mvn -q -B -ntp -f "%MODULE_DIR%\pom.xml" org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=project.version -DforceStdout`) do set "PROJECT_VERSION=%%v"
  if errorlevel 1 (
    echo Error: could not resolve project.version via Maven. 1>&2
    exit /b 1
  )
  if "!PROJECT_VERSION!"=="" (
    echo Error: could not resolve project.version via Maven. 1>&2
    exit /b 1
  )
)

if "!PROJECT_VERSION:~-8!"=="SNAPSHOT" (
  echo Error: refusing to prepare packages for a snapshot version '!PROJECT_VERSION!'. 1>&2
  exit /b 2
)

set "TAR=%MODULE_DIR%\target\camel-launcher-!PROJECT_VERSION!-bin.tar.gz"
set "ZIP=%MODULE_DIR%\target\camel-launcher-!PROJECT_VERSION!-bin.zip"
set "INSTALL_SH_SRC=%MODULE_DIR%\src\install\install.sh"
set "INSTALL_PS1_SRC=%MODULE_DIR%\src\install\install.ps1"

if not exist "!TAR!" (
  echo Error: release TAR not found: !TAR! 1>&2
  exit /b 1
)
if not exist "!ZIP!" (
  echo Error: release ZIP not found: !ZIP! 1>&2
  exit /b 1
)
if not exist "!INSTALL_SH_SRC!" (
  echo Error: installer source not found: !INSTALL_SH_SRC! 1>&2
  exit /b 1
)
if not exist "!INSTALL_PS1_SRC!" (
  echo Error: installer source not found: !INSTALL_PS1_SRC! 1>&2
  exit /b 1
)

@REM Recreate only the prepared website staging directory (leave the rest of target\jreleaser alone).
set "WEBSITE_DIR=%MODULE_DIR%\target\jreleaser\website"
if exist "!WEBSITE_DIR!" rd /s /q "!WEBSITE_DIR!"
mkdir "!WEBSITE_DIR!"

copy /y "!INSTALL_SH_SRC!" "!WEBSITE_DIR!\install.sh" >nul
if errorlevel 1 (
  echo Error: failed to copy install.sh. 1>&2
  exit /b 1
)
copy /y "!INSTALL_PS1_SRC!" "!WEBSITE_DIR!\install.ps1" >nul
if errorlevel 1 (
  echo Error: failed to copy install.ps1. 1>&2
  exit /b 1
)

fc /b "!INSTALL_SH_SRC!" "!WEBSITE_DIR!\install.sh" >nul
if errorlevel 1 (
  echo Error: copied install.sh does not match its source. 1>&2
  exit /b 1
)
fc /b "!INSTALL_PS1_SRC!" "!WEBSITE_DIR!\install.ps1" >nul
if errorlevel 1 (
  echo Error: copied install.ps1 does not match its source. 1>&2
  exit /b 1
)

call java "%MODULE_DIR%\src\jreleaser\java\WebsiteManifestGenerator.java" --version !PROJECT_VERSION! --tar "!TAR!" --zip "!ZIP!" --output "!WEBSITE_DIR!\camel-cli" --latest !WEBSITE_LATEST!
if errorlevel 1 (
  echo Error: website manifest generation failed. 1>&2
  exit /b 1
)

@REM jreleaser.yml reads the Homebrew formula name via `{{ Env.CAMEL_PKG_BREW_FORMULA }}`,
@REM which becomes the generated Ruby class name directly, so this must be BREW_CLASS (a
@REM valid Ruby class name), not the human-facing BREW_FORMULA. JReleaser's `Env.` template
@REM prefix resolves real OS environment variables, not Maven -D system properties, so this
@REM must be a real env var.
set "CAMEL_PKG_BREW_FORMULA=!BREW_CLASS!"

@REM Non-empty only for a versioned formula (e.g. "camel@4.20"); formula.rb.tpl uses
@REM this to add `keg_only :versioned_formula` and its PATH caveat.
set "CAMEL_PKG_BREW_VERSIONED="
echo !BREW_FORMULA! | findstr /C:"@" >nul && set "CAMEL_PKG_BREW_VERSIONED=!BREW_FORMULA!"

@REM `-Djreleaser.dry.run=true` below never performs a network call, so a placeholder
@REM satisfies JReleaser's config validation (it requires a non-blank release token)
@REM without requiring a real credential. Never overrides a token the caller already set.
if "%JRELEASER_GITHUB_TOKEN%"=="" set "JRELEASER_GITHUB_TOKEN=dry-run-placeholder"

@REM `-Djreleaser.distributions` / `-Djreleaser.packagers` are the JReleaser Maven
@REM plugin's own include filters (confirmed via `mvn help:describe` on the pinned
@REM 1.25.0 plugin), used to select which packagers run for this channel; e.g. `lts`
@REM excludes `scoop` by omitting it from !PACKAGERS!.
@REM
@REM `-Djreleaser.project.version` is NOT a real parameter of this Mojo (confirmed via
@REM `mvn help:describe -Dgoal=prepare/config/package -Ddetail=true`: none of them expose it).
@REM JReleaser always reads the real Maven project.version from the POM itself, regardless of
@REM !PROJECT_VERSION! above (which only governs our own SNAPSHOT guard, artifact lookup, and the
@REM website manifest). What actually gates every packager above (`active: RELEASE`) is
@REM JReleaser's own snapshot detection, which matches the *real* POM version against
@REM `jreleaser.project.snapshot.pattern` (default `.*-SNAPSHOT`) - so test-mode runs from
@REM a SNAPSHOT checkout would otherwise skip every packager even once our own guard has been
@REM satisfied via a CAMEL_PACKAGE_TEST_VERSION override. In that one case, override the pattern
@REM to something that can never match, so JReleaser treats the real POM version as a release too.
set "SNAPSHOT_PATTERN_ARG="
if "%CAMEL_PACKAGE_TEST_MODE%"=="true" if not "%CAMEL_PACKAGE_TEST_VERSION%"=="" set "SNAPSHOT_PATTERN_ARG=-Djreleaser.project.snapshot.pattern=CAMEL_LAUNCHER_NEVER_MATCH_SNAPSHOT_PATTERN"
echo Preparing packages for channel '%CHANNEL%' ^(packagers: !PACKAGERS!^)...
call mvn -B -ntp -f "%MODULE_DIR%\pom.xml" -Djreleaser.distributions=camel-cli -Djreleaser.packagers=!PACKAGERS! !SNAPSHOT_PATTERN_ARG! jreleaser:config jreleaser:prepare jreleaser:package -Djreleaser.dry.run=true
if errorlevel 1 exit /b %ERRORLEVEL%

@REM Homebrew's own versioned-formula convention names the *file* "camel@X.Y.rb" but
@REM the pinned JReleaser plugin derives the output filename from formulaName's literal text
@REM (kebab-casing our "CamelAT420" class name to "camel-at-420.rb"), not from Homebrew's
@REM file-naming rule. The generated formula content already has the correct class name, so only
@REM the filename needs fixing up here.
if /I "%CHANNEL%"=="lts" (
  set "BREW_FORMULA_DIR=%MODULE_DIR%\target\jreleaser\package\camel-cli\brew\Formula"
  if exist "!BREW_FORMULA_DIR!" (
    set "GENERATED_FILE="
    set "MULTI_FOUND=0"
    for %%f in ("!BREW_FORMULA_DIR!\*.rb") do (
      if not "!GENERATED_FILE!"=="" set "MULTI_FOUND=1"
      set "GENERATED_FILE=%%f"
    )
    if "!MULTI_FOUND!"=="1" (
      echo Error: expected exactly one generated Homebrew formula file in !BREW_FORMULA_DIR!, found multiple. 1>&2
      exit /b 1
    )
    if "!GENERATED_FILE!"=="" (
      echo Error: expected exactly one generated Homebrew formula file in !BREW_FORMULA_DIR!, found none. 1>&2
      exit /b 1
    )
    if /I not "!GENERATED_FILE!"=="!BREW_FORMULA_DIR!\!BREW_FORMULA!.rb" (
      move /y "!GENERATED_FILE!" "!BREW_FORMULA_DIR!\!BREW_FORMULA!.rb" >nul
      if errorlevel 1 (
        echo Error: failed to rename generated Homebrew formula to !BREW_FORMULA!.rb. 1>&2
        exit /b 1
      )
      echo Renamed generated Homebrew formula to Homebrew's versioned-formula file convention: !BREW_FORMULA!.rb
    )
  )
)
@REM The POSIX wrapper has a Homebrew-only test-mode patch after packaging. That patch is not
@REM duplicated here because Homebrew package validation runs on macOS/Linux, not Windows.
exit /b 0

:usage
echo Usage: camel-package.bat ^<prepare^|publish^> --channel ^<stable^|lts^> [--lts-line X.Y] [--print-plan] 1>&2
exit /b 2
