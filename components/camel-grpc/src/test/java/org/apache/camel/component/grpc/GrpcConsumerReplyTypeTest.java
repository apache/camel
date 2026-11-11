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

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.TypeConverterSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reply of the consumer must be of the response type of the called method: a body of another type is converted to
 * it, or the call ends with an error. It must never be serialized as is, as the client would decode the bytes of, for
 * example, the unchanged request as a response.
 */
public class GrpcConsumerReplyTypeTest extends GrpcTestSupport {

    private static final String PING_NAME = "PING";
    private static final int PING_ID = 7;

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    @AfterEach
    public void stopGrpcChannels() {
        channels.values().forEach(channel -> channel.shutdown().shutdownNow());
        channels.clear();
    }

    @Test
    public void unchangedRequestIsNotSentAsResponse() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> blockingStub("grpc-unchanged").pingSyncSync(pingRequest(PING_ID)));

        assertReplyTypeError(e.getStatus(), PingRequest.class);
    }

    @Test
    public void textReplyEndsTheCallWithAnError() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> blockingStub("grpc-text").pingSyncSync(pingRequest(PING_ID)));

        assertReplyTypeError(e.getStatus(), String.class);
    }

    @Test
    public void replyTypeErrorIsMutedByDefault() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> blockingStub("grpc-unchanged-muted").pingSyncSync(pingRequest(PING_ID)));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals("Exchange processing failed", e.getStatus().getDescription());
    }

    @Test
    public void replyIsConvertedToTheResponseType() {
        PongResponse pongResponse = blockingStub("grpc-convert").pingSyncSync(pingRequest(PING_ID));

        assertEquals(PING_NAME + "PONG", pongResponse.getPongName());
        assertEquals(PING_ID, pongResponse.getPongId());
    }

    @Test
    public void serverStreamingSendsNothingWhenOneMessageHasTheWrongType() {
        Iterator<PongResponse> responses = blockingStub("grpc-streaming-mixed").pingSyncAsync(pingRequest(PING_ID));

        // the first message is a valid PongResponse, but the whole reply is checked before any message is sent
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, responses::hasNext);
        assertReplyTypeError(e.getStatus(), PingRequest.class);
    }

    @Test
    public void serverStreamingConvertsEachMessage() {
        Iterator<PongResponse> responses = blockingStub("grpc-streaming-convert").pingSyncAsync(pingRequest(PING_ID));

        assertEquals(PING_NAME + "PONG", responses.next().getPongName());
        assertEquals(PING_NAME + "PONG2", responses.next().getPongName());
        assertFalse(responses.hasNext());
    }

    @Test
    public void aggregationUnchangedRequestsAreNotSentAsResponse() throws Exception {
        PongResponseStreamObserver responseObserver = callStreaming("grpc-aggregation-unchanged", false);

        assertTrue(responseObserver.pongResponses.isEmpty(), "the requests must not be sent as responses");
        StatusRuntimeException e = assertInstanceOf(StatusRuntimeException.class, responseObserver.error);
        assertReplyTypeError(e.getStatus(), PingRequest.class);
        assertEquals(0, responseObserver.completed.get());
    }

    @Test
    public void propagationUnchangedRequestIsNotSentAsResponse() throws Exception {
        PongResponseStreamObserver responseObserver = callStreaming("grpc-propagation-unchanged", true);

        assertTrue(responseObserver.pongResponses.isEmpty(), "the request must not be sent as response");
        StatusRuntimeException e = assertInstanceOf(StatusRuntimeException.class, responseObserver.error);
        assertReplyTypeError(e.getStatus(), PingRequest.class);
        assertEquals(0, responseObserver.completed.get());
    }

    @Test
    public void propagationConvertsTheReply() throws Exception {
        PongResponseStreamObserver responseObserver = callStreaming("grpc-propagation-convert", true);

        assertEquals(1, responseObserver.pongResponses.size());
        assertEquals(PING_NAME + "PONG", responseObserver.pongResponses.get(0).getPongName());
        assertEquals(1, responseObserver.completed.get());
    }

    @Test
    public void noBodySendsAnEmptyMessageAsBefore() {
        PongResponse pongResponse = blockingStub("grpc-null").pingSyncSync(pingRequest(PING_ID));

        assertEquals(PongResponse.getDefaultInstance(), pongResponse);
    }

    private static void assertReplyTypeError(Status status, Class<?> replyType) {
        assertEquals(Status.Code.INTERNAL, status.getCode());
        assertTrue(status.getDescription().contains(PongResponse.class.getName()),
                "the description must name the response type: " + status);
        assertTrue(status.getDescription().contains(replyType.getName()),
                "the description must name the type of the reply: " + status);
    }

    private static PingRequest pingRequest(int id) {
        return PingRequest.newBuilder().setPingName(PING_NAME).setPingId(id).build();
    }

    private ManagedChannel channel(String routeId) {
        return channels.computeIfAbsent(routeId,
                id -> ManagedChannelBuilder.forAddress("localhost", getRoutePort(id)).usePlaintext().build());
    }

    private PingPongGrpc.PingPongBlockingStub blockingStub(String routeId) {
        return PingPongGrpc.newBlockingStub(channel(routeId)).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private PongResponseStreamObserver callStreaming(String routeId, boolean bidi) throws InterruptedException {
        PingPongGrpc.PingPongStub stub = PingPongGrpc.newStub(channel(routeId));
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();
        StreamObserver<PingRequest> requestObserver
                = bidi ? stub.pingAsyncAsync(responseObserver) : stub.pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest(PING_ID));
        requestObserver.onCompleted();
        assertTrue(responseObserver.latch.await(5, TimeUnit.SECONDS));
        return responseObserver;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getTypeConverterRegistry().addTypeConverter(PongResponse.class, Pong.class,
                        new TypeConverterSupport() {
                            @Override
                            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                                Pong pong = (Pong) value;
                                return type.cast(PongResponse.newBuilder().setPongName(pong.name()).setPongId(pong.id())
                                        .build());
                            }
                        });

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=1&synchronous=true&muteException=false")
                        .routeId("grpc-unchanged")
                        .log("${body}");

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=2&synchronous=true")
                        .routeId("grpc-unchanged-muted")
                        .log("${body}");

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=3&synchronous=true&muteException=false")
                        .routeId("grpc-text")
                        .setBody(constant("PONG"));

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=4&synchronous=true&muteException=false")
                        .routeId("grpc-null")
                        .process(e -> e.getMessage().setBody(null));

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=5&synchronous=true")
                        .routeId("grpc-convert")
                        .process(e -> e.getMessage().setBody(toPong(e.getMessage().getBody(PingRequest.class), "PONG")));

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=6&synchronous=true&muteException=false")
                        .routeId("grpc-streaming-mixed")
                        .process(e -> {
                            PingRequest request = e.getMessage().getBody(PingRequest.class);
                            PongResponse pong = PongResponse.newBuilder().setPongName("PONG").build();
                            e.getMessage().setBody(List.of(pong, request));
                        });

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=7&synchronous=true")
                        .routeId("grpc-streaming-convert")
                        .process(e -> {
                            PingRequest request = e.getMessage().getBody(PingRequest.class);
                            e.getMessage().setBody(List.of(toPong(request, "PONG"), toPong(request, "PONG2")));
                        });

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=8&synchronous=true"
                     + "&consumerStrategy=AGGREGATION&muteException=false")
                        .routeId("grpc-aggregation-unchanged")
                        .log("${body}");

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=9&synchronous=true"
                     + "&consumerStrategy=PROPAGATION&muteException=false")
                        .routeId("grpc-propagation-unchanged")
                        .log("${body}");

                from("grpc://localhost:0/org.apache.camel.component.grpc.PingPong?hash=10&synchronous=true"
                     + "&consumerStrategy=PROPAGATION")
                        .routeId("grpc-propagation-convert")
                        .process(e -> e.getMessage().setBody(toPong(e.getMessage().getBody(PingRequest.class), "PONG")));
            }
        };
    }

    private static Pong toPong(PingRequest request, String suffix) {
        return new Pong(request.getPingName() + suffix, request.getPingId());
    }

    /**
     * A reply of the route that is not a protobuf message, with a type converter to PongResponse.
     */
    public record Pong(String name, int id) {
    }

    static class PongResponseStreamObserver implements StreamObserver<PongResponse> {
        final List<PongResponse> pongResponses = new CopyOnWriteArrayList<>();
        final AtomicInteger completed = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        volatile Throwable error;

        @Override
        public void onNext(PongResponse value) {
            pongResponses.add(value);
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            completed.incrementAndGet();
            latch.countDown();
        }
    }
}
