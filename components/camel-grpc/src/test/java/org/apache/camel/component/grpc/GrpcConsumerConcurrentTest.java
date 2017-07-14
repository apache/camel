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
package org.apache.camel.component.grpc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.googlecode.junittoolbox.MultithreadingTester;
import com.googlecode.junittoolbox.RunnableAssert;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrpcConsumerConcurrentTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(GrpcConsumerConcurrentTest.class);

    private static final int GRPC_ASYNC_REQUEST_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int GRPC_HEADERS_TEST_PORT = AvailablePortFinder.getNextAvailable();
    private static final int CONCURRENT_THREAD_COUNT = 30;
    private static final int ROUNDS_PER_THREAD_COUNT = 10;
    private static final String GRPC_TEST_PING_VALUE = "PING";
    private static final String GRPC_TEST_PONG_VALUE = "PONG";
    private static final String GRPC_USER_AGENT_PREFIX = "user-agent-";
    private static AtomicInteger idCounter = new AtomicInteger();

    public static Integer createId() {
        return idCounter.getAndIncrement();
    }

    public static Integer getId() {
        return idCounter.get();
    }

    @Test
    public void testAsyncWithConcurrentThreads() throws Exception {
        RunnableAssert ra = new RunnableAssert("foo") {

            @Override
            public void run() {
                final CountDownLatch latch = new CountDownLatch(1);
                ManagedChannel asyncRequestChannel = NettyChannelBuilder.forAddress("localhost", GRPC_ASYNC_REQUEST_TEST_PORT).usePlaintext(true).build();
                PingPongGrpc.PingPongStub asyncNonBlockingStub = PingPongGrpc.newStub(asyncRequestChannel);

                PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);
                int instanceId = createId();

                final PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(instanceId).build();
                StreamObserver<PingRequest> requestObserver = asyncNonBlockingStub.pingAsyncAsync(responseObserver);
                requestObserver.onNext(pingRequest);
                requestObserver.onNext(pingRequest);
                requestObserver.onCompleted();
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                PongResponse pongResponse = responseObserver.getPongResponse();

                assertNotNull("instanceId = " + instanceId, pongResponse);
                assertEquals(instanceId, pongResponse.getPongId());
                assertEquals(GRPC_TEST_PING_VALUE + GRPC_TEST_PONG_VALUE, pongResponse.getPongName());

                asyncRequestChannel.shutdown().shutdownNow();
            }
        };

        new MultithreadingTester().add(ra).numThreads(CONCURRENT_THREAD_COUNT).numRoundsPerThread(ROUNDS_PER_THREAD_COUNT).run();
    }

    @Test
    public void testHeadersWithConcurrentThreads() throws Exception {
        RunnableAssert ra = new RunnableAssert("foo") {

            @Override
            public void run() {
                int instanceId = createId();
                final CountDownLatch latch = new CountDownLatch(1);
                ManagedChannel asyncRequestChannel = NettyChannelBuilder.forAddress("localhost", GRPC_HEADERS_TEST_PORT).userAgent(GRPC_USER_AGENT_PREFIX + instanceId)
                    .usePlaintext(true).build();
                PingPongGrpc.PingPongStub asyncNonBlockingStub = PingPongGrpc.newStub(asyncRequestChannel);

                PongResponseStreamObserver responseObserver = new PongResponseStreamObserver(latch);

                final PingRequest pingRequest = PingRequest.newBuilder().setPingName(GRPC_TEST_PING_VALUE).setPingId(instanceId).build();
                StreamObserver<PingRequest> requestObserver = asyncNonBlockingStub.pingAsyncAsync(responseObserver);
                requestObserver.onNext(pingRequest);
                requestObserver.onNext(pingRequest);
                requestObserver.onCompleted();
                try {
                    latch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                PongResponse pongResponse = responseObserver.getPongResponse();

                assertNotNull("instanceId = " + instanceId, pongResponse);
                assertEquals(instanceId, pongResponse.getPongId());
                assertEquals(GRPC_USER_AGENT_PREFIX + instanceId, pongResponse.getPongName());

                asyncRequestChannel.shutdown().shutdownNow();
            }
        };

        new MultithreadingTester().add(ra).numThreads(CONCURRENT_THREAD_COUNT).numRoundsPerThread(ROUNDS_PER_THREAD_COUNT).run();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("grpc://localhost:" + GRPC_ASYNC_REQUEST_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION")
                    .bean(new GrpcMessageBuilder(), "buildAsyncPongResponse");
                
                from("grpc://localhost:" + GRPC_HEADERS_TEST_PORT + "/org.apache.camel.component.grpc.PingPong?synchronous=true&consumerStrategy=AGGREGATION")
                    .process(new HeaderExchangeProcessor());
            }
        };
    }

    public class PongResponseStreamObserver implements StreamObserver<PongResponse> {
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
            LOG.info("Exception", t);
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }
    }

    public class GrpcMessageBuilder {
        public PongResponse buildAsyncPongResponse(List<PingRequest> pingRequests) {
            return PongResponse.newBuilder().setPongName(pingRequests.get(0).getPingName() + GRPC_TEST_PONG_VALUE).setPongId(pingRequests.get(0).getPingId()).build();
        }
    }

    public class HeaderExchangeProcessor implements Processor {

        @SuppressWarnings("unchecked")
        public void process(Exchange exchange) throws Exception {
            List<PingRequest> pingRequests = (List<PingRequest>)exchange.getIn().getBody();
            String userAgentName = (String)exchange.getIn().getHeader(GrpcConstants.GRPC_USER_AGENT_HEADER);

            // As user agent name is prepended the library's user agent
            // information it's necessary to extract this value (before first
            // space)
            PongResponse pongResponse = PongResponse.newBuilder().setPongName(userAgentName.substring(0, userAgentName.indexOf(' '))).setPongId(pingRequests.get(0).getPingId())
                .build();
            exchange.getIn().setBody(pongResponse);
        }
    }
}
