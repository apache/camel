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
package org.apache.camel.component.http;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.http.HttpStatus;

public class HttpCamelHeadersNotCopiedTest extends HttpCamelHeadersTest {

    @Override
    protected void assertHeaders(Map<String, Object> headers) {
        assertEquals("Should return " + HttpStatus.SC_OK, HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("Should return mocked 12 CL", "12", headers.get("Content-Length"));

        assertNotNull("Should have any Content-Type header propagated", headers.get("Content-Type"));

        assertNull("Should not copy TestHeader from in to out", headers.get("TestHeader"));
        assertNull("Should not copy Accept-Language from in to out", headers.get("Accept-Language"));
    }

    @Override
    protected String setupEndpointParams() {
        return "?copyHeaders=false";
    }
}
