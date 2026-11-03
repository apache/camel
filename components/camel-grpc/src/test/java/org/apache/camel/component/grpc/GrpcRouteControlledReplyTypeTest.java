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
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.grpc.GrpcConsumerReplyTypeTest.Pong;
import org.apache.camel.component.grpc.GrpcConsumerReplyTypeTest.PongResponseStreamObserver;
import org.apache.camel.support.TypeConverterSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With routeControlledStreamObserver=true, a reply sent by the toRouteControlledStreamObserver producer must be of the
 * response type of the called method: a body of another type is converted to it, or the call ends with an error. It
 * must never be serialized as is, as the client would decode the bytes of, for example, the unchanged request as a
 * response.
 */
public class GrpcRouteControlledReplyTypeTest extends GrpcTestSupport {

    private static final String PING_NAME = "PING";
    private static final int PING_ID = 7;

    private static final String CONSUMER = "grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                                           + "&routeControlledStreamObserver=true";
    private static final String REPLY = "grpc://dummy:0/?toRouteControlledStreamObserver=true";

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    @AfterEach
    public void stopGrpcChannels() {
        channels.values().forEach(channel -> channel.shutdown().shutdownNow());
        channels.clear();
    }

    @Test
    public void unchangedRequestIsNotSentAsResponse() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> blockingStub("rc-unchanged").pingSyncSync(pingRequest()));

        assertReplyTypeError(e.getStatus(), PingRequest.class);
    }

    @Test
    public void replyTypeErrorIsMutedByDefault() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> blockingStub("rc-unchanged-muted").pingSyncSync(pingRequest()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
        assertEquals("Exchange processing failed", e.getStatus().getDescription());
    }

    @Test
    public void replyIsConvertedToTheResponseType() {
        PongResponse pongResponse = blockingStub("rc-convert").pingSyncSync(pingRequest());

        assertEquals(PING_NAME + "PONG", pongResponse.getPongName());
        assertEquals(PING_ID, pongResponse.getPongId());
    }

    @Test
    public void serverStreamingSendsEachMessageOfAList() {
        Iterator<PongResponse> responses = blockingStub("rc-streaming-list").pingSyncAsync(pingRequest());

        assertEquals(PING_NAME + "PONG", responses.next().getPongName());
        assertEquals(PING_NAME + "PONG2", responses.next().getPongName());
        assertFalse(responses.hasNext());
    }

    @Test
    public void delegationUnchangedRequestIsNotSentAsResponse() throws Exception {
        PongResponseStreamObserver responseObserver = callBidiStreaming("rc-delegation-unchanged");

        assertTrue(responseObserver.pongResponses.isEmpty(), "the request must not be sent as response");
        StatusRuntimeException e = assertInstanceOf(StatusRuntimeException.class, responseObserver.error);
        assertReplyTypeError(e.getStatus(), PingRequest.class);
        assertEquals(0, responseObserver.completed.get());
    }

    @Test
    public void delegationConvertsTheReply() throws Exception {
        PongResponseStreamObserver responseObserver = callBidiStreaming("rc-delegation-convert");

        assertEquals(1, responseObserver.pongResponses.size());
        assertEquals(PING_NAME + "PONG", responseObserver.pongResponses.get(0).getPongName());
        assertEquals(1, responseObserver.completed.get());
    }

    @Test
    public void missingEventTypeHeaderFailsTheExchangeAndSendsNothing() {
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();

        Exchange exchange = template.send(REPLY, e -> {
            e.setProperty(GrpcConstants.GRPC_RESPONSE_OBSERVER, responseObserver);
            e.getMessage().setBody(PongResponse.newBuilder().setPongName(PING_NAME + "PONG").build());
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(e.getMessage().contains(GrpcConstants.GRPC_EVENT_TYPE_HEADER), e.getMessage());
        assertTrue(e.getMessage().contains(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT), e.getMessage());
        assertTrue(e.getMessage().contains(GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED), e.getMessage());
        assertTrue(e.getMessage().contains(GrpcConstants.GRPC_EVENT_TYPE_ON_ERROR), e.getMessage());
        assertTrue(responseObserver.pongResponses.isEmpty(), "nothing must be sent to the client");
        assertNull(responseObserver.error, "nothing must be sent to the client");
        assertEquals(0, responseObserver.completed.get());
    }

    @Test
    public void missingResponseObserverFailsTheExchange() {
        Exchange exchange = template.send(REPLY, e -> {
            e.getMessage().setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT);
            e.getMessage().setBody(PongResponse.newBuilder().setPongName(PING_NAME + "PONG").build());
        });

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, exchange.getException());
        assertTrue(e.getMessage().contains(GrpcConstants.GRPC_RESPONSE_OBSERVER), e.getMessage());
        assertTrue(e.getMessage().contains("routeControlledStreamObserver=true"), e.getMessage());
    }

    private static void assertReplyTypeError(Status status, Class<?> replyType) {
        assertEquals(Status.Code.INTERNAL, status.getCode());
        assertTrue(status.getDescription().contains(PongResponse.class.getName()),
                "the description must name the response type: " + status);
        assertTrue(status.getDescription().contains(replyType.getName()),
                "the description must name the type of the reply: " + status);
    }

    private static PingRequest pingRequest() {
        return PingRequest.newBuilder().setPingName(PING_NAME).setPingId(PING_ID).build();
    }

    private ManagedChannel channel(String routeId) {
        return channels.computeIfAbsent(routeId,
                id -> ManagedChannelBuilder.forAddress("localhost", getRoutePort(id)).usePlaintext().build());
    }

    private PingPongGrpc.PingPongBlockingStub blockingStub(String routeId) {
        return PingPongGrpc.newBlockingStub(channel(routeId)).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private PongResponseStreamObserver callBidiStreaming(String routeId) throws InterruptedException {
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();
        StreamObserver<PingRequest> requestObserver = PingPongGrpc.newStub(channel(routeId)).pingAsyncAsync(responseObserver);
        requestObserver.onNext(pingRequest());
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

                // unary and server streaming calls: the route sends the reply, then completes the call
                from(CONSUMER + "&hash=1")
                        .routeId("rc-unchanged")
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, constant(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT))
                        .to(REPLY + "&muteException=false")
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                                constant(GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED))
                        .to(REPLY);

                from(CONSUMER + "&hash=2")
                        .routeId("rc-unchanged-muted")
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, constant(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT))
                        .to(REPLY)
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                                constant(GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED))
                        .to(REPLY);

                from(CONSUMER + "&hash=3")
                        .routeId("rc-convert")
                        .process(e -> e.getMessage().setBody(toPong(e.getMessage().getBody(PingRequest.class), "PONG")))
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, constant(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT))
                        .to(REPLY)
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                                constant(GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED))
                        .to(REPLY);

                from(CONSUMER + "&hash=4")
                        .routeId("rc-streaming-list")
                        .process(e -> {
                            PingRequest request = e.getMessage().getBody(PingRequest.class);
                            e.getMessage().setBody(List.of(toPong(request, "PONG"), toPong(request, "PONG2")));
                        })
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, constant(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT))
                        .to(REPLY)
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                                constant(GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED))
                        .to(REPLY);

                // client and bidirectional streaming calls: the consumer forwards each event with its type header
                from(CONSUMER + "&hash=5&consumerStrategy=DELEGATION&forwardOnCompleted=true")
                        .routeId("rc-delegation-unchanged")
                        .to(REPLY + "&muteException=false");

                from(CONSUMER + "&hash=6&consumerStrategy=DELEGATION&forwardOnCompleted=true")
                        .routeId("rc-delegation-convert")
                        .process(e -> {
                            PingRequest request = e.getMessage().getBody(PingRequest.class);
                            if (request != null) {
                                e.getMessage().setBody(toPong(request, "PONG"));
                            }
                        })
                        .to(REPLY);
            }
        };
    }

    private static Pong toPong(PingRequest request, String suffix) {
        return new Pong(request.getPingName() + suffix, request.getPingId());
    }
}
