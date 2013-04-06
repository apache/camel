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
package org.apache.camel.web.util;


import org.apache.camel.test.junit4.TestSupport;
import org.junit.Test;

public class UriCharactersEncoderTest extends TestSupport {

    @Test
    public void testEncoder() {
        String afterEncoding = "direct:%2F%2Fstart";
        String beforeEncoding = "direct://start";

        String result = UriCharactersEncoder.encode(beforeEncoding);
        assertEquals("Get the wrong encoding result", afterEncoding, result);
    }

    @Test
    public void testNoEncoding() {
        String noEncoding = "direct:start\uFD04";
        log.info("The non-encoded endpoint {} has the lenght {}", noEncoding.length());
        String result = UriCharactersEncoder.encode(noEncoding);
        assertEquals("Get the wrong encoding result", noEncoding, result);
    }

}
