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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.LineDisciplineTerminal;

/**
 * The terminal of one browser session of {@code camel tui --web} (CAMEL-25581). The page (xterm.js) talks JSON over the
 * WebSocket: {@code init} with its size, {@code read} with what the user typed, {@code resize} with the new size; it
 * gets the terminal output back as text frames. Here that becomes a JLine {@link LineDisciplineTerminal}, so a web
 * session runs on TamboUI's JLineBackend as a local one does. Closing the socket ends the input of the terminal, which
 * ends the session; the session ending closes the socket.
 */
final class WebTerminal extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger LOG = System.getLogger(WebTerminal.class.getName());
    // the page is always xterm.js
    private static final String TYPE = "xterm-256color";
    // a size the page cannot inflate into a screen buffer too big to allocate
    static final int MAX_COLUMNS = 1000;
    static final int MAX_ROWS = 500;

    /** Runs the sessions of the server. */
    interface Sessions {

        /**
         * Starts a session on the terminal, and runs {@code ended} when it is over. Returns false when it cannot start
         * one (the server stops, or has too many).
         */
        boolean start(Terminal terminal, Runnable ended);
    }

    private final ChannelGroup channels;
    private final Sessions sessions;
    private LineDisciplineTerminal terminal;

    WebTerminal(ChannelGroup channels, Sessions sessions) {
        this.channels = channels;
        this.sessions = sessions;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // the server closes the sockets of the open sessions when it stops
        channels.add(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws IOException {
        JsonObject message;
        try {
            message = (JsonObject) Jsoner.deserialize(frame.text());
        } catch (Exception e) {
            LOG.log(Level.DEBUG, "Web TUI: ignoring a message that is not JSON");
            return;
        }
        String action = message.get("action") instanceof String a ? a : "";
        if ("init".equals(action) && terminal == null) {
            open(ctx.channel(), message);
        } else if ("read".equals(action) && terminal != null && message.get("data") instanceof String data) {
            terminal.processInputBytes(data.getBytes(StandardCharsets.UTF_8));
        } else if ("resize".equals(action) && terminal != null) {
            terminal.setSize(size(message));
            terminal.raise(Terminal.Signal.WINCH);
        }
    }

    private void open(Channel channel, JsonObject init) throws IOException {
        FrameOutput output = new FrameOutput(text -> {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(text));
            }
        });
        terminal = new LineDisciplineTerminal("camel-tui-web", TYPE, output, StandardCharsets.UTF_8);
        terminal.setSize(size(init));
        if (!sessions.start(terminal, channel::close)) {
            channel.close();
        }
    }

    static Size size(JsonObject message) {
        return new Size(bounded(message.get("cols"), 80, MAX_COLUMNS), bounded(message.get("rows"), 24, MAX_ROWS));
    }

    private static int bounded(Object value, int otherwise, int max) {
        return value instanceof Number n ? Math.max(1, Math.min(max, n.intValue())) : otherwise;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // the browser went away: the end of the input ends the session
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                LOG.log(Level.DEBUG, "Web TUI: closing the terminal failed", e);
            }
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.log(Level.DEBUG, "Web TUI: closing the socket after an error", cause);
        ctx.close();
    }

    /**
     * The output of the terminal, handed on as text when it is flushed (TamboUI flushes after each frame it draws). A
     * UTF-8 sequence split between two flushes is kept until it is whole, so a character is never cut in two.
     */
    static final class FrameOutput extends OutputStream {

        // past this, the text is handed on without waiting for a flush
        private static final int MAX_PENDING = 256 * 1024;

        private final Consumer<String> sink;
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        private ByteBuffer pending = ByteBuffer.allocate(8192);

        FrameOutput(Consumer<String> sink) {
            this.sink = sink;
        }

        @Override
        public synchronized void write(int b) {
            room(1);
            pending.put((byte) b);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            room(length);
            pending.put(bytes, offset, length);
            if (pending.position() > MAX_PENDING) {
                flush();
            }
        }

        private void room(int length) {
            if (pending.remaining() < length) {
                ByteBuffer larger = ByteBuffer.allocate(Math.max(pending.capacity() * 2, pending.position() + length));
                pending.flip();
                larger.put(pending);
                pending = larger;
            }
        }

        @Override
        public synchronized void flush() {
            if (pending.position() == 0) {
                return;
            }
            pending.flip();
            CharBuffer text = CharBuffer.allocate(pending.remaining());
            // the end of a sequence that is not complete yet stays in pending
            decoder.decode(pending, text, false);
            pending.compact();
            text.flip();
            if (text.hasRemaining()) {
                sink.accept(text.toString());
            }
        }
    }
}
