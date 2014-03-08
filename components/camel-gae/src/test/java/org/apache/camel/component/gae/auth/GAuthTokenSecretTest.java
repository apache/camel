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
package org.apache.camel.component.gae.auth;

import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.gae.auth.GAuthTokenSecret.COOKIE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GAuthTokenSecretTest {

    private GAuthTokenSecret tokenSecret;

    @Before
    public void setUp() {
        tokenSecret = new GAuthTokenSecret("abc123");
    }

    @Test
    public void testToCookie() {
        assertEquals(COOKIE_NAME + "=abc123", tokenSecret.toCookie());
    }

    @Test
    public void testFromCookieExist() {
        String cookie0 = " " + COOKIE_NAME + "=abc122";
        String cookie1 = " " + COOKIE_NAME + "=abc123 ";
        String cookie2 = " " + COOKIE_NAME + "=abc124; ";
        String cookie3 = " " + COOKIE_NAME + "=abc125;";
        String cookie4 = " " + COOKIE_NAME + "=abc126; x=y;";
        assertEquals("abc122", GAuthTokenSecret.fromCookie(cookie0).getValue());
        assertEquals("abc123", GAuthTokenSecret.fromCookie(cookie1).getValue());
        assertEquals("abc124", GAuthTokenSecret.fromCookie(cookie2).getValue());
        assertEquals("abc125", GAuthTokenSecret.fromCookie(cookie3).getValue());
        assertEquals("abc126", GAuthTokenSecret.fromCookie(cookie4).getValue());
    }

    @Test
    public void testFromCookieNotExist() {
        String cookie = "blah=abc123";
        assertNull(GAuthTokenSecret.fromCookie(cookie));
    }

}
