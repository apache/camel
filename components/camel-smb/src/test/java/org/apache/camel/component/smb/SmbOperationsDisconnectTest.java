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
package org.apache.camel.component.smb;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.connection.LeaseManager;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Disconnecting from a server that does not answer no longer holds the poll and the stop of the route, and the
 * directories smbj holds a lease on are closed while the share is open.
 */
class SmbOperationsDisconnectTest extends CamelTestSupport {

    private final SMBClient client = mock(SMBClient.class);
    private final Connection connection = mock(Connection.class);
    private final LeaseManager leaseManager = mock(LeaseManager.class);
    private final Session session = mock(Session.class);
    private final DiskShare share = mock(DiskShare.class);
    private SmbOperations operations;

    @BeforeEach
    void connect() throws Exception {
        when(client.connect(anyString(), anyInt())).thenReturn(connection);
        when(connection.isConnected()).thenReturn(true);
        when(connection.authenticate(any(AuthenticationContext.class))).thenReturn(session);
        when(connection.getLeaseManager()).thenReturn(leaseManager);
        when(session.connectShare("share")).thenReturn(share);
        when(share.isConnected()).thenReturn(true);

        SmbEndpoint endpoint = context.getEndpoint("smb:localhost/share?username=scott&password=tiger", SmbEndpoint.class);
        operations = new SmbOperations(endpoint.getConfiguration(), client);
        operations.setEndpoint(endpoint);
        operations.connect(endpoint.getConfiguration(), null);
        assertTrue(operations.isConnected());
    }

    @Test
    void closesLeasedDirectoriesBeforeTheShare() throws Exception {
        operations.disconnect();

        InOrder order = inOrder(leaseManager, share, session, connection);
        order.verify(leaseManager).close();
        order.verify(share).close();
        order.verify(session).close();
        order.verify(connection).close();
    }

    @Test
    void serverThatDoesNotAnswer() throws Exception {
        // the close of the share waits for an answer, as smbj does up to its transact timeout
        CountDownLatch answer = new CountDownLatch(1);
        doAnswer(invocation -> answer.await(1, TimeUnit.MINUTES)).when(share).close();

        StopWatch watch = new StopWatch();
        operations.disconnect();
        long taken = watch.taken();

        assertTrue(taken < SmbOperations.DISCONNECT_WAIT_MILLIS + 5000, "disconnect took " + taken + " ms");
        assertFalse(operations.isConnected());

        // the disconnect completes in the background once the server answers
        answer.countDown();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> verify(connection).close());
        verify(session).close();
    }
}
