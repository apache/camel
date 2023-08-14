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
package org.apache.camel.component.sjms;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SjmsComponentRestartTest extends CamelTestSupport {

    @RegisterExtension
    public ArtemisService service = ArtemisServiceFactory.createSingletonVMService();
    @BindToRegistry("activemqCF")
    private ActiveMQConnectionFactory connectionFactory
            = new ActiveMQConnectionFactory(service.serviceAddress());

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRestartWithStopStart() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder(context) {
            @Override
            public void configure() {
                from("sjms:queue:test.SjmsComponentRestartTest").to("mock:test");
            }
        };
        context.addRoutes(routeBuilder);

        context.start();

        getMockEndpoint("mock:test").expectedMessageCount(1);
        template.sendBody("sjms:queue:test.SjmsComponentRestartTest", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        // restart
        context.stop();

        MockEndpoint.resetMocks(context);

        // rebind as the registry is cleared on stop
        context.getRegistry().bind("activemqCF", connectionFactory);

        context.start();

        getMockEndpoint("mock:test").expectedMessageCount(1);

        // and re-create template
        template = context.createProducerTemplate();
        template.sendBody("sjms:queue:test.SjmsComponentRestartTest", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        context.stop();
    }

    @Test
    public void testRestartWithSuspendResume() throws Exception {
        RouteBuilder routeBuilder = new RouteBuilder(context) {
            @Override
            public void configure() {
                from("sjms:queue:test.SjmsComponentRestartTest").to("mock:test");
            }
        };
        context.addRoutes(routeBuilder);

        context.start();

        getMockEndpoint("mock:test").expectedMessageCount(1);
        template.sendBody("sjms:queue:test.SjmsComponentRestartTest", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        // restart
        context.suspend();
        context.resume();

        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:test").expectedMessageCount(1);

        template.sendBody("sjms:queue:test.SjmsComponentRestartTest", "Hello World");
        MockEndpoint.assertIsSatisfied(context);

        context.stop();
    }
}
