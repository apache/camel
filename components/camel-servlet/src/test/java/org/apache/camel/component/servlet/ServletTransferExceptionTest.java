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
package org.apache.camel.component.servlet;

import java.io.ByteArrayInputStream;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpConstants;
import org.apache.camel.http.common.HttpHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServletTransferExceptionTest extends ServletCamelRouterTestSupport {

    @Test
    public void testTransferException() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/hello",
                new ByteArrayInputStream("".getBytes()), "text/plain");
        WebResponse response = query(req, false);

        assertEquals(500, response.getResponseCode());
        assertEquals(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT, response.getContentType());
        Object object = HttpHelper.deserializeJavaObjectFromStream(response.getInputStream(), null);
        assertNotNull(object);

        IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, object);
        assertEquals("Damn", cause.getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("servlet:hello?transferException=true")
                        .throwException(new IllegalArgumentException("Damn"));
            }
        };
    }
}
