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
package org.apache.camel.component.kudu;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kudu.ColumnSchema;
import org.apache.kudu.Schema;
import org.apache.kudu.Type;
import org.apache.kudu.client.Delete;
import org.apache.kudu.client.Insert;
import org.apache.kudu.client.KuduClient;
import org.apache.kudu.client.KuduSession;
import org.apache.kudu.client.KuduTable;
import org.apache.kudu.client.Operation;
import org.apache.kudu.client.OperationResponse;
import org.apache.kudu.client.RowError;
import org.apache.kudu.client.Status;
import org.apache.kudu.client.Update;
import org.apache.kudu.client.Upsert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs the producer against a mocked Kudu client, so it does not need the Kudu harness of the other tests of this
 * module. The session answers as a session with the default flush mode does: a row that the tablet server rejected, and
 * a request that failed (a timeout for example, after which it is not known whether the row was written), are the row
 * error of the returned response, not an exception.
 */
class KuduProducerRowErrorTest extends CamelTestSupport {

    private final KuduClient client = mock(KuduClient.class);
    private final KuduSession session = mock(KuduSession.class);
    private final KuduTable table = mock(KuduTable.class);
    private final OperationResponse response = mock(OperationResponse.class);
    private final RowError rowError = mock(RowError.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        Schema schema = new Schema(
                Arrays.asList(
                        new ColumnSchema.ColumnSchemaBuilder("id", Type.INT32).key(true).build(),
                        new ColumnSchema.ColumnSchemaBuilder("name", Type.STRING).build()));

        when(client.openTable("TestTable")).thenReturn(table);
        when(client.newSession()).thenReturn(session);
        when(table.getSchema()).thenReturn(schema);
        when(table.newInsert()).thenAnswer(invocation -> operation(Insert.class, schema));
        when(table.newDelete()).thenAnswer(invocation -> operation(Delete.class, schema));
        when(table.newUpdate()).thenAnswer(invocation -> operation(Update.class, schema));
        when(table.newUpsert()).thenAnswer(invocation -> operation(Upsert.class, schema));
        when(session.apply(any(Operation.class))).thenReturn(response);
        when(response.getRowError()).thenReturn(rowError);
        when(rowError.toString()).thenReturn("Row error for row=(int32 id=5), status=Already present: key already present");

        CamelContext context = super.createCamelContext();
        KuduComponent kudu = new KuduComponent();
        kudu.setKuduClient(client);
        context.addComponent("kudu", kudu);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String operation : new String[] { "insert", "delete", "update", "upsert" }) {
                    from("direct:" + operation)
                            .to("kudu:localhost:7051/TestTable?operation=" + operation)
                            .to("mock:result");
                }
            }
        };
    }

    @ParameterizedTest
    @ValueSource(strings = { "insert", "delete", "update", "upsert" })
    void rowErrorFailsTheExchange(String operation) throws Exception {
        when(response.hasRowError()).thenReturn(true);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange exchange = template.send("direct:" + operation, e -> e.getIn().setBody(row()));

        KuduRowErrorException failure = assertInstanceOf(KuduRowErrorException.class, exchange.getException(),
                "exception of an exchange whose " + operation + " was answered with a row error");
        assertSame(rowError, failure.getRowError());
        assertEquals("Kudu reported a row error for the operation: Row error for row=(int32 id=5),"
                     + " status=Already present: key already present",
                failure.getMessage());
        result.assertIsSatisfied();
        verify(session, times(1)).close();
    }

    @Test
    void failedRequestFailsTheExchangeWithItsStatus() throws Exception {
        // what the Kudu client returns for a request that timed out: a response whose row error has the status of
        // the failure
        Status timedOut = Status.TimedOut("can not complete before timeout");
        when(response.hasRowError()).thenReturn(true);
        when(rowError.getErrorStatus()).thenReturn(timedOut);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange exchange = template.send("direct:delete", e -> e.getIn().setBody(row()));

        KuduRowErrorException failure = assertInstanceOf(KuduRowErrorException.class, exchange.getException(),
                "exception of an exchange whose delete was answered with the row error of a request that timed out");
        assertSame(timedOut, failure.getRowError().getErrorStatus());
        assertTrue(failure.getRowError().getErrorStatus().isTimedOut());
        result.assertIsSatisfied();
        verify(session, times(1)).close();
    }

    @ParameterizedTest
    @ValueSource(strings = { "insert", "delete", "update", "upsert" })
    void sessionIsClosedAfterAnAppliedRow(String operation) throws Exception {
        when(response.hasRowError()).thenReturn(false);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:" + operation, e -> e.getIn().setBody(row()));

        assertNull(exchange.getException());
        result.assertIsSatisfied();
        verify(session, times(1)).apply(any(Operation.class));
        verify(session, times(1)).close();
    }

    @Test
    void sessionIsClosedWhenTheOperationFails() throws Exception {
        IllegalStateException failure = new IllegalStateException("apply failed");
        IllegalStateException closeFailure = new IllegalStateException("close failed too");
        when(session.apply(any(Operation.class))).thenThrow(failure);
        when(session.close()).thenThrow(closeFailure);
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Exchange exchange = template.send("direct:insert", e -> e.getIn().setBody(row()));

        // the failure of the operation is reported, not the failure of the close that follows it
        assertSame(failure, exchange.getException());
        assertArrayEquals(new Throwable[] { closeFailure }, failure.getSuppressed());
        result.assertIsSatisfied();
        verify(session, times(1)).close();
        verify(response, never()).hasRowError();
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 5);
        row.put("name", "Samuel");
        return row;
    }

    private static <T extends Operation> T operation(Class<T> type, Schema schema) {
        T operation = mock(type);
        when(operation.getRow()).thenReturn(schema.newPartialRow());
        return operation;
    }
}
