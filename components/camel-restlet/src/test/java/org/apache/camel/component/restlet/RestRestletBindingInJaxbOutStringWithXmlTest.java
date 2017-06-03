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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

/**
 * @version 
 */
public class RestRestletBindingInJaxbOutStringWithXmlTest extends RestletTestSupport {

    @Test
    public void testBindingMode() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:input");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(UserJaxbPojo.class);

        String body = "<user name=\"Donald Duck\" id=\"123\"></user>";
        String reply = template.requestBody("http://localhost:" + portNum + "/users/new", body, String.class);

        assertEquals("<message>Thanks for calling us</message>", reply);

        assertMockEndpointsSatisfied();

        UserJaxbPojo user = mock.getReceivedExchanges().get(0).getIn().getBody(UserJaxbPojo.class);
        assertNotNull(user);
        assertEquals(123, user.getId());
        assertEquals("Donald Duck", user.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration().component("restlet").host("localhost").port(portNum).bindingMode(RestBindingMode.auto)
                        // turn off must be JAXB as we create the output type ourselves as xml in a String type
                        .dataFormatProperty("xml.out.mustBeJAXBElement", "false");

                // use the rest DSL to define the rest services
                rest("/users/")
                    .post("new").consumes("application/xml").produces("application/xml").type(UserJaxbPojo.class)
                        .route()
                        .to("mock:input")
                        .transform().constant("<message>Thanks for calling us</message>");
            }
        };
    }
}
