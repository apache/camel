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

package org.apache.camel.component.jms;

import jakarta.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.artemis.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.CamelTestSupportHelper;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

@Tags({ @Tag("jms") })
public abstract class AbstractJMSTest implements CamelTestSupportHelper, ConfigurableRoute, ConfigurableContext {

    @Order(1)
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    public static String queueNameForClass(String desiredName, Class<?> requestingClass) {
        return desiredName + "." + requestingClass.getSimpleName();
    }

    protected abstract String getComponentName();

    protected JmsComponent buildComponent(ConnectionFactory connectionFactory) {
        return jmsComponentAutoAcknowledge(connectionFactory);
    }

    protected JmsComponent setupComponent(
            CamelContext camelContext, ConnectionFactory connectionFactory, String componentName) {
        return buildComponent(connectionFactory);
    }

    protected JmsComponent setupComponent(
            CamelContext camelContext, ArtemisService service, String componentName) {
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service);

        return setupComponent(camelContext, connectionFactory, componentName);
    }

    protected abstract RouteBuilder createRouteBuilder();

    @ContextFixture
    @Override
    public synchronized void configureContext(CamelContext context) throws Exception {
        JmsComponent component = setupComponent(context, service, getComponentName());
        context.addComponent(getComponentName(), component);
    }

    @RouteFixture
    @Override
    public void createRouteBuilder(CamelContext context) throws Exception {
        final RouteBuilder routeBuilder = createRouteBuilder();

        if (routeBuilder != null) {
            context.addRoutes(routeBuilder);
        }
    }

}
