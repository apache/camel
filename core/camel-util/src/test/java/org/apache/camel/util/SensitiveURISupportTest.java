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
package org.apache.camel.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SensitiveURISupportTest {

    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    public void testDesensitizeUriWithUserInfoAndColonPasswordThenRestore() {
        String uri = "sftp://USERNAME:HARRISON:COLON@sftp.server.test";
        // e.g. sftp://USERNAME:beb43aea-755e-45f0-b282-f0647cf48e37@sftp.server.test
        String desensitizedUri = URISupport.desensitizeUri(uri);
        assertNotEquals(uri, desensitizedUri);
        assertEquals(uri, URISupport.restoreSensitiveUri(desensitizedUri));
    }

    @Test
    public void testDesensitizeUriWithUserInfoAndColonPasswordThenRestoreSecretRef() {
        String sensitiveKey = URISupport.storeSensitive("HARRISON:COLON");
        assertTrue(UUID_PATTERN.matcher(sensitiveKey).matches());

        // e.g. sftp://USERNAME:beb43aea-755e-45f0-b282-f0647cf48e37@sftp.server.test
        String uri = String.format("sftp://USERNAME:%s@sftp.server.test", sensitiveKey);
        assertNotEquals("sftp://USERNAME:HARRISON:COLON@sftp.server.test", uri);

        String restoredUri = URISupport.restoreSensitiveUri(uri);
        assertEquals("sftp://USERNAME:HARRISON:COLON@sftp.server.test", restoredUri);

        restoredUri = String.format("sftp://USERNAME:%s@sftp.server.test", URISupport.getStoredSensitive(sensitiveKey));
        assertEquals("sftp://USERNAME:HARRISON:COLON@sftp.server.test", restoredUri);
    }

    @Test
    public void testDesensitiseWithPasswordThenRestore() {
        String uri = "http://foo?username=me&password=RAW(me#@123)&foo=bar";
        // e.g. http://foo?username=7b6c7551-4dde-4639-b403-a3420e5bb095&password=9010400f-7cd5-4984-9fd2-e1bc8273d90e&foo=bar
        String desensitizedUri = URISupport.desensitizeUri(uri);
        assertNotEquals(uri, desensitizedUri);
        assertEquals(uri, URISupport.restoreSensitiveUri(desensitizedUri));
    }

    @Test
    public void testDesensitiseWithPasswordUpdateThenRestore() throws Exception {
        String uri = "http://foo?username=me&password=RAW(me#@123)&foo=bar";
        // e.g. http://foo?username=7b6c7551-4dde-4639-b403-a3420e5bb095&password=9010400f-7cd5-4984-9fd2-e1bc8273d90e&foo=bar
        String desensitizedUri = URISupport.desensitizeUri(uri);
        assertNotEquals(desensitizedUri, uri);

        Map<String, Object> parsed = URISupport.parseParameters(new URI(desensitizedUri));
        String sensitiveKey = (String) parsed.get("password");
        assertTrue(URISupport.isStoredSensitive(sensitiveKey));

        String restoredUri = URISupport.restoreSensitiveUri(desensitizedUri);
        assertEquals("http://foo?username=me&password=RAW(me#@123)&foo=bar", restoredUri);

        URISupport.updateStoredSensitive(sensitiveKey, "new-password");
        restoredUri = URISupport.restoreSensitiveUri(desensitizedUri);
        assertEquals("http://foo?username=me&password=new-password&foo=bar", restoredUri);
    }

    @Test
    public void testDesensitiseUriThenParse() throws Exception {
        String username = "me";
        String password = "me#@1)23";
        String uri1 = String.format("http://foo?username=%s&password=%s&foo=bar", username, password);

        // e.g http://foo?username=7df6d0d7-6454-410a-8b8f-fad4b9a07ac1&password=39a8cc44-beb6-4120-9c9f-145475a6c481&foo=bar
        String desensitizedUri = URISupport.desensitizeUri(uri1);

        Map<String, Object> parsed = URISupport.parseParameters(new URI(desensitizedUri));
        assertEquals(3, parsed.size());

        String passwordSensitiveKey = (String) parsed.get("password");
        String usernameSensitiveKey = (String) parsed.get("username");

        assertTrue(URISupport.isStoredSensitive(passwordSensitiveKey));
        assertTrue(URISupport.isStoredSensitive(usernameSensitiveKey));

        assertEquals(password, URISupport.getStoredSensitive(passwordSensitiveKey));
        assertEquals(username, URISupport.getStoredSensitive(usernameSensitiveKey));
    }

    @Test
    public void testCreateRemainingUriWithSecretThenParse() throws Exception {
        URI original = new URI("http://camel.apache.org");
        Map<String, Object> param = new HashMap<>();
        param.put("username", URISupport.storeSensitive("S\u00F8ren"));
        param.put("password", URISupport.storeSensitive("++?w0rd"));

        // e.g. http://camel.apache.org?password=8b4a8b49-f713-4019-9e3b-3ac01a74f9cf&username=399a1075-8aea-4d92-94a2-f20250388a85
        URI newUri = URISupport.createRemainingURI(original, param);
        assertNotNull(newUri);

        Map<String, Object> parsed = URISupport.parseParameters(newUri);
        assertEquals(2, parsed.size());

        String usernameSensitiveKey = (String) parsed.get("username");
        String passwordSensitiveKey = (String) parsed.get("password");

        assertTrue(URISupport.isStoredSensitive(passwordSensitiveKey));
        assertTrue(URISupport.isStoredSensitive(usernameSensitiveKey));

        assertEquals("S\u00F8ren", URISupport.getStoredSensitive(usernameSensitiveKey));
        assertEquals("++?w0rd", URISupport.getStoredSensitive(passwordSensitiveKey));
    }

    @Test
    public void testCreateRemainingUriWithSecretParseThenRestore() throws Exception {
        URI original = new URI("http://camel.apache.org");
        Map<String, Object> param = new HashMap<>();
        param.put("username", URISupport.storeSensitive("S\u00F8ren"));
        param.put("password", URISupport.storeSensitive("++?w0rd"));

        // e.g. http://camel.apache.org?password=8b4a8b49-f713-4019-9e3b-3ac01a74f9cf&username=399a1075-8aea-4d92-94a2-f20250388a85
        URI newUri = URISupport.createRemainingURI(original, param);
        assertNotNull(newUri);

        String restoredUri = URISupport.restoreSensitiveUri(newUri.toString());
        assertEquals("http://camel.apache.org?password=++?w0rd&username=S\u00F8ren", restoredUri);
    }

    @Test
    public void testCreateQueryStringWithSecretThenUpdate() throws Exception {
        Map<String, Object> map = new HashMap<>();
        String sensitiveKey = URISupport.storeSensitive("hidden-secret");
        map.put("password", sensitiveKey);

        // e.g. password=d05f449e-51ee-4451-8252-bd42cd4a88ec
        String q1 = URISupport.createQueryString(map);
        String passwordSensitiveKey = (String) URISupport.parseQuery(q1).get("password");

        assertTrue(URISupport.isStoredSensitive(passwordSensitiveKey));
        assertEquals("hidden-secret", URISupport.getStoredSensitive(passwordSensitiveKey));
    }

    @Test
    public void testStore() {
        String sensitiveKey = URISupport.storeSensitive("1234");

        assertTrue(URISupport.isStoredSensitive(sensitiveKey));
        assertTrue(UUID_PATTERN.matcher(sensitiveKey).matches());
        assertEquals("1234", URISupport.getStoredSensitive(sensitiveKey));
        assertNull(URISupport.storeSensitive(null));
    }

    @Test
    public void testStoreMultiple() {
        String sensitiveKey1 = URISupport.storeSensitive("1234");
        String sensitiveKey2 = URISupport.storeSensitive("1234");

        assertNotEquals(sensitiveKey1, sensitiveKey2);
    }

    @Test
    public void testUpdate() {
        String sensitiveKey = URISupport.storeSensitive("1234");

        assertTrue(URISupport.updateStoredSensitive(sensitiveKey, "4321"));
        assertTrue(URISupport.isStoredSensitive(sensitiveKey));
        assertEquals("4321", URISupport.getStoredSensitive(sensitiveKey));
    }

    @Test
    public void testUpdateInvalid() {
        String notStored = "not-stored";
        assertFalse(URISupport.updateStoredSensitive(notStored, "4321"));
        assertFalse(URISupport.updateStoredSensitive(null, "4321"));
        assertFalse(URISupport.updateStoredSensitive(notStored, null));
    }

    @Test
    public void testRemove() {
        String sensitiveKey = URISupport.storeSensitive("1234");

        assertTrue(UUID_PATTERN.matcher(sensitiveKey).matches());
        assertTrue(URISupport.isStoredSensitive(sensitiveKey));
        assertTrue(URISupport.removeStoredSensitive(sensitiveKey));
        assertFalse(URISupport.isStoredSensitive(sensitiveKey));
        assertNull(URISupport.getStoredSensitive(sensitiveKey));
    }

    @Test
    public void testRemoveMultiple() {
        String sensitiveKey = URISupport.storeSensitive("1234");
        assertTrue(URISupport.removeStoredSensitive(sensitiveKey));
        assertFalse(URISupport.removeStoredSensitive(sensitiveKey));
    }

    @Test
    public void testRemoveInvalid() {
        UUID notStored = UUID.randomUUID();
        assertFalse(URISupport.removeStoredSensitive(notStored.toString()));
        assertFalse(URISupport.removeStoredSensitive(null));
    }
}
