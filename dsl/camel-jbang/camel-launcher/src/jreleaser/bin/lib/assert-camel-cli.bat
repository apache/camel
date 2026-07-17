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
REM Usage: call assert-camel-cli.bat <camel-cmd> <workdir> [fixture]
REM Offline: runs `camel version` and `camel init`, checks generated content. Never starts a route.
setlocal

set "CAMELCMD=%~1"
set "WORKDIR=%~2"
if "%~3"=="" (
    set "_BASE=%~dp0"
    REM From lib/ -> .. -> bin/ -> .. -> jreleaser/ -> .. -> src/
    for %%I in ("%_BASE%\..\..") do set "_MOD_ROOT=%%~fI"
    set "FIXTURE=%_MOD_ROOT%\test\resources\validate\expected-init-route.txt"
) else (
    set "FIXTURE=%~3"
)

REM Step 1: version check
set "VERFILE=%WORKDIR%\ver.txt"
if exist "%VERFILE%" del /q "%VERFILE%" >nul 2>&1
call "%CAMELCMD%" --version >"%VERFILE%" 2>&1
if errorlevel 1 ( echo FAIL: 'camel version' exited nonzero & exit /b 1 )
for %%A in ("%VERFILE%") do if %%~zA==0 ( echo FAIL: 'camel version' printed nothing & exit /b 1 )
echo OK: camel version

REM Step 2: init route content check. Uses "hello.java" (not an arbitrary name) because the
REM fixture's class name is derived from this filename, same as the POSIX validators.
set "ROUTE=%WORKDIR%\hello.java"
if exist "%ROUTE%" del /q "%ROUTE%" >nul 2>&1
call "%CAMELCMD%" init hello.java >"%WORKDIR%\init.log" 2>&1
if errorlevel 1 ( echo FAIL: 'camel init' exited nonzero & exit /b 1 )
if not exist "%ROUTE%" ( echo FAIL: 'camel init' did not create route & exit /b 1 )

REM Every non-empty marker line from the fixture must appear in the generated route.
for /f "usebackq delims=" %%L in ("%FIXTURE%") do (
  findstr /c:"%%L" "%ROUTE%" >nul 2>&1 || ( echo FAIL: generated route missing line: %%L & exit /b 1 )
)
echo OK: camel init produced expected route content

REM Step 3: assert the executable exists at the given path
if exist "%CAMELCMD%" (
    echo OK: camel CLI executable found
) else (
    echo WARN: camel CLI not found at '%CAMELCMD%' (skipped)
)

exit /b 0
