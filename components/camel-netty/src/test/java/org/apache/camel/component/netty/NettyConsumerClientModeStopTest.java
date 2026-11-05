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
package org.apache.camel.component.netty;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringEncoder;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A consumer with clientMode=true closes its connection and stops reconnecting when it stops, also with a shared worker
 * group, and reconnects with a single worker thread.
 */
class NettyConsumerClientModeStopTest extends BaseNettyTest {

    @BindToRegistry("sharedWorkerGroup")
    private final EventLoopGroup sharedWorkerGroup
            = new NettyWorkerPoolBuilder().withWorkerCount(2).withName("SharedClientWorker").build();

    @BindToRegistry("sharedSingleWorkerGroup")
    private final EventLoopGroup sharedSingleWorkerGroup
            = new NettyWorkerPoolBuilder().withWorkerCount(1).withName("SharedSingleClientWorker").build();

    // holds the event loop of the consumer once its reconnect has started to connect
    @BindToRegistry("holdReconnect")
    private final ServerInitializerFactory holdReconnect = new HoldReconnectInitializerFactory();
    private final AtomicInteger initializedChannels = new AtomicInteger();
    private final CountDownLatch reconnectStarted = new CountDownLatch(1);
    private final CountDownLatch releaseEventLoop = new CountDownLatch(1);

    // the server side of each connection the consumer opened, in order
    private final BlockingQueue<Channel> connections = new LinkedBlockingQueue<>();
    private EventLoopGroup serverBossGroup;
    private EventLoopGroup serverWorkerGroup;

    NettyConsumerClientModeStopTest() {
        testConfiguration().withUseRouteBuilder(false);
    }

    @AfterEach
    void shutdownGroups() {
        releaseEventLoop.countDown();
        // stop the consumers before the event loops they use
        context.stop();
        if (serverBossGroup != null) {
            serverBossGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
            serverWorkerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        }
        sharedWorkerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
        sharedSingleWorkerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).awaitUninterruptibly(10, TimeUnit.SECONDS);
    }

    @Test
    void stopClosesTheConnectionWithASharedWorkerGroup() throws Exception {
        startServer();
        addClientRoute("workerGroup=#sharedWorkerGroup&reconnect=false");
        Channel first = nextConnection();

        context.getRouteController().stopRoute("client");

        assertTrue(first.closeFuture().await(10, TimeUnit.SECONDS), "the connection of the stopped consumer is closed");
    }

    @Test
    void noReconnectAfterStopWithASharedWorkerGroup() throws Exception {
        startServer();
        addClientRoute("workerGroup=#sharedWorkerGroup&reconnect=true&reconnectInterval=100");
        Channel first = nextConnection();

        context.getRouteController().stopRoute("client");
        // the server drops the connection (if the stop did not close it)
        first.close().sync();

        // ten reconnect intervals
        assertNull(connections.poll(1, TimeUnit.SECONDS), "a connection opened after the stop");
    }

    @Test
    void stopWhileReconnectingWithASharedWorkerGroup() throws Exception {
        startServer();
        addClientRoute(
                "workerGroup=#sharedSingleWorkerGroup&reconnect=true&reconnectInterval=100&serverInitializerFactory=#holdReconnect");
        nextConnection().close().sync();
        // the reconnect has started to connect, and its event loop is held before the connect completes
        assertTrue(reconnectStarted.await(10, TimeUnit.SECONDS), "the reconnect started");

        context.getRouteController().stopRoute("client");
        releaseEventLoop.countDown();

        // a connection the reconnect made anyway is closed, and none follows
        Channel late = connections.poll(1, TimeUnit.SECONDS);
        if (late != null) {
            assertTrue(late.closeFuture().await(10, TimeUnit.SECONDS),
                    "the connection of the reconnect in progress at the stop is closed");
        }
        assertNull(connections.poll(1, TimeUnit.SECONDS), "a connection opened after the stop");
    }

    @Test
    void restartHasOneConnectionWithASharedWorkerGroup() throws Exception {
        startServer();
        addClientRoute("workerGroup=#sharedWorkerGroup&reconnect=true&reconnectInterval=100");
        Channel first = nextConnection();

        context.getRouteController().stopRoute("client");
        context.getRouteController().startRoute("client");
        Channel second = nextConnection();

        assertTrue(first.closeFuture().await(10, TimeUnit.SECONDS), "the connection of the first start is closed");
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("Hello");
        second.writeAndFlush("Hello\n");
        mock.assertIsSatisfied();
        assertNull(connections.poll(1, TimeUnit.SECONDS), "a third connection");
    }

    @Test
    void reconnectsAfterTheServerDroppedTheConnectionWithASharedWorkerGroup() throws Exception {
        startServer();
        addClientRoute("workerGroup=#sharedWorkerGroup&reconnect=true&reconnectInterval=100");
        nextConnection().close().sync();

        Channel second = nextConnection();
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("Hello");
        second.writeAndFlush("Hello\n");
        mock.assertIsSatisfied();
    }

    @Test
    void twoConsumersReconnectWithASharedWorkerGroup() throws Exception {
        startServer();
        addClientRoute("client", "workerGroup=#sharedWorkerGroup&reconnect=true&reconnectInterval=10");
        addClientRoute("client2", "workerGroup=#sharedWorkerGroup&reconnect=true&reconnectInterval=11");

        // the server drops both connections at once, a few times: the reconnects run at the same time on both event
        // loops of the group, and each connects a channel that may be on the other loop
        Channel first = nextConnection();
        Channel second = nextConnection();
        for (int i = 0; i < 5; i++) {
            first.close();
            second.close().sync();
            first = nextConnection();
            second = nextConnection();
        }
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceivedInAnyOrder("Hello", "World");
        first.writeAndFlush("Hello\n");
        second.writeAndFlush("World\n");
        mock.assertIsSatisfied();
        // one connection per consumer: no reconnect chain was duplicated
        assertNull(connections.poll(1, TimeUnit.SECONDS), "a third connection");
    }

    @Test
    void reconnectsWithOneWorkerThread() throws Exception {
        // the server is not up yet: the consumer starts and schedules a reconnect
        addClientRoute("workerCount=1&reconnect=true&reconnectInterval=100");
        startServer();

        Channel first = nextConnection();
        MockEndpoint mock = getMockEndpoint("mock:received");
        mock.expectedBodiesReceived("Hello", "World");
        first.writeAndFlush("Hello\n");
        // and again after the server dropped the connection
        first.close().sync();
        nextConnection().writeAndFlush("World\n");
        mock.assertIsSatisfied();
    }

    @Test
    void stopClosesTheConnectionWithItsOwnWorkerGroup() throws Exception {
        startServer();
        addClientRoute("reconnect=true&reconnectInterval=100");
        Channel first = nextConnection();

        context.getRouteController().stopRoute("client");

        assertTrue(first.closeFuture().await(10, TimeUnit.SECONDS), "the connection of the stopped consumer is closed");
        assertNull(connections.poll(1, TimeUnit.SECONDS), "a connection opened after the stop");
    }

    private final class HoldReconnectInitializerFactory extends ServerInitializerFactory {

        @Override
        public ServerInitializerFactory createPipelineFactory(NettyConsumer consumer) {
            return new DefaultServerInitializerFactory(consumer) {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    super.initChannel(ch);
                    if (initializedChannels.incrementAndGet() == 2) {
                        // the channel of the first reconnect: hold its event loop until the test releases it
                        ch.eventLoop().execute(() -> {
                            try {
                                releaseEventLoop.await(20, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });
                        reconnectStarted.countDown();
                    }
                }
            };
        }

        @Override
        protected void initChannel(Channel ch) {
            throw new UnsupportedOperationException("only used to create the pipeline factory of the consumer");
        }
    }

    private Channel nextConnection() throws InterruptedException {
        Channel channel = connections.poll(10, TimeUnit.SECONDS);
        assertNotNull(channel, "a connection from the consumer");
        return channel;
    }

    private void addClientRoute(String options) throws Exception {
        addClientRoute("client", options);
    }

    private void addClientRoute(String routeId, String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("netty:tcp://localhost:{{port}}?textline=true&clientMode=true&" + options).routeId(routeId)
                        .to("mock:received");
            }
        });
        context.start();
    }

    private void startServer() throws InterruptedException {
        serverBossGroup = new NettyWorkerPoolBuilder().withWorkerCount(1).withName("TestServerBoss").build();
        serverWorkerGroup = new NettyWorkerPoolBuilder().withWorkerCount(1).withName("TestServerWorker").build();
        new ServerBootstrap().group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new StringEncoder());
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) {
                                connections.add(ctx.channel());
                            }
                        });
                    }
                }).bind("localhost", getPort()).sync();
    }
}
