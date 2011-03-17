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
package org.apache.camel.spring.processor;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version 
 */
public class SpringDefaultErrorHandlerNotHandledPolicyTest extends SpringTestSupport {

    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/processor/SpringDefaultErrorHandlerNotHandledPolicyTest.xml");
    }

    public void testNotHandled() throws Exception {
        try {
            template.sendBody("direct:start", "Hello World");
            fail("Should have thrown an exception");
        } catch (CamelExecutionException e) {
            // as its NOT handled the exception should be thrown back to the client
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", e.getCause().getMessage());
        }
    }

    public void testNotHandledSendExchange() throws Exception {
        Exchange out = template.send("direct:start", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        Exception e = out.getException();
        assertNotNull("Should have thrown an exception", e);
        assertIsInstanceOf(IllegalArgumentException.class, e);
        assertEquals("Forced", e.getMessage());

        assertEquals(true, out.getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(2, out.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
        assertEquals(true, out.getProperty(Exchange.FAILURE_HANDLED));
        assertEquals(false, out.getProperty(Exchange.ERRORHANDLER_HANDLED));
        assertSame("Should be same exception", e, out.getProperty(Exchange.EXCEPTION_CAUGHT));
    }

}