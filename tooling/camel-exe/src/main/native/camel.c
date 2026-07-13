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

/*
 * Return a heap-allocated wide string holding the directory that contains this
 * exe, or NULL on failure. The caller must free() the result. Uses a doubling
 * loop so paths beyond MAX_PATH (260) work on Windows 10+ with long-path
 * support enabled.
 */
static wchar_t *get_exe_dir(void) {
    DWORD bufSize = MAX_PATH;
    wchar_t *buf = (wchar_t *) malloc(bufSize * sizeof(wchar_t));
    if (buf == NULL) {
        return NULL;
    }
    for (;;) {
        DWORD n = GetModuleFileNameW(NULL, buf, bufSize);
        if (n == 0) {
            free(buf);
            return NULL;
        }
        if (n < bufSize) {
            break;
        }
        bufSize *= 2;
        wchar_t *tmp = (wchar_t *) realloc(buf, bufSize * sizeof(wchar_t));
        if (tmp == NULL) {
            free(buf);
            return NULL;
        }
        buf = tmp;
    }
    wchar_t *slash = wcsrchr(buf, L'\\');
    if (slash == NULL) {
        free(buf);
        return NULL;
    }
    *slash = L'\0';
    return buf;
}

int wmain(void) {
    wchar_t *exePath = get_exe_dir();
    if (exePath == NULL) {
        fwprintf(stderr, L"camel: cannot resolve launcher directory\n");
        return 1;
    }

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
        free(exePath);
        return 1;
    }
    int ret = _snwprintf_s(cmdline, cap, _TRUNCATE,
                           L"cmd.exe /S /C \"\"%s\\camel.bat\" %s\"", exePath, tail);
    if (ret == -1) {
        fwprintf(stderr, L"camel: command line was truncated\n");
        free(cmdline);
        free(exePath);
        return 1;
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    /* bInheritHandles=TRUE and no STARTF_USESTDHANDLES: child shares our console. */
    if (!CreateProcessW(NULL, cmdline, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        fwprintf(stderr, L"camel: failed to start camel.bat (error %lu)\n", GetLastError());
        free(cmdline);
        free(exePath);
        return 1;
    }

    WaitForSingleObject(pi.hProcess, INFINITE);
    DWORD code = 1;
    if (!GetExitCodeProcess(pi.hProcess, &code)) {
        fwprintf(stderr, L"camel: failed to get exit code (error %lu)\n", GetLastError());
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    free(cmdline);
    free(exePath);
    return (int) code;
}
