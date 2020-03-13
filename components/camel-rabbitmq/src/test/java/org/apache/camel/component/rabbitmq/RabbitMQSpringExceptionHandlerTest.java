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
package org.apache.camel.component.rabbitmq;


import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.rabbitmq.testbeans.CustomRabbitExceptionHandler;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class RabbitMQSpringExceptionHandlerTest extends CamelSpringTestSupport {

    @EndpointInject("rabbitmq:localhost:5672/foo?username=cameltest&password=cameltest&connectionFactoryExceptionHandler=#customExceptionHandler")
    private Endpoint foo;


    protected String getConfigLocation() {
        return "classpath:/RabbitMQSpringExceptionHandlerIntTest-context.xml";
    }

    @Test
    public void checkExceptionHandlerWasInjected() {
        RabbitMQEndpoint endp = (RabbitMQEndpoint) foo;
        assertEquals(endp.getConnectionFactoryExceptionHandler().getClass(), CustomRabbitExceptionHandler.class);
    }

    @Test
    public void checkComponentSetsExceptionHandler() throws Exception {
        Component comp = context().getComponent("rabbitmq");
        RabbitMQEndpoint endp = (RabbitMQEndpoint) comp.createEndpoint("rabbitmq:localhost:5672/foo?username=cameltest&password=cameltest&connectionFactoryExceptionHandler=#customExceptionHandler");
        assertEquals(endp.getConnectionFactoryExceptionHandler().getClass(), CustomRabbitExceptionHandler.class);
    }

    @Test
    public void checkComponentIgnoresExceptionHandler() throws Exception {
        Component comp = context().getComponent("rabbitmq");
        RabbitMQEndpoint endp = (RabbitMQEndpoint) comp.createEndpoint("rabbitmq:localhost:5672/foo?username=cameltest&password=cameltest&connectionFactoryExceptionHandler=#customExceptionHandler&connectionFactory=#customConnectionFactory");
        assertNotEquals(endp.getConnectionFactory().getExceptionHandler().getClass(), CustomRabbitExceptionHandler.class);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(getConfigLocation());
    }
}
