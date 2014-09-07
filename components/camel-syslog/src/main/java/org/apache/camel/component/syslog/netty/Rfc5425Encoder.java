/**
 *
 */
package org.apache.camel.component.syslog.netty;

import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;

@Sharable
public class Rfc5425Encoder extends OneToOneEncoder {

    @Override
    protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
        if (!(msg instanceof ChannelBuffer)) {
            return msg;
        }

        ChannelBuffer src = (ChannelBuffer) msg;
        int length = src.readableBytes();

        String headerString = length + " ";

        ChannelBuffer header = channel.getConfig().getBufferFactory().getBuffer(src.order(), headerString.getBytes(Charset.forName("UTF8")).length);
        header.writeBytes(headerString.getBytes(Charset.forName("UTF8")));

        return wrappedBuffer(header, src);
    }
}
