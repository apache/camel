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

import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebRequest;
import com.meterware.httpunit.WebResponse;
import com.meterware.servletunit.ServletUnitClient;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpConstants;
import org.apache.camel.http.common.HttpHelper;
import org.junit.Test;

public class ServletTransferExceptionTest extends ServletCamelRouterTestSupport {

    @Test
    public void testTransferException() throws Exception {
        WebRequest req = new PostMethodWebRequest(CONTEXT_URL + "/services/hello", new ByteArrayInputStream("".getBytes()), "text/plain");
        ServletUnitClient client = newClient();
        client.setExceptionsThrownOnErrorStatus(false);
        WebResponse response = client.getResponse(req);

        assertEquals(500, response.getResponseCode());
        assertEquals(HttpConstants.CONTENT_TYPE_JAVA_SERIALIZED_OBJECT, response.getContentType());
        Object object = HttpHelper.deserializeJavaObjectFromStream(response.getInputStream());
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
