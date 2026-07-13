/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * camel.exe - minimal WinGet-compatible bootstrap for the Camel CLI launcher.
 *
 * It locates the camel.bat sitting in the same directory, forwards the caller's
 * exact command-line tail (preserving Unicode and Windows quoting), inherits the
 * standard streams, and returns the child process exit code. It performs NO Java
 * discovery and NO Camel option parsing; that all lives in camel.bat.
 */

#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

/* Return the command-line tail after argv[0], preserving the caller's quoting. */
static LPWSTR skip_argv0(LPWSTR cmd) {
    LPWSTR p = cmd;
    if (*p == L'"') {
        p++;
        while (*p && *p != L'"') {
            p++;
        }
        if (*p == L'"') {
            p++;
        }
    } else {
        while (*p && *p != L' ' && *p != L'\t') {
            p++;
        }
    }
    while (*p == L' ' || *p == L'\t') {
        p++;
    }
    return p;
}

int wmain(void) {
    wchar_t exePath[MAX_PATH];
    DWORD n = GetModuleFileNameW(NULL, exePath, MAX_PATH);
    if (n == 0 || n >= MAX_PATH) {
        fwprintf(stderr, L"camel: launcher path exceeds MAX_PATH (%d chars) or cannot be resolved\n", MAX_PATH);
        return 1;
    }

    /* Strip the exe filename, leaving the directory. */
    wchar_t *slash = wcsrchr(exePath, L'\\');
    if (slash == NULL) {
        fwprintf(stderr, L"camel: unexpected launcher path\n");
        return 1;
    }
    *slash = L'\0';

    LPWSTR tail = skip_argv0(GetCommandLineW());

    /*
     * cmd.exe /S /C ""<dir>\camel.bat" <tail>"
     * With /S and a command wrapped in an outer pair of quotes, cmd strips the
     * first and last quote and executes the remainder verbatim, so the inner
     * quotes around the (possibly spaced/Unicode) camel.bat path survive.
     */
    size_t cap = wcslen(exePath) + wcslen(tail) + 64;
    LPWSTR cmdline = (LPWSTR) malloc(cap * sizeof(wchar_t));
    if (cmdline == NULL) {
        fwprintf(stderr, L"camel: out of memory\n");
        return 1;
    }
    _snwprintf_s(cmdline, cap, _TRUNCATE,
                 L"cmd.exe /S /C \"\"%s\\camel.bat\" %s\"", exePath, tail);

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    /* bInheritHandles=TRUE and no STARTF_USESTDHANDLES: child shares our console. */
    if (!CreateProcessW(NULL, cmdline, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        fwprintf(stderr, L"camel: failed to start camel.bat (error %lu)\n", GetLastError());
        free(cmdline);
        return 1;
    }

    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    free(cmdline);
    return (int) code;
}
