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
package org.apache.camel.spring.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

/**
 * @version 
 */
public class TransactionalClientDataSourceOnExceptionRedeliveryTest extends TransactionalClientDataSourceTest {

    @Test
    public void testTransactionRollbackWithExchange() throws Exception {
        Exchange out = template.send("direct:fail", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals("Number of books", 1, count);

        assertNotNull(out);

        Exception e = out.getException();
        assertIsInstanceOf(RuntimeCamelException.class, e);
        assertTrue(e.getCause()instanceof IllegalArgumentException);
        assertEquals("We don't have Donkeys, only Camels", e.getCause().getMessage());

        assertEquals(true, out.getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(3, out.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
        assertEquals(true, out.getProperty(Exchange.FAILURE_HANDLED));
        assertEquals(false, out.getProperty(Exchange.ERRORHANDLER_HANDLED));
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                onException(IllegalArgumentException.class).maximumRedeliveries(3);

                // START SNIPPET: e1
                from("direct:okay")
                    // marks this route as transacted, and we dont pass in any parameters so we
                    // will auto lookup and use the Policy defined in the spring XML file
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Elephant in Action")).bean("bookService");

                // marks this route as transacted that will use the single policy defined in the registry
                from("direct:fail")
                    // marks this route as transacted, and we dont pass in any parameters so we
                    // will auto lookup and use the Policy defined in the spring XML file
                    .transacted()
                    .setBody(constant("Tiger in Action")).bean("bookService")
                    .setBody(constant("Donkey in Action")).bean("bookService");
                // END SNIPPET: e1
            }
        };
    }

}