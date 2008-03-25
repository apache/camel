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

package org.apache.camel.component.jhc;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import org.apache.http.impl.nio.reactor.AbstractMultiworkerIOReactor;
import org.apache.http.impl.nio.reactor.ChannelEntry;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.params.HttpParams;
import org.apache.http.util.concurrent.ThreadFactory;

/**
 * Trunk version of DefaultListenerIOReactor.
 * Should be removed when upgrading to httpcore > alpha5
 */
public class DefaultListeningIOReactor extends AbstractMultiworkerIOReactor
        implements ListeningIOReactor {

    private volatile boolean closed;

    private final HttpParams params;



    public DefaultListeningIOReactor(
            int workerCount,
            final ThreadFactory threadFactory,
            final HttpParams params) throws IOReactorException {
        super(workerCount, threadFactory, params);
        this.params = params;
    }

    public DefaultListeningIOReactor(
            int workerCount,
            final HttpParams params) throws IOReactorException {
        this(workerCount, null, params);
    }


    private void processEvents(final Set selectedKeys) throws IOReactorException {
        for (Iterator it = selectedKeys.iterator(); it.hasNext();) {

            SelectionKey key = (SelectionKey)it.next();
            processEvent(key);

        }
        selectedKeys.clear();
    }

    private void processEvent(final SelectionKey key) throws IOReactorException {
        try {

            if (key.isAcceptable()) {

                ServerSocketChannel serverChannel = (ServerSocketChannel)key.channel();
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

    @Override
    protected void processEvents(int count) throws IOReactorException {
        processEvents(this.selector.selectedKeys());

    }

}
