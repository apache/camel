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
package org.apache.camel.spring.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactionalClientDataSourceRedeliveryTest extends TransactionalClientDataSourceTest {

    @Test
    public void testTransactionRollbackWithExchange() throws Exception {
        Exchange out = template.send("direct:fail", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("Hello World");
            }
        });

        int count = jdbc.queryForObject("select count(*) from books", Integer.class);
        assertEquals(1, count, "Number of books");

        assertNotNull(out);

        Exception e = out.getException();
        assertIsInstanceOf(RuntimeCamelException.class, e);
        assertTrue(e.getCause() instanceof IllegalArgumentException);
        assertEquals("We don't have Donkeys, only Camels", e.getCause().getMessage());

        assertEquals(true, out.getIn().getHeader(Exchange.REDELIVERED));
        assertEquals(4, out.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
        assertEquals(true, out.getExchangeExtension().isFailureHandled());
        assertEquals(false, out.getExchangeExtension().isErrorHandlerHandled());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                // configure transacted error handler to use up till 4 redeliveries
                // with 100 millis delay between each redelivery attempt
                // we have not passed in any spring TX manager. Camel will automatic
                // find it in the spring application context. You only need to help
                // Camel in case you have multiple TX managers
                errorHandler(transactionErrorHandler().maximumRedeliveries(4).redeliveryDelay(100));

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
