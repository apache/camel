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

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SjmsComponentRestartTest extends CamelTestSupport {

    @BindToRegistry("activemqCF")
    private ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://broker?broker.persistent=false&broker.useJmx=false");
    
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRestartWithStopStart() throws Exception {
        SjmsComponent sjmsComponent = new SjmsComponent();
        sjmsComponent.setConnectionFactory((ConnectionFactory) context.getRegistry().lookupByName("activemqCF"));
        context.addComponent("sjms", sjmsComponent);

        RouteBuilder routeBuilder = new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                from("sjms:queue:test").to("mock:test");
            }
        };
        context.addRoutes(routeBuilder);

        context.start();

        getMockEndpoint("mock:test").expectedMessageCount(1);
        template.sendBody("sjms:queue:test", "Hello World");
        assertMockEndpointsSatisfied();

        // restart
        context.stop();

        // must add our custom component back again
        context.addComponent("sjms", sjmsComponent);

        context.start();

        getMockEndpoint("mock:test").expectedMessageCount(1);

        // and re-create template
        template = context.createProducerTemplate();
        template.sendBody("sjms:queue:test", "Hello World");
        assertMockEndpointsSatisfied();

        context.stop();
    }

    @Test
    public void testRestartWithSuspendResume() throws Exception {
        SjmsComponent sjmsComponent = new SjmsComponent();
        sjmsComponent.setConnectionFactory((ConnectionFactory) context.getRegistry().lookupByName("activemqCF"));
        context.addComponent("sjms", sjmsComponent);

        RouteBuilder routeBuilder = new RouteBuilder(context) {
            @Override
            public void configure() throws Exception {
                from("sjms:queue:test").to("mock:test");
            }
        };
        context.addRoutes(routeBuilder);

        context.start();

        getMockEndpoint("mock:test").expectedMessageCount(1);
        template.sendBody("sjms:queue:test", "Hello World");
        assertMockEndpointsSatisfied();

        // restart
        context.suspend();
        context.resume();

        getMockEndpoint("mock:test").expectedMessageCount(1);

        template.sendBody("sjms:queue:test", "Hello World");
        assertMockEndpointsSatisfied();

        context.stop();
    }
}
