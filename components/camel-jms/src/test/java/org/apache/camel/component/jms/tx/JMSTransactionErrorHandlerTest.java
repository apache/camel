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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * To demonstrate transacted with minimal configuration.
 */
public class JMSTransactionErrorHandlerTest extends CamelSpringTestSupport {

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
            "/org/apache/camel/component/jms/tx/JMSTransactionErrorHandlerTest.xml");
    }

    @Test
    public void testTransactionSuccess() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");
        // success at 3rd attempt
        mock.message(0).header("count").isEqualTo(3);
        // and since it was Camel doing the redelivery we should have headers for this
        mock.message(0).header(Exchange.REDELIVERED).isEqualTo(true);
        mock.message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(2);
        // and not JMS doing the redelivery
        mock.message(0).header("JMSRedelivered").isEqualTo(false);

        template.sendBody("activemq:queue:okay", "Hello World");

        mock.assertIsSatisfied();
    }

    public static class MyProcessor implements Processor {
        private int count;

        public void process(Exchange exchange) throws Exception {
            if (++count <= 2) {
                throw new IllegalArgumentException("Forced Exception number " + count + ", please retry");
            }
            exchange.getIn().setBody("Bye World");
            exchange.getIn().setHeader("count", count);
        }
    }

}