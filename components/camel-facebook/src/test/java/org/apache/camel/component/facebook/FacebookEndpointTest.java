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
package org.apache.camel.component.facebook;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Test preconfigured Facebook component.
 */
public class FacebookEndpointTest extends CamelFacebookTestSupport {

    public FacebookEndpointTest() throws Exception {
    }

    @Test
    public void testNoArgsEndpoint() throws Exception {
        final MockEndpoint mockEndpoint = getMockEndpoint("mock://testNoArgs");
        mockEndpoint.expectedMessageCount(1);

        template().requestBodyAndHeader("direct:testNoArgs", null,
            FacebookConstants.FACEBOOK_PROPERTY_PREFIX + "userId", "me");

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();
        final FacebookComponent component = new FacebookComponent();
        component.setConfiguration(getConfiguration());
        camelContext.addComponent("facebook", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:testNoArgs")
                    .to("facebook:getuserlikes")
                    .to("mock://testNoArgs");
            }
        };
    }
}
