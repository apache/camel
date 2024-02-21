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
package org.apache.camel.component.grpc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GrpcConsumerExceptionTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumerExceptionTest.class);

    private static final int GRPC_SYNC_REQUEST_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_TEST_PING_ID = 1;
    private static final String GRPC_TEST_PING_VALUE = "PING";

    private ManagedChannel syncRequestChannel;
    private PingPongGrpc.PingPongBlockingStub blockingStub;
    private PingPongGrpc.PingPongStub nonBlockingStub;

    @BeforeEach
    public void startGrpcChannels() {
        syncRequestChannel = ManagedChannelBuilder.forAddress("localhost", GRPC_SYNC_REQUEST_TEST_PORT).usePlaintext().build();
        blockingStub = PingPongGrpc.newBlockingStub(syncRequestChannel);
        nonBlockingStub = PingPongGrpc.newStub(syncRequestChannel);
    }

    @AfterEach
    public void stopGrpcChannels() {
        if (syncRequestChannel != null) {
            syncRequestChannel.shutdown().shutdownNow();
        }
    }

    @Test
    public void testExceptionExpected() {
        LOG.info("gRPC expected exception test start");
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        assertThrows(StatusRuntimeException.class, () -> blockingStub.pingSyncSync(pingRequest));
    }

    @Test
    public void testExchangeExceptionHandling() {
        LOG.info("gRPC exchange exception handling test start");
        assertDoesNotThrow(this::runExchangeExceptionHandlingTest);
    }

    private void runExchangeExceptionHandlingTest() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        PingRequest pingRequest
                = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(GRPC_TEST_PING_ID).build();
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

        nonBlockingStub.pingSyncSync(pingRequest, responseObserver);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:" + GRPC_SYNC_REQUEST_TEST_PORT
                     + "/org.apache.camel.component.grpc.PingPong?synchronous=true")
                        .throwException(CamelException.class, "GRPC Camel exception message");

            }
        };
    }

    static class PongResponseStreamObserver implements StreamObserver<PongResponse> {
        private PongResponse pongResponse;
        private final CountDownLatch latch;

        public PongResponseStreamObserver(CountDownLatch latch) {
            this.latch = latch;
        }

        public PongResponse getPongResponse() {
            return pongResponse;
        }

        @Override
        public void onNext(PongResponse value) {
            pongResponse = value;
        }

        @Override
        public void onError(Throwable t) {
            assertEquals("INTERNAL: GRPC Camel exception message", t.getMessage());
            LOG.info("Exception", t);
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }
    }
}
