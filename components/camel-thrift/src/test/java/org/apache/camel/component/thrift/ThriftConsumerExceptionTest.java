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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.thrift.generated.Calculator;
import org.apache.camel.component.thrift.generated.InvalidOperation;
import org.apache.camel.component.thrift.generated.Operation;
import org.apache.camel.component.thrift.generated.Work;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A failed exchange must be sent to the Thrift client as an error, once, by the synchronous and the asynchronous
 * server.
 */
public class ThriftConsumerExceptionTest extends CamelTestSupport {

    private static final Work WORK = new Work(12, 13, Operation.MULTIPLY);

    @Test
    public void testSyncDeclaredExceptionIsSent() throws Exception {
        TTransport transport = open("sync");
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));

            InvalidOperation e = assertThrows(InvalidOperation.class, () -> client.calculate(1, WORK));
            assertEquals("Forced", e.getWhy());
        } finally {
            transport.close();
        }
    }

    @Test
    public void testSyncFailedVoidMethodIsAnError() throws Exception {
        TTransport transport = open("sync");
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));

            assertThrows(TApplicationException.class, client::ping);
            // the connection is still usable
            assertEquals(25, client.add(12, 13));
        } finally {
            transport.close();
        }
    }

    @Test
    public void testAsyncServerSendsDeclaredExceptionOnce() throws Exception {
        TTransport transport = open("async");
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));

            InvalidOperation e = assertThrows(InvalidOperation.class, () -> client.calculate(1, WORK));
            assertEquals("Forced", e.getWhy());
            // the next call on the same connection gets its own response
            assertEquals(25, client.add(12, 13));
        } finally {
            transport.close();
        }
    }

    @Test
    public void testAsyncServerSendsFailureOfVoidMethodOnce() throws Exception {
        TTransport transport = open("async");
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));

            assertThrows(TApplicationException.class, client::ping);
            // the next call on the same connection gets its own response
            assertEquals(25, client.add(12, 13));
        } finally {
            transport.close();
        }
    }

    private TTransport open(String routeId) throws Exception {
        TTransport transport = new TSocket("localhost", getPort(routeId));
        transport.open();
        return transport;
    }

    private int getPort(String routeId) {
        return ((ThriftConsumer) context.getRoute(routeId).getConsumer()).getLocalPort();
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
                        .choice()
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("add"))
                        .setBody(constant(25))
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("calculate"))
                        .throwException(new InvalidOperation(1, "Forced"))
                        .otherwise()
                        .throwException(new IllegalStateException("Forced"));
            }
        };
    }
}
