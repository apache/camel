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
package org.apache.camel.component.undertow;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.undertow.client.ClientCallback;
import io.undertow.client.ClientConnection;
import io.undertow.client.ClientExchange;
import io.undertow.client.ClientRequest;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.http.common.HttpHelper;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.ChannelExceptionHandler;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.IoUtils;
import org.xnio.channels.StreamSinkChannel;

/**
 * Undertow {@link ClientCallback} that will get notified when the HTTP
 * connection is ready or when the client failed to connect. It will also handle
 * writing the request and reading the response in
 * {@link #writeRequest(ClientExchange, ByteBuffer)} and
 * {@link #setupResponseListener(ClientExchange)}. The main entry point is
 * {@link #completed(ClientConnection)} or {@link #failed(IOException)} in case
 * of errors, every error condition that should terminate Camel {@link Exchange}
 * should go to {@link #hasFailedWith(Exception)} and successful execution of
 * the exchange should end with {@link #finish(Message)}. Any
 * {@link ClientCallback}s that are added here should extend
 * {@link ErrorHandlingClientCallback}, best way to do that is to use the
 * {@link #on(Consumer)} helper method.
 */
class UndertowClientCallback implements ClientCallback<ClientConnection> {

    /**
     * {@link ClientCallback} that handles failures automatically by propagating
     * the exception to Camel {@link Exchange} and notifies Camel that the
     * exchange finished by calling {@link AsyncCallback#done(boolean)}.
     */
    final class ErrorHandlingClientCallback<T> implements ClientCallback<T> {

        private final Consumer<T> consumer;

        private ErrorHandlingClientCallback(final Consumer<T> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void completed(final T result) {
            consumer.accept(result);
        }

        @Override
        public void failed(final IOException e) {
            hasFailedWith(e);
        }

    }

    private static final Logger LOG = LoggerFactory.getLogger(UndertowClientCallback.class);

    private final ByteBuffer body;

    private final AsyncCallback callback;

    /**
     * A queue of resources that will be closed when the exchange ends, add more
     * resources via {@link #deferClose(Closeable)}.
     */
    private final BlockingDeque<Closeable> closables = new LinkedBlockingDeque<>();

    private final UndertowEndpoint endpoint;

    private final Exchange exchange;

    private final ClientRequest request;

    private final Boolean throwExceptionOnFailure;

    UndertowClientCallback(final Exchange exchange, final AsyncCallback callback, final UndertowEndpoint endpoint,
        final ClientRequest request, final ByteBuffer body) {
        this.exchange = exchange;
        this.callback = callback;
        this.endpoint = endpoint;
        this.request = request;
        this.body = body;
        throwExceptionOnFailure = endpoint.getThrowExceptionOnFailure();
    }

    @Override
    public void completed(final ClientConnection connection) {
        // we have established connection, make sure we close it
        deferClose(connection);

        // now we can send the request and perform the exchange: writing the
        // request and reading the response
        connection.sendRequest(request, on(this::performClientExchange));
    }

    @Override
    public void failed(final IOException e) {
        hasFailedWith(e);
    }

    ChannelListener<StreamSinkChannel> asyncWriter(final ByteBuffer body) {
        return channel -> {
            try {
                write(channel, body);

                if (body.hasRemaining()) {
                    channel.resumeWrites();
                } else {
                    flush(channel);
                }
            } catch (final IOException e) {
                hasFailedWith(e);
            }
        };
    }

    void deferClose(final Closeable closeable) {
        try {
            closables.putFirst(closeable);
        } catch (final InterruptedException e) {
            hasFailedWith(e);
        }
    }

    void finish(final Message result) {
        for (final Closeable closeable : closables) {
            IoUtils.safeClose(closeable);
        }

        if (result != null) {
            if (ExchangeHelper.isOutCapable(exchange)) {
                exchange.setOut(result);
            } else {
                exchange.setIn(result);
            }
        }

        callback.done(false);
    }

    void hasFailedWith(final Throwable e) {
        LOG.trace("Exchange has failed with", e);
        if (Boolean.TRUE.equals(throwExceptionOnFailure)) {
            exchange.setException(e);
        }

        finish(null);
    }

    <T> ClientCallback<T> on(final Consumer<T> consumer) {
        return new ErrorHandlingClientCallback<>(consumer);
    }

    void performClientExchange(final ClientExchange clientExchange) {
        // add response listener to the exchange, we could receive the response
        // at any time (async)
        setupResponseListener(clientExchange);

        // write the request
        writeRequest(clientExchange, body);
    }

    void setupResponseListener(final ClientExchange clientExchange) {
        clientExchange.setResponseListener(on((ClientExchange response) -> {
            LOG.trace("completed: {}", clientExchange);

            try {
                storeCookies(clientExchange);

                final UndertowHttpBinding binding = endpoint.getUndertowHttpBinding();
                final Message result = binding.toCamelMessage(clientExchange, exchange);

                // if there was a http error code then check if we should throw an exception
                final int code = clientExchange.getResponse().getResponseCode();
                LOG.debug("Http responseCode: {}", code);

                final boolean ok = HttpHelper.isStatusCodeOk(code, "200-299");
                if (!ok && throwExceptionOnFailure) {
                    // operation failed so populate exception to throw
                    final String uri = endpoint.getHttpURI().toString();
                    final String statusText = clientExchange.getResponse().getStatus();

                    // Convert Message headers (Map<String, Object>) to Map<String, String> as expected by HttpOperationsFailedException
                    // using Message versus clientExchange as its header values have extra formatting
                    final Map<String, String> headers = result.getHeaders().entrySet()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().toString()));

                    // Since result (Message) isn't associated with an Exchange yet, you can not use result.getBody(String.class)
                    final String bodyText = ExchangeHelper.convertToType(exchange, String.class, result.getBody());

                    final Exception cause = new HttpOperationFailedException(uri, code, statusText, null, headers, bodyText);

                    if (ExchangeHelper.isOutCapable(exchange)) {
                        exchange.setOut(result);
                    } else {
                        exchange.setIn(result);
                    }

                    // make sure to fail with HttpOperationFailedException
                    hasFailedWith(cause);
                } else {
                    // we end Camel exchange here
                    finish(result);
                }
            } catch (Throwable e) {
                hasFailedWith(e);
            }
        }));
    }

    void storeCookies(final ClientExchange clientExchange) throws IOException, URISyntaxException {
        if (endpoint.getCookieHandler() != null) {
            // creating the url to use takes 2-steps
            final String url = UndertowHelper.createURL(exchange, endpoint);
            final URI uri = UndertowHelper.createURI(exchange, url, endpoint);
            final HeaderMap headerMap = clientExchange.getResponse().getResponseHeaders();
            final Map<String, List<String>> m = new HashMap<>();
            for (final HttpString headerName : headerMap.getHeaderNames()) {
                final List<String> headerValue = new LinkedList<>();
                for (int i = 0; i < headerMap.count(headerName); i++) {
                    headerValue.add(headerMap.get(headerName, i));
                }
                m.put(headerName.toString(), headerValue);
            }
            endpoint.getCookieHandler().storeCookies(exchange, uri, m);
        }
    }

    void writeRequest(final ClientExchange clientExchange, final ByteBuffer body) {
        final StreamSinkChannel requestChannel = clientExchange.getRequestChannel();
        if (body != null) {
            try {
                // try writing, we could be on IO thread and ready to write to
                // the socket (or not)
                write(requestChannel, body);

                if (body.hasRemaining()) {
                    // we did not write all of body (or at all) register a write
                    // listener to write asynchronously
                    requestChannel.getWriteSetter().set(asyncWriter(body));
                    requestChannel.resumeWrites();
                } else {
                    // we are done, we need to flush the request
                    flush(requestChannel);
                }
            } catch (final IOException e) {
                hasFailedWith(e);
            }
        }
    }

    static void flush(final StreamSinkChannel channel) throws IOException {
        // the canonical way of flushing Xnio channels
        channel.shutdownWrites();
        if (!channel.flush()) {
            final ChannelListener<StreamSinkChannel> safeClose = IoUtils::safeClose;
            final ChannelExceptionHandler<Channel> closingChannelExceptionHandler = ChannelListeners
                .closingChannelExceptionHandler();
            final ChannelListener<StreamSinkChannel> flushingChannelListener = ChannelListeners
                .flushingChannelListener(safeClose, closingChannelExceptionHandler);
            channel.getWriteSetter().set(flushingChannelListener);
            channel.resumeWrites();
        }
    }

    static void write(final StreamSinkChannel channel, final ByteBuffer body) throws IOException {
        int written = 1;
        while (body.hasRemaining() && written > 0) {
            written = channel.write(body);
        }
    }
}
