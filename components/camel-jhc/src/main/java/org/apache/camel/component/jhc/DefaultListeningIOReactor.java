/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.camel.component.jhc;

import org.apache.http.impl.nio.reactor.AbstractMultiworkerIOReactor;
import org.apache.http.impl.nio.reactor.ChannelEntry;
import org.apache.http.nio.params.HttpNIOParams;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.concurrent.ThreadFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * Trunk version of DefaultListenerIOReactor.
 * Should be removed when upgrading to httpcore > alpha5
 */
public class DefaultListeningIOReactor extends AbstractMultiworkerIOReactor
        implements ListeningIOReactor {

    private volatile boolean closed = false;

    private final HttpParams params;
    private final Selector selector;

    private IOReactorExceptionHandler exceptionHandler;

    public DefaultListeningIOReactor(
            int workerCount,
            final ThreadFactory threadFactory,
            final HttpParams params) throws IOReactorException {
        super(HttpNIOParams.getSelectInterval(params), workerCount, threadFactory);
        this.params = params;
        try {
            this.selector = Selector.open();
        } catch (IOException ex) {
            throw new IOReactorException("Failure opening selector", ex);
        }
    }

    public DefaultListeningIOReactor(
            int workerCount,
            final HttpParams params) throws IOReactorException {
        this(workerCount, null, params);
    }

    public void setExceptionHandler(final IOReactorExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void execute(final IOEventDispatch eventDispatch)
            throws InterruptedIOException, IOReactorException {
        if (eventDispatch == null) {
            throw new IllegalArgumentException("Event dispatcher may not be null");
        }
        startWorkers(eventDispatch);
        for (;;) {

            int readyCount;
            try {
                readyCount = this.selector.select(getSelectTimeout());
            } catch (InterruptedIOException ex) {
                throw ex;
            } catch (IOException ex) {
                throw new IOReactorException("Unexpected selector failure", ex);
            }

            if (this.closed) {
                break;
            }
            if (readyCount > 0) {
                processEvents(this.selector.selectedKeys());
            }
            verifyWorkers();
        }
    }

    private void processEvents(final Set selectedKeys)
            throws IOReactorException {
        for (Iterator it = selectedKeys.iterator(); it.hasNext(); ) {

            SelectionKey key = (SelectionKey) it.next();
            processEvent(key);

        }
        selectedKeys.clear();
    }

    private void processEvent(final SelectionKey key)
            throws IOReactorException {
        try {

            if (key.isAcceptable()) {

                ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                SocketChannel socketChannel = null;
                try {
                    socketChannel = serverChannel.accept();
                } catch (IOException ex) {
                    if (this.exceptionHandler == null || !this.exceptionHandler.handle(ex)) {
                        throw new IOReactorException("Failure accepting connection", ex);
                    }
                }

                if (socketChannel != null) {
                    try {
                        prepareSocket(socketChannel.socket());
                    } catch (IOException ex) {
                        if (this.exceptionHandler == null || !this.exceptionHandler.handle(ex)) {
                            throw new IOReactorException("Failure initalizing socket", ex);
                        }
                    }
                    ChannelEntry entry = new ChannelEntry(socketChannel); 
                    addChannel(entry);
                }
            }

        } catch (CancelledKeyException ex) {
            key.attach(null);
        }
    }

    protected void prepareSocket(final Socket socket) throws IOException {
        socket.setTcpNoDelay(HttpConnectionParams.getTcpNoDelay(this.params));
        socket.setSoTimeout(HttpConnectionParams.getSoTimeout(this.params));
        int linger = HttpConnectionParams.getLinger(this.params);
        if (linger >= 0) {
            socket.setSoLinger(linger > 0, linger);
        }
    }

    public SocketAddress listen(
            final SocketAddress address) throws IOException {
        if (this.closed) {
            throw new IllegalStateException("I/O reactor has been shut down");
        }
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(address);
        SelectionKey key = serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        key.attach(null);
        return serverChannel.socket().getLocalSocketAddress();
    }

    public void shutdown() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;

        // Close out all channels
        Set keys = this.selector.keys();
        for (Iterator it = keys.iterator(); it.hasNext(); ) {
            try {
                SelectionKey key = (SelectionKey) it.next();
                Channel channel = key.channel();
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException ignore) {
            }
        }
        // Stop dispatching I/O events
        this.selector.close();
        // Stop the workers
        stopWorkers(500);
    }

}
