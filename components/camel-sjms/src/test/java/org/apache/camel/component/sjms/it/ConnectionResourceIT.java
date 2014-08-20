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
package org.apache.camel.component.sjms.it;

import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Session;

import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsComponent;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.StopWatch;
import org.junit.Test;

/**
 * Integration test that verifies we can replace the internal
 * ConnectionFactoryResource with another provider.
 * 
 */
public class ConnectionResourceIT extends JmsTestSupport {

    /**
     * Test method for
     * {@link org.apache.commons.pool.ObjectPool#returnObject(java.lang.Object)}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testCreateConnections() throws Exception {
        ConnectionResource pool = new AMQConnectionResource("tcp://localhost:33333", 1);
        assertNotNull(pool);
        Connection connection = pool.borrowConnection();
        assertNotNull(connection);
        assertNotNull(connection.createSession(false, Session.AUTO_ACKNOWLEDGE));
        pool.returnConnection(connection);
        Connection connection2 = pool.borrowConnection();
        assertNotNull(connection2);
    }

    @Test
    public void testConnectionResourceRouter() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(100);
        mock.expectsNoDuplicates(body());

        StopWatch watch = new StopWatch();

        for (int i = 0; i < 100; i++) {
            template.sendBody("seda:start", "" + i);
        }

        // just in case we run on slow boxes
        assertMockEndpointsSatisfied(20, TimeUnit.SECONDS);

        log.info("Took " + watch.stop() + " ms. to process 100 messages request/reply over JMS");
    }

    /*
     * @see org.apache.camel.test.junit4.CamelTestSupport#createCamelContext()
     * @return
     * @throws Exception
     */
    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        AMQConnectionResource pool = new AMQConnectionResource("tcp://localhost:33333", 1);
        SjmsComponent component = new SjmsComponent();
        component.setConnectionResource(pool);
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("seda:start")
                    .to("sjms:queue:in.foo?namedReplyTo=out.bar&exchangePattern=InOut&producerCount=5")
                    .to("mock:result");

                from("sjms:queue:in.foo?exchangePattern=InOut&consumerCount=20")
                    .log("Using ${threadName} to process ${body}")
                    .transform(body().prepend("Bye "));
            }
        };
    }

    public class AMQConnectionResource implements ConnectionResource {
        private PooledConnectionFactory pcf;

        public AMQConnectionResource(String connectString, int maxConnections) {
            pcf = new PooledConnectionFactory(connectString);
            pcf.setMaxConnections(maxConnections);
            pcf.start();
        }

        public void stop() {
            pcf.stop();
        }

        @Override
        public Connection borrowConnection() throws Exception {
            Connection answer = pcf.createConnection();
            answer.start();
            return answer;
        }

        @Override
        public void returnConnection(Connection connection) throws Exception {
            // Do nothing in this case since the PooledConnectionFactory takes
            // care of this for us
            log.info("Connection returned");
        }
    }
}
