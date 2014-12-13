package org.apache.camel.component.netty4;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.util.CharsetUtil;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Created by tterm on 06.11.14.
 */
public class NettyUdpConnectionlessSendTest extends BaseNettyTest {
    private static final String SEND_STRING = "***<We all love camel>***";
    private static final int SEND_COUNT = 20;
    private int receivedCount = 0;
    private EventLoopGroup group;
    private Bootstrap bootstrap;

    public void createNettyUdpReceiver() {
        group = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        channel.pipeline().addLast(new UdpHandler());
                        channel.pipeline().addLast(new ContentHandler());
                    }
                }).localAddress(new InetSocketAddress(8601));
    }


    public void bind() {
        bootstrap.bind().syncUninterruptibly().channel().closeFuture().syncUninterruptibly();
    }

    public void stop() {
        group.shutdownGracefully();
    }

    @Test
    public void sendConnectionlessUdp() throws Exception {
        createNettyUdpReceiver();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                bind();
            }
        });
        t.start();
        Thread.sleep(1000);
        for (int i = 0; i < SEND_COUNT; ++i) {
            template.sendBody("direct:in", SEND_STRING);
        }
        Thread.sleep(1000);
        stop();
        assertTrue("We should have received some datagrams", receivedCount > 0);

    }

    @Test
    public void sendWithoutReceiver() throws Exception{
        int exceptionCount = 0;
        for (int i = 0; i < SEND_COUNT; ++i) {
            try {
                template.sendBody("direct:in", SEND_STRING);
            } catch (Exception ex) {
                ++exceptionCount;
            }
        }
        assertEquals("No exception should occur", 0, exceptionCount);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:in").to("netty4:udp://localhost:8601?sync=false&textline=true&udpConnectionlessSend=true");
            }
        };
    }

    public class UdpHandler extends MessageToMessageDecoder<DatagramPacket> {
        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket, List<Object> objects) throws Exception {
            objects.add(datagramPacket.content().toString(CharsetUtil.UTF_8));
        }
    }

    public class ContentHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, String s) throws Exception {
            ++receivedCount;
            assertEquals(SEND_STRING, s);
        }
    }
}
