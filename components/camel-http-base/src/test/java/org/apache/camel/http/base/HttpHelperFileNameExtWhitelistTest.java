/*
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
package org.apache.camel.http.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the shared {@code fileNameExtWhitelist} check used by the HTTP bindings and the platform-http-vertx
 * consumer (CAMEL-24652). The important property is that an extension is matched as a whole comma-separated token and
 * never as a substring of the whitelist.
 */
class HttpHelperFileNameExtWhitelistTest {

    @Test
    void nullWhitelistAcceptsEverything() {
        assertTrue(HttpHelper.isFileNameExtWhitelisted(null, "evil.exe"));
    }

    @Test
    void starWhitelistAcceptsEverything() {
        assertTrue(HttpHelper.isFileNameExtWhitelisted("*", "evil.exe"));
    }

    @Test
    void aNameWithoutAnExtensionIsAccepted() {
        assertTrue(HttpHelper.isFileNameExtWhitelisted("txt", "noextension"));
    }

    @Test
    void anExactExtensionIsAccepted() {
        assertTrue(HttpHelper.isFileNameExtWhitelisted("txt", "notes.txt"));
    }

    @Test
    void aSubstringOfTheWhitelistIsNotAccepted() {
        // the bug this guards: "txt".contains("x") must not accept "evil.x"
        assertFalse(HttpHelper.isFileNameExtWhitelisted("txt", "evil.x"));
        assertFalse(HttpHelper.isFileNameExtWhitelisted("txt", "evil.t"));
        assertFalse(HttpHelper.isFileNameExtWhitelisted("txt", "evil.tx"));
    }

    @Test
    void commaSeparatedTokensAreMatchedExactlyAndTrimmed() {
        assertTrue(HttpHelper.isFileNameExtWhitelisted("txt,pdf", "report.pdf"));
        assertTrue(HttpHelper.isFileNameExtWhitelisted("txt, pdf", "report.pdf"));
        assertFalse(HttpHelper.isFileNameExtWhitelisted("txt,pdf", "report.doc"));
    }

    @Test
    void theMatchIsCaseInsensitive() {
        assertTrue(HttpHelper.isFileNameExtWhitelisted("TXT", "notes.txt"));
        assertTrue(HttpHelper.isFileNameExtWhitelisted("txt", "NOTES.TXT"));
    }

    @Test
    void aMultiDotNameIsMatchedOnItsFullExtensionChain() {
        // FileUtil.onlyExt returns everything after the first dot, so the whole chain must be whitelisted
        assertFalse(HttpHelper.isFileNameExtWhitelisted("gz", "archive.tar.gz"));
        assertTrue(HttpHelper.isFileNameExtWhitelisted("tar.gz", "archive.tar.gz"));
    }

    @Test
    void aDoubleExtensionIsNotAcceptedOnItsTrailingExtension() {
        // evil.php.jpg must not pass a "jpg" whitelist: its extension chain is "php.jpg", not "jpg"
        assertFalse(HttpHelper.isFileNameExtWhitelisted("jpg", "evil.php.jpg"));
    }
}
