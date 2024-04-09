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
package org.apache.camel.component.asterisk;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@Disabled("CAMEL-10321: Set host, username and password test asterisk producer.")
public class AsteriskProducerTest implements ConfigurableRoute, CamelTestSupportHelper, ConfigurableContext {

    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();

    private String hostname = "192.168.0.254";
    private String username = "username";
    private String password = "password";
    private String action = AsteriskAction.EXTENSION_STATE.name();

    protected CamelContext context;
    protected ProducerTemplate template;

    @BeforeEach
    void setupContext() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
    }

    @Test
    void testSnmpProducer() throws Exception {
        template.sendBody("direct:in", "");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:in")
                        .to("asterisk://myVoIP?hostname=" + hostname + "&username=" + username + "&password=" + password
                            + "&action=" + action)
                        .log("${body}")
                        .to("mock:result");
            }
        };
    }

    @Override
    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

    @Override
    public void configureContext(CamelContext context) throws Exception {

    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }
}
