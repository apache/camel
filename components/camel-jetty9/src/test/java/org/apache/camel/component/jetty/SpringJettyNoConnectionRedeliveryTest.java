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
package org.apache.camel.component.jetty;

import java.net.ConnectException;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version
 */
@Ignore("Fails with Address already in use")
public class SpringJettyNoConnectionRedeliveryTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jetty/jetty-noconnection-redelivery.xml");
    }

    @Test
    public void testConnectionOk() throws Exception {
        String reply = template.requestBody("direct:start", "World", String.class);
        assertEquals("Bye World", reply);
    }

    @Test
    public void testConnectionNotOk() throws Exception {
        // stop Jetty route so there should not be a connection
        context.stopRoute("jetty");

        Exchange exchange = template.request("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Moon");
            }
        });

        assertTrue(exchange.isFailed());

        // there should be a connect exception as cause
        ConnectException ce = exchange.getException(ConnectException.class);
        assertNotNull(ce);

        assertEquals(true, exchange.getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(4, exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
    }
}
