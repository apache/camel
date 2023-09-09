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
package org.apache.camel.component.sjms.manual;

import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.support.MyAsyncComponent;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.impl.CamelTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Manual test")
public class ManualFromQueueManualTest extends CamelTestSupport {

    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // using failover will automatic re-connect with ActiveMQ
    // private String url = "failover:tcp://localhost:61616";
    private String url = "tcp://localhost:61616";

    @Test
    public void testConsume() throws Exception {
        getMockEndpoint("mock:foo").expectedMinimumMessageCount(3);

        MockEndpoint.assertIsSatisfied(context, 1, TimeUnit.MINUTES);
    }

    @Override
    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RoutesBuilder routesBuilder = createRouteBuilder();

        if (routesBuilder != null) {
            context.addRoutes(routesBuilder);
        }
    }

    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sjms:queue:foo?asyncStartListener=true")
                        .to("log:foo")
                        .to("mock:foo");
            }
        };
    }

    @ContextFixture
    public void configureComponent(CamelContext context) {
        context.addComponent("async", new MyAsyncComponent());
    }

    @Override
    @RouteFixture
    protected void configureCamelContext(CamelContext camelContext) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);
        SjmsComponent sjms = new SjmsComponent();
        log.info("Using live connection to existing ActiveMQ broker running on {}", url);
        sjms.setConnectionFactory(connectionFactory);

        camelContext.addComponent("sjms", sjms);
    }
}
