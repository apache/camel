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

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.apache.camel.test.infra.activemq.services.ActiveMQServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

@Tags({ @Tag("jms") })
public abstract class AbstractJMSTest extends CamelTestSupport {
    @RegisterExtension
    public ActiveMQService service = ActiveMQServiceFactory.createVMService();

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

    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        ConnectionFactory connectionFactory = ConnectionFactoryHelper.createConnectionFactory(service);

        return setupComponent(camelContext, connectionFactory, componentName);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        JmsComponent component = setupComponent(camelContext, service, getComponentName());
        camelContext.addComponent(getComponentName(), component);
        return camelContext;
    }
}
