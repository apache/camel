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
package org.apache.camel.component.thrift;

import java.util.List;
import java.util.Map;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TConfiguration;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The reply of the consumer route is converted to the return type of the method: a reply that is null, or that does not
 * convert, must give the client an error from the synchronous and the asynchronous server, not a null or 0 result.
 */
public class ThriftConsumerReplyTest extends CamelTestSupport {

    private static final int CLIENT_TIMEOUT = 5000;

    // the first argument of the call selects the reply of the route
    private static final int REPLY_NULL = 1;
    private static final int REPLY_MAP = 2;
    private static final int REPLY_TEXT = 3;
    private static final int REPLY_SAME = 4;

    @ParameterizedTest
    @ValueSource(strings = { "sync", "async" })
    public void testNullReplyOfIntMethodIsAnError(String routeId) throws Exception {
        TTransport transport = open(routeId);
        try {
            Calculator.Client client = client(transport);

            TApplicationException e = assertThrows(TApplicationException.class, () -> client.add(REPLY_NULL, 0));
            assertEquals(TApplicationException.MISSING_RESULT, e.getType());
            // the connection is still usable
            assertEquals(25, client.add(REPLY_TEXT, 0));
        } finally {
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "sync", "async" })
    public void testReplyOfIntMethodThatDoesNotConvertIsAnError(String routeId) throws Exception {
        TTransport transport = open(routeId);
        try {
            Calculator.Client client = client(transport);

            TApplicationException e = assertThrows(TApplicationException.class, () -> client.add(REPLY_MAP, 0));
            assertEquals(TApplicationException.INTERNAL_ERROR, e.getType());
            assertEquals(25, client.add(REPLY_TEXT, 0));
        } finally {
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "sync", "async" })
    public void testReplyOfStructMethodThatIsNullOrDoesNotConvertIsAnError(String routeId) throws Exception {
        TTransport transport = open(routeId);
        try {
            Calculator.Client client = client(transport);

            TApplicationException e
                    = assertThrows(TApplicationException.class, () -> client.echo(work(REPLY_NULL)));
            assertEquals(TApplicationException.MISSING_RESULT, e.getType());
            e = assertThrows(TApplicationException.class, () -> client.echo(work(REPLY_TEXT)));
            assertEquals(TApplicationException.INTERNAL_ERROR, e.getType());
            e = assertThrows(TApplicationException.class, () -> client.echo(work(REPLY_MAP)));
            assertEquals(TApplicationException.INTERNAL_ERROR, e.getType());
            assertEquals(work(REPLY_SAME), client.echo(work(REPLY_SAME)));
        } finally {
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "sync", "async" })
    public void testReplyOfVoidMethodIsIgnored(String routeId) throws Exception {
        TTransport transport = open(routeId);
        try {
            Calculator.Client client = client(transport);

            // the route replies with a Map, which a void method ignores
            client.ping();
            assertEquals(25, client.add(REPLY_TEXT, 0));
        } finally {
            transport.close();
        }
    }

    private static Work work(int num1) {
        return new Work(num1, 13, Operation.MULTIPLY);
    }

    private static Calculator.Client client(TTransport transport) throws Exception {
        return new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));
    }

    private TTransport open(String routeId) throws Exception {
        // a timeout so that a call the server never answers fails the test
        TTransport transport = new TSocket(new TConfiguration(), "localhost", getPort(routeId), CLIENT_TIMEOUT);
        transport.open();
        return transport;
    }

    private int getPort(String routeId) {
        return ((ThriftConsumer) context.getRoute(routeId).getConsumer()).getLocalPort();
    }

    private static void reply(Message message) {
        List<?> args = message.getBody(List.class);
        Object first = args == null || args.isEmpty() ? null : args.get(0);
        int selector = first instanceof Work ? ((Work) first).num1 : first instanceof Integer ? (Integer) first : REPLY_MAP;
        switch (selector) {
            case REPLY_NULL:
                message.setBody(null);
                break;
            case REPLY_TEXT:
                message.setBody("25");
                break;
            case REPLY_SAME:
                message.setBody(first);
                break;
            default:
                message.setBody(Map.of("result", 25));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("thrift://localhost:0/org.apache.camel.component.thrift.generated.Calculator?synchronous=true")
                        .routeId("sync")
                        .to("direct:service");

                from("thrift://localhost:0/org.apache.camel.component.thrift.generated.Calculator")
                        .routeId("async")
                        .to("direct:service");

                from("direct:service")
                        .process(exchange -> reply(exchange.getMessage()));
            }
        };
    }
}
