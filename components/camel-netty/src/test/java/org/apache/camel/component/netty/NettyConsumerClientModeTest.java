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
package org.apache.camel.component.netty;


import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.Executors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.frame.Delimiters;
import org.jboss.netty.util.CharsetUtil;
import org.junit.Test;

public class NettyConsumerClientModeTest extends BaseNettyTest {
    private static final ChannelBuffer DATA = ChannelBuffers.copiedBuffer("Willem".getBytes(CharsetUtil.UTF_8));
    private MyServer server;
    
   
    public void startNettyServer() {
        server = new MyServer(getPort());
        server.start();
    }
   
    public void shutdownServer() {
        if (server != null) {
            server.shutdown();
        }
    }
    @Test
    public void testNettyRoute() throws Exception {
        try {
            startNettyServer();
            MockEndpoint receive = context.getEndpoint("mock:receive", MockEndpoint.class);
            receive.expectedBodiesReceived("Bye Willem");
            context.startRoute("client");
            receive.assertIsSatisfied();
        } finally {
            shutdownServer();
        }
        
    }
      
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("netty:tcp://localhost:{{port}}?textline=true&clientMode=true").id("client")
                .process(new Processor() {
                    public void process(final Exchange exchange) {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + body);
                    }
                }).to("mock:receive").noAutoStartup();
            }
        };
    }
    
    private static class MyServer {
        private int port;
        private ServerBootstrap bootstrap;

        MyServer(int port) {
            this.port = port;
        }

        public void start() {
            // Configure the server.
            bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(), 
                    Executors.newCachedThreadPool()));
            // Set up the event pipeline factory.
            bootstrap.setPipelineFactory(new ServerPipelineFactory());
            // Bind and start to accept incoming connections.
            bootstrap.bind(new InetSocketAddress(port));
        }
        
        public void shutdown() {
            bootstrap.shutdown();
        }
        
    }
    
    private static class ServerHandler extends SimpleChannelHandler {

        @Override
        public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) {
            Channel ch = e.getChannel();
            ch.write(DATA);
            ChannelFuture f = ch.write(Delimiters.lineDelimiter()[0]);
            
            f.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture future) {
                    Channel ch = future.getChannel();
                    ch.close();
                }
            });
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
            e.getCause().printStackTrace();
            e.getChannel().close();
        }
    }
    
    private static class ServerPipelineFactory implements ChannelPipelineFactory {

        public ChannelPipeline getPipeline() {
            ChannelPipeline p = Channels.pipeline();
            Charset charset = CharsetUtil.UTF_8;
           
            ChannelBuffer[] delimiters = Delimiters.nulDelimiter();
           
            // setup the textline encoding and decoding
            p.addLast("decoder1", ChannelHandlerFactories.newDelimiterBasedFrameDecoder(1024 * 8, delimiters).newChannelHandler());
            p.addLast("decoder2", ChannelHandlerFactories.newStringDecoder(charset).newChannelHandler());
            
            p.addLast("encoder", ChannelHandlerFactories.newStringEncoder(charset).newChannelHandler());
            
            p.addLast("handler", new ServerHandler());
            return p;
        }
    }

}
