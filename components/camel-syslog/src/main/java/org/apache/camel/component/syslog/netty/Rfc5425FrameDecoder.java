/**
 * 
 */
package org.apache.camel.component.syslog.netty;

import org.apache.camel.component.netty.ChannelHandlerFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

/**
 * @author svenrienstra
 */
public class Rfc5425FrameDecoder extends FrameDecoder implements ChannelHandlerFactory {

    private Integer currentFramelength;

    /**
     * @see org.jboss.netty.handler.codec.frame.FrameDecoder#decode(org.jboss.netty.channel.ChannelHandlerContext,
     *      org.jboss.netty.channel.Channel,
     *      org.jboss.netty.buffer.ChannelBuffer)
     */
    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) throws Exception {
        if (currentFramelength == null) {
            // find index of the first space, it should be after the length
            // field
            int index = indexOf(buffer, ChannelBuffers.wrappedBuffer(new byte[] {' '}));

            // Read part until the first space, if we have found one
            StringBuffer lengthbuffer = new StringBuffer();
            if (index > -1) {
                lengthbuffer.append(new String(buffer.readBytes(index).array()));
            }

            int length;
            try {
                // add one because we have to take in account the space after
                // the length field
                length = Integer.parseInt(lengthbuffer.toString()) + 1;
            } catch (NumberFormatException e) {
                length = -1;
            }

            // We have not found the length field, reset the buffer so we can
            // retry next time
            if (length < 0) {
                buffer.resetReaderIndex();
                return null;
            }
            currentFramelength = length;
        }

        // Buffer does not contain enough data yet, wait until it does
        if (buffer.readableBytes() < currentFramelength) {
            return null;
        }

        // read the message
        int lengthToRead = currentFramelength;
        currentFramelength = null;
        return buffer.readBytes(lengthToRead);
    }

    /**
     * Borrowed from the DelimiterBasedFrameDecoder Returns the number of bytes
     * between the readerIndex of the haystack and the first needle found in the
     * haystack. -1 is returned if no needle is found in the haystack.
     */
    private static int indexOf(ChannelBuffer haystack, ChannelBuffer needle) {
        for (int i = haystack.readerIndex(); i < haystack.writerIndex(); i++) {
            int haystackIndex = i;
            int needleIndex;
            for (needleIndex = 0; needleIndex < needle.capacity(); needleIndex++) {
                if (haystack.getByte(haystackIndex) != needle.getByte(needleIndex)) {
                    break;
                } else {
                    haystackIndex++;
                    if (haystackIndex == haystack.writerIndex() && needleIndex != needle.capacity() - 1) {
                        return -1;
                    }
                }
            }

            if (needleIndex == needle.capacity()) {
                // Found the needle from the haystack!
                return i - haystack.readerIndex();
            }
        }
        return -1;
    }

    @Override
    public ChannelHandler newChannelHandler() {
        return new Rfc5425FrameDecoder();
    }
}
