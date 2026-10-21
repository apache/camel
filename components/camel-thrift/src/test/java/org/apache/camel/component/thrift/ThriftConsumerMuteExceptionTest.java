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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The consumer sends a generic error for an exception that the Thrift method does not declare, unless muteException is
 * disabled.
 */
class ThriftConsumerMuteExceptionTest extends CamelTestSupport {

    private static final String DETAIL = "Detail of the failure";

    @Test
    void asyncServerMutesTheException() throws Exception {
        TApplicationException e = callAdd("async");
        assertEquals(TApplicationException.INTERNAL_ERROR, e.getType());
        assertEquals("Internal error processing add", e.getMessage());
    }

    @Test
    void asyncServerSendsTheExceptionWhenNotMuted() throws Exception {
        assertTrue(callAdd("async-not-muted").getMessage().contains(DETAIL));
    }

    @Test
    void declaredExceptionIsSentWhenMuted() throws Exception {
        TTransport transport = open("async");
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));
            InvalidOperation e = assertThrows(InvalidOperation.class,
                    () -> client.calculate(1, new Work(12, 13, Operation.DIVIDE)));
            assertEquals(DETAIL, e.getWhy());
        } finally {
            transport.close();
        }
    }

    private TApplicationException callAdd(String routeId) throws Exception {
        TTransport transport = open(routeId);
        try {
            Calculator.Client client = new Calculator.Client(new TBinaryProtocol(new TFramedTransport(transport)));
            return assertThrows(TApplicationException.class, () -> client.add(12, 13));
        } finally {
            transport.close();
        }
    }

    private TTransport open(String routeId) throws Exception {
        TTransport transport
                = new TSocket("localhost", ((ThriftConsumer) context.getRoute(routeId).getConsumer()).getLocalPort());
        transport.open();
        return transport;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String uri = "thrift://localhost:0/org.apache.camel.component.thrift.generated.Calculator";
                from(uri).routeId("async").to("direct:service");
                from(uri + "?muteException=false").routeId("async-not-muted").to("direct:service");

                from("direct:service")
                        .choice()
                        .when(header(ThriftConstants.THRIFT_METHOD_NAME_HEADER).isEqualTo("calculate"))
                        .throwException(new InvalidOperation(4, DETAIL))
                        .otherwise()
                        // such as the error of a Thrift service the route called
                        .throwException(new TApplicationException(TApplicationException.INTERNAL_ERROR, DETAIL));
            }
        };
    }
}
