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
package org.apache.camel.component.restlet;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

/**
 * @version 
 */
public class RestRestletPostJsonPojoOnExceptionTest extends RestletTestSupport {

    @Test
    public void testRestletPostPojoError() throws Exception {
        getMockEndpoint("mock:input").expectedMessageCount(0);
        getMockEndpoint("mock:error").expectedMessageCount(1);

        String body = "This is not json";
        try {
            template.sendBody("http://localhost:" + portNum + "/users/new", body);
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            HttpOperationFailedException cause = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
            assertEquals(400, cause.getStatusCode());
            assertEquals("Invalid json data", cause.getResponseBody());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use restlet on localhost with the given port
                // and enable auto binding mode
                restConfiguration().component("restlet").host("localhost").port(portNum).bindingMode(RestBindingMode.auto);

                onException(JsonParseException.class)
                    .handled(true)
                    .to("mock:error")
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                    .setBody().constant("Invalid json data");

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("new").type(UserPojo.class)
                        .to("mock:input");
            }
        };
    }
}
