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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsInOutParallelTest extends CamelTestSupport {

    @Test
    public void testInOutParallel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.setAssertPeriod(2000);
        mock.expectedMessageCount(5);
        String outPayload = template.requestBody("direct:test", "test", String.class);
        assertEquals("Fully done", outPayload);
        mock.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                
                from("direct:test")
                    .setBody(constant("1,2,3,4,5"))
                    .inOut("activemq:queue:test1?requestTimeout=2000")
                    .split().tokenize(",").parallelProcessing()
                        .inOut("activemq:queue:test2?requestTimeout=2000")
                        .to("mock:received")
                    .end()
                    .setBody(constant("Fully done"))
                    .log("Finished");
                
                from("activemq:queue:test1")
                    .log("Received on queue test1");
                
                from("activemq:queue:test2")
                    .log("Received on queue test2")
                    .setBody(constant("Some reply"))
                    .delay(constant(100));
                
            }
        };
    }

}
