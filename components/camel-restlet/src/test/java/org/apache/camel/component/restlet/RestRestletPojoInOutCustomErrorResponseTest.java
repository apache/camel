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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

/**
 * @version 
 */
public class RestRestletPojoInOutCustomErrorResponseTest extends RestletTestSupport {

    @Test
    public void testRestletPojoInOutOk() throws Exception {
        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";
        String out = template.requestBody("http://localhost:" + portNum + "/users/lives", body, String.class);

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);
    }

    @Test
    public void testRestletPojoInOutError() throws Exception {
        final String body = "{\"id\": 77, \"name\": \"John Doe\"}";
        Exchange reply = template.request("http://localhost:" + portNum + "/users/lives?throwExceptionOnFailure=false", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(body);
            }
        });

        assertNotNull(reply);
        assertEquals("id value is too low", reply.getOut().getBody(String.class));
        assertEquals(400, reply.getOut().getHeader(Exchange.HTTP_RESPONSE_CODE));
        String type = reply.getOut().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertTrue(type.contains("text/plain"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("restlet").host("localhost").port(portNum).bindingMode(RestBindingMode.json);

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("lives").type(UserPojo.class).outType(CountryPojo.class)
                        .route()
                            .choice()
                                .when().simple("${body.id} < 100")
                                    .bean(new UserErrorService(), "idToLowError")
                                .otherwise()
                                    .bean(new UserService(), "livesWhere");
            }
        };
    }

}
