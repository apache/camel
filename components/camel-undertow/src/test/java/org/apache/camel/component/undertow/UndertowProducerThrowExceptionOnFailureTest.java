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
package org.apache.camel.component.undertow;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class UndertowProducerThrowExceptionOnFailureTest extends BaseUndertowTest {

    @Test
    public void testFailWithoutException() throws Exception {
        String out = template().requestBody("undertow:http://localhost:{{port}}/fail?throwExceptionOnFailure=false", null, String.class);
        assertEquals("Fail", out);
    }

    @Test
    public void testFailWithException() throws Exception {
        try {
            template().requestBody("undertow:http://localhost:{{port}}/fail?throwExceptionOnFailure=true", null, String.class);
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(404, cause.getStatusCode());
        }
    }

    @Test
    public void testFailWithException2() throws Exception {
        try {
            fluentTemplate().to("undertow:http://localhost:{{port2}}/test/fail?throwExceptionOnFailure=true")
                    .withHeader(Exchange.HTTP_METHOD, "PUT")
                    .withBody("This is not JSON format")
                    .request(String.class);
            fail("Should throw an exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException httpException = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(400, httpException.getStatusCode());
            assertEquals("text/plain", httpException.getResponseHeaders().get(Exchange.CONTENT_TYPE));
            assertEquals("Invalid json data", httpException.getResponseBody());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                        .component("undertow").port(getPort2())
                        .bindingMode(RestBindingMode.json);

                onException(JsonParseException.class)
                        .handled(true)
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                        .setBody().constant("Invalid json data");

                rest("/test")
                        .put("/fail").to("mock:test");

                from("undertow:http://localhost:{{port}}/fail")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE).constant(404)
                        .transform(constant("Fail"));
            }
        };
    }
}
