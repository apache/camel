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

import junit.framework.TestCase;

import org.junit.Test;

import static org.apache.camel.util.StringHelper.camelCaseToDash;

public class StringHelperTest extends TestCase {

    @Test
    public void testCamelCashToDash() throws Exception {
        assertEquals("hello-world", camelCaseToDash("HelloWorld"));
        assertEquals("hello-big-world", camelCaseToDash("HelloBigWorld"));
        assertEquals("hello-big-world", camelCaseToDash("Hello-bigWorld"));
        assertEquals("my-id", camelCaseToDash("MyId"));
        assertEquals("my-id", camelCaseToDash("MyID"));
        assertEquals("my-url", camelCaseToDash("MyUrl"));
        assertEquals("my-url", camelCaseToDash("MyURL"));
        assertEquals("my-big-id", camelCaseToDash("MyBigId"));
        assertEquals("my-big-id", camelCaseToDash("MyBigID"));
        assertEquals("my-big-url", camelCaseToDash("MyBigUrl"));
        assertEquals("my-big-url", camelCaseToDash("MyBigURL"));
        assertEquals("my-big-id-again", camelCaseToDash("MyBigIdAgain"));
        assertEquals("my-big-id-again", camelCaseToDash("MyBigIDAgain"));
        assertEquals("my-big-url-again", camelCaseToDash("MyBigUrlAgain"));
        assertEquals("my-big-url-again", camelCaseToDash("MyBigURLAgain"));

        assertEquals("use-mdc-logging", camelCaseToDash("UseMDCLogging"));
        assertEquals("mdc-logging-keys-pattern", camelCaseToDash("MDCLoggingKeysPattern"));
    }
}
