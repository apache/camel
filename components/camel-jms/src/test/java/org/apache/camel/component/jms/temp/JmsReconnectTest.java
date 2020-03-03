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
package org.apache.camel.component.jms.temp;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.camel.Produce;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spring.CamelBeanPostProcessor;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test for issue CAMEL-3193. Camel should support reconnects in case of
 * connection loss to jms server. After reconnect Temporary destinations have to
 * be recreated as they may become invalid
 */
public class JmsReconnectTest {

    public interface MyService {
        String echo(String st);
    }

    private static final class EchoServiceImpl implements MyService {

        @Override
        public String echo(String st) {
            return st;
        }

    }

    @Produce("direct:test")
    MyService proxy;

    /**
     * This test is disabled as the problem can currently not be reproduced using ActiveMQ.
     * TODO Find a way to recreate the problem with ActiveMQ and fully automate the test
     * @throws Exception
     */
    @Ignore
    @Test
    public void testRequestReply() throws Exception {
        BrokerService broker = new BrokerService();
        broker.addConnector("tcp://localhost:61616");
        broker.setPersistent(false);
        broker.setTimeBeforePurgeTempDestinations(1000);
        broker.start();

        DefaultCamelContext context = new DefaultCamelContext();
        JmsComponent jmsComponent = new JmsComponent();

        /**
         * 
         */
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL("failover://(tcp://localhost:61616)?maxReconnectAttempts=1");

        /**
         * When using Tibco EMS the problem can be recreated. As the broker is
         * external it has to be stopped and started by hand.
         */
        // TibjmsConnectionFactory connectionFactory = new TibjmsConnectionFactory();
        // connectionFactory.setReconnAttemptCount(1);

        jmsComponent.getConfiguration().setConnectionFactory(connectionFactory);
        jmsComponent.getConfiguration().setRequestTimeout(1000);
        jmsComponent.getConfiguration().setReceiveTimeout(1000);
        context.addComponent("jms", jmsComponent);
        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("jms:testqueue").bean(new EchoServiceImpl());
                from("direct:test").to("jms:testqueue");
            }
        });
        CamelBeanPostProcessor processor = new CamelBeanPostProcessor();
        processor.setCamelContext(context);
        processor.postProcessBeforeInitialization(this, "this");
        context.start();

        String ret = proxy.echo("test");
        Assert.assertEquals("test", ret);

        broker.stop();
        /**
         * Wait long enough for the jms client to do a full reconnect. In the
         * Tibco EMS case this means that a Temporary Destination created before
         * is invalid now
         */
        Thread.sleep(5000);

        System.in.read();
        broker.start(true);

        /**
         * Before the fix to this issue this call will throw a spring UncategorizedJmsException
         * which contains an InvalidJmsDestination
         */
        String ret2 = proxy.echo("test");
        Assert.assertEquals("test", ret2);

    }
}
