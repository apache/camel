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
package org.apache.camel.component.file.remote;

import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.function.ThrowingRunnable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * While the producer uses its connection, the health check does not check the connection: it reports the state that its
 * last check found.
 */
class RemoteFileProducerHealthCheckBusyTest extends CamelTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    void busyReportsTheStateOfTheLastCheck() throws Exception {
        RemoteFileEndpoint<Object> endpoint = context.getEndpoint("ftp://localhost/out", RemoteFileEndpoint.class);
        RemoteFileOperations<Object> operations = mock(RemoteFileOperations.class);
        RemoteFileProducer<Object> producer = new RemoteFileProducer<>(endpoint, operations);
        RemoteFileProducerHealthCheck check = new RemoteFileProducerHealthCheck(producer);
        check.setCamelContext(context);

        // idle: the check runs, and the server refuses the connection
        idle(operations);
        doThrow(new GenericFileOperationFailedException("Connection refused")).when(operations).connect(any(), any());
        assertEquals(HealthCheck.State.DOWN, check.call().getState());

        // busy: no connect, the state of the last check
        doReturn(false).when(operations).runIfIdle(any());
        HealthCheck.Result busy = check.call();
        assertEquals(HealthCheck.State.DOWN, busy.getState());
        assertEquals(true, busy.getDetails().get("busy"));
        verify(operations, times(1)).connect(any(), any());

        // idle again: the check connects
        idle(operations);
        doReturn(true).when(operations).connect(any(), any());
        doReturn(true).when(operations).isConnected();
        HealthCheck.Result up = check.call();
        assertEquals(HealthCheck.State.UP, up.getState());
        assertFalse(up.getDetails().containsKey("busy"));

        doReturn(false).when(operations).runIfIdle(any());
        busy = check.call();
        assertEquals(HealthCheck.State.UP, busy.getState());
        assertEquals(true, busy.getDetails().get("busy"));
        verify(operations, times(2)).connect(any(), any());
    }

    private static void idle(RemoteFileOperations<?> operations) throws Exception {
        doAnswer(invocation -> {
            invocation.<ThrowingRunnable<Exception>> getArgument(0).run();
            return true;
        }).when(operations).runIfIdle(any());
    }
}
