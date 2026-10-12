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
package org.apache.camel.component.pgevent;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.impossibl.postgres.api.jdbc.PGConnection;
import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The consumer must connect again and listen to the channel again every time the connection to the database is lost,
 * not only the first time. It gives back the connections it no longer uses, and a stop releases the thread that waits
 * for a reconnect.
 */
class PgEventConsumerReconnectTest extends CamelTestSupport {

    // the connections the data source has handed out
    private final List<PGConnection> connections = new CopyOnWriteArrayList<>();

    @BindToRegistry("pgDataSource")
    private final DataSource dataSource = mock(DataSource.class);

    private PgEventConsumer createConsumer() throws Exception {
        PgEventEndpoint endpoint = context.getEndpoint(
                "pgevent:///testdb/testchannel?datasource=#pgDataSource&reconnectDelay=100", PgEventEndpoint.class);
        return (PgEventConsumer) endpoint.createConsumer(exchange -> {
        });
    }

    private PGConnection newConnection() throws SQLException {
        PGConnection connection = mock(PGConnection.class);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        connections.add(connection);
        return connection;
    }

    @Test
    void reconnectsAfterEveryConnectionLoss() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocation -> newConnection());

        PgEventConsumer consumer = createConsumer();
        consumer.start();
        try {
            assertThat(connections).hasSize(1);

            // the driver reports a lost connection; the call returns when the consumer has connected again
            consumer.getPgEventListener().closed();
            assertThat(connections).as("connections after the first connection loss").hasSize(2);

            consumer.getPgEventListener().closed();
            assertThat(connections).as("connections after the second connection loss").hasSize(3);

            // and it listens to the channel on the new connection
            PGConnection last = connections.get(2);
            verify(last).prepareStatement("LISTEN testchannel");
            verify(last).addNotificationListener("testchannel", "testchannel", consumer.getPgEventListener());
        } finally {
            consumer.stop();
        }
    }

    @Test
    void closesTheLostConnectionWhenItReconnects() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocation -> newConnection());

        PgEventConsumer consumer = createConsumer();
        consumer.start();
        try {
            consumer.getPgEventListener().closed();
            assertThat(connections).hasSize(2);

            // the lost connection is given back (to the pool, with a pooled data source), the new one is in use
            verify(connections.get(0)).close();
            verify(connections.get(1), never()).close();
        } finally {
            consumer.stop();
        }
        verify(connections.get(1)).close();
    }

    @Test
    void closesAConnectionWhoseListenFails() throws Exception {
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            PGConnection connection = newConnection();
            if (connections.size() == 2) {
                // the first attempt to reconnect gets a connection, and LISTEN fails on it
                when(connection.prepareStatement(anyString())).thenThrow(new SQLException("LISTEN failed"));
            }
            return connection;
        });

        PgEventConsumer consumer = createConsumer();
        consumer.start();
        try {
            // returns when the second attempt has connected
            consumer.getPgEventListener().closed();
            assertThat(connections).hasSize(3);

            PGConnection failed = connections.get(1);
            verify(failed).close();
            verify(failed, never()).addNotificationListener("testchannel", "testchannel", consumer.getPgEventListener());
            verify(connections.get(2), never()).close();
        } finally {
            consumer.stop();
        }
    }

    @Test
    void stopReleasesTheThreadThatWaitsForAReconnect() throws Exception {
        CountDownLatch attempted = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            if (connections.isEmpty()) {
                return newConnection();
            }
            // the database stays away, so the reconnect stays pending
            attempted.countDown();
            throw new SQLException("Connection refused");
        });

        PgEventConsumer consumer = createConsumer();
        consumer.start();
        // as the thread of the driver that reports the lost connection: it waits until the consumer has reconnected
        Thread driver = new Thread(() -> consumer.getPgEventListener().closed(), "driver-io");
        driver.setDaemon(true);
        try {
            driver.start();
            assertThat(attempted.await(20, TimeUnit.SECONDS)).as("a reconnect was attempted").isTrue();
            assertThat(driver.isAlive()).as("the reconnect is pending").isTrue();
        } finally {
            consumer.stop();
        }

        driver.join(TimeUnit.SECONDS.toMillis(20));
        assertThat(driver.isAlive()).as("the thread that reported the lost connection still waits").isFalse();
    }

    @Test
    void connectionLostWhileStoppingDoesNotStartAReconnect() throws Exception {
        PgEventConsumer[] consumer = new PgEventConsumer[1];
        // as the thread of the driver that reports the lost connection
        Thread driver = new Thread(() -> consumer[0].getPgEventListener().closed(), "driver-io");
        driver.setDaemon(true);
        CountDownLatch reported = new CountDownLatch(1);
        PreparedStatement unlisten = mock(PreparedStatement.class);
        when(unlisten.execute()).thenAnswer(invocation -> {
            // the connection is lost while the consumer stops: the stop goes on when the driver thread has returned,
            // or waits for a reconnect
            driver.start();
            await().atMost(20, TimeUnit.SECONDS).until(() -> driver.getState() == Thread.State.TERMINATED
                    || driver.getState() == Thread.State.WAITING || driver.getState() == Thread.State.TIMED_WAITING);
            reported.countDown();
            throw new SQLException("Connection lost");
        });
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            PGConnection connection = newConnection();
            when(connection.prepareStatement("UNLISTEN testchannel")).thenReturn(unlisten);
            return connection;
        });

        consumer[0] = createConsumer();
        consumer[0].start();
        try {
            consumer[0].stop();
            assertThat(reported.getCount()).as("the lost connection was reported during the stop").isZero();

            // the pool that would run a reconnect is shut down, so a thread that waits for one waits forever
            driver.join(TimeUnit.SECONDS.toMillis(20));
            assertThat(driver.isAlive()).as("the thread that reported the lost connection still waits").isFalse();
            assertThat(connections).as("no reconnect was attempted").hasSize(1);
            verify(connections.get(0)).close();
        } finally {
            // never leave the thread behind
            driver.interrupt();
        }
    }
}
