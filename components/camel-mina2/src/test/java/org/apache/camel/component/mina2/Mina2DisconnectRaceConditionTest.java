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
package org.apache.camel.component.mina2;

import java.lang.reflect.Field;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.mina.core.session.IoSession;
import org.junit.Test;

public class Mina2DisconnectRaceConditionTest extends BaseMina2Test {

    /**
     * This is a test for issue CAMEL-10024 - the closing must complete before we return from the producer
     *
     * @throws Exception
     */
    @Test
    public void testCloseSessionWhenCompleteManyTimes() throws Exception {
        final String endpointUri = String.format("mina2:tcp://localhost:%1$s?sync=true&textline=true&disconnect=true&minaLogger=true", getPort());
        Mina2Producer producer = (Mina2Producer) context.getEndpoint(endpointUri).createProducer();
        // Access session to check that the session is really closed
        Field field = producer.getClass().getDeclaredField("session");
        field.setAccessible(true);

        for (int i = 0; i < 100; i++) {
            Exchange e = new DefaultExchange(context, ExchangePattern.InOut);
            e.getIn().setBody("Chad");
            producer.process(e);
            final IoSession ioSession = (IoSession) field.get(producer);
            assertTrue(ioSession.getCloseFuture().isDone());
            Object out = e.getOut().getBody();
            assertEquals("Bye Chad", out);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {
                from(String.format("mina2:tcp://localhost:%1$s?sync=true&textline=true", getPort())).process(new Processor() {

                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + body);
                    }
                });
            }
        };
    }
}