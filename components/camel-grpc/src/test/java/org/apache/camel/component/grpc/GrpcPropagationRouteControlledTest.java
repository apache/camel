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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.grpc.GrpcConsumerReplyTypeTest.PongResponseStreamObserver;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the default PROPAGATION consumer strategy and routeControlledStreamObserver=true, the route sends the replies of
 * client streaming and bidirectional streaming calls with the toRouteControlledStreamObserver producer: the consumer
 * sends nothing itself.
 */
public class GrpcPropagationRouteControlledTest extends GrpcTestSupport {

    private static final String CONSUMER = "grpc://localhost:0/org.apache.camel.component.grpc.PingPong?synchronous=true"
                                           + "&consumerStrategy=PROPAGATION&routeControlledStreamObserver=true"
                                           + "&forwardOnCompleted=true&muteException=false";
    private static final String REPLY = "grpc://dummy:0/?toRouteControlledStreamObserver=true";

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();
    // the exception of each exchange done by a consumer route (null when it completed), by route id
    private final Map<String, List<Exception>> doneExchanges = new ConcurrentHashMap<>();
    private final AtomicInteger clientStreamingPings = new AtomicInteger();

    @AfterEach
    public void stopGrpcChannels() {
        channels.values().forEach(channel -> channel.shutdown().shutdownNow());
        channels.clear();
    }

    @Test
    public void bidiStreamingRouteSendsOneReplyPerEvent() throws Exception {
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();
        StreamObserver<PingRequest> requestObserver
                = PingPongGrpc.newStub(channel("propagation-bidi")).pingAsyncAsync(responseObserver);
        requestObserver.onNext(pingRequest("PING1"));
        requestObserver.onNext(pingRequest("PING2"));
        requestObserver.onCompleted();
        assertTrue(responseObserver.latch.await(5, TimeUnit.SECONDS));

        assertNull(responseObserver.error, "the call must not end with an error");
        assertEquals(2, responseObserver.pongResponses.size(), "exactly one reply per event: "
                                                               + responseObserver.pongResponses);
        assertEquals("PING1PONG", responseObserver.pongResponses.get(0).getPongName());
        assertEquals("PING2PONG", responseObserver.pongResponses.get(1).getPongName());
        assertEquals(1, responseObserver.completed.get());
        // two onNext exchanges and the forwarded onCompleted exchange, none failed
        assertEquals(Collections.nCopies(3, null), awaitDoneExchanges("propagation-bidi", 3));
    }

    @Test
    public void clientStreamingRouteSendsTheReplyOnCompleted() throws Exception {
        PongResponseStreamObserver responseObserver = new PongResponseStreamObserver();
        StreamObserver<PingRequest> requestObserver
                = PingPongGrpc.newStub(channel("propagation-client-streaming")).pingAsyncSync(responseObserver);
        requestObserver.onNext(pingRequest("PING1"));
        requestObserver.onNext(pingRequest("PING2"));
        requestObserver.onCompleted();
        assertTrue(responseObserver.latch.await(5, TimeUnit.SECONDS));

        // the consumer does not send the unchanged requests of the onNext events as replies
        assertNull(responseObserver.error, "the call must not end with an error");
        assertEquals(1, responseObserver.pongResponses.size(), "exactly one reply: " + responseObserver.pongResponses);
        assertEquals("PONG2", responseObserver.pongResponses.get(0).getPongName());
        assertEquals(1, responseObserver.completed.get());
        assertEquals(Collections.nCopies(3, null), awaitDoneExchanges("propagation-client-streaming", 3));
    }

    private List<Exception> awaitDoneExchanges(String routeId, int count) {
        List<Exception> exceptions
                = doneExchanges.computeIfAbsent(routeId, id -> Collections.synchronizedList(new ArrayList<>()));
        await().atMost(5, TimeUnit.SECONDS).until(() -> exceptions.size() >= count);
        synchronized (exceptions) {
            assertEquals(count, exceptions.size(), "exchanges done by route " + routeId + ": " + exceptions);
            return new ArrayList<>(exceptions);
        }
    }

    private static PingRequest pingRequest(String name) {
        return PingRequest.newBuilder().setPingName(name).setPingId(1).build();
    }

    private ManagedChannel channel(String routeId) {
        return channels.computeIfAbsent(routeId,
                id -> ManagedChannelBuilder.forAddress("localhost", getRoutePort(id)).usePlaintext().build());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                getContext().getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
                    @Override
                    public void notify(CamelEvent event) {
                        Exchange exchange = ((ExchangeEvent) event).getExchange();
                        if (exchange.getFromRouteId() != null) {
                            doneExchanges.computeIfAbsent(exchange.getFromRouteId(),
                                    id -> Collections.synchronizedList(new ArrayList<>()))
                                    .add(exchange.getException());
                        }
                    }

                    @Override
                    public boolean isEnabled(CamelEvent event) {
                        return event instanceof CamelEvent.ExchangeCompletedEvent
                                || event instanceof CamelEvent.ExchangeFailedEvent;
                    }
                });

                // bidirectional streaming: a reply for each request, then the forwarded onCompleted ends the call
                from(CONSUMER + "&hash=1")
                        .routeId("propagation-bidi")
                        .process(e -> {
                            PingRequest request = e.getMessage().getBody(PingRequest.class);
                            if (request != null) {
                                e.getMessage().setBody(PongResponse.newBuilder()
                                        .setPongName(request.getPingName() + "PONG").setPongId(request.getPingId())
                                        .build());
                            }
                        })
                        .to(REPLY);

                // client streaming: the requests are counted, and the single reply is sent on the forwarded onCompleted
                from(CONSUMER + "&hash=2")
                        .routeId("propagation-client-streaming")
                        .choice()
                        .when(header(GrpcConstants.GRPC_EVENT_TYPE_HEADER).isEqualTo(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT))
                        .process(e -> clientStreamingPings.incrementAndGet())
                        .otherwise()
                        .process(e -> e.getMessage().setBody(
                                PongResponse.newBuilder().setPongName("PONG" + clientStreamingPings.get()).build()))
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER, constant(GrpcConstants.GRPC_EVENT_TYPE_ON_NEXT))
                        .to(REPLY)
                        .setHeader(GrpcConstants.GRPC_EVENT_TYPE_HEADER,
                                constant(GrpcConstants.GRPC_EVENT_TYPE_ON_COMPLETED))
                        .to(REPLY)
                        .end();
            }
        };
    }
}
