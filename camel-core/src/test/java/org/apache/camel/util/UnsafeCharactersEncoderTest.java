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
package org.apache.camel.util;

import junit.framework.TestCase;

public class UnsafeCharactersEncoderTest extends TestCase {
    public void testQnameEncoder() {
        String afterEncoding = "%7Bhttp://www.example.com/test%7DServiceName";
        String beforeEncoding = "{http://www.example.com/test}ServiceName";

        String result = UnsafeUriCharactersEncoder.encode(beforeEncoding);
        assertEquals("Get the wrong encoding result", result, afterEncoding);
    }

    public void testNoEncoding() {
        String noEncoding = "http://www.example.com";
        String result = UnsafeUriCharactersEncoder.encode(noEncoding);
        assertEquals("Get the wrong encoding result", result, noEncoding);
    }
}
