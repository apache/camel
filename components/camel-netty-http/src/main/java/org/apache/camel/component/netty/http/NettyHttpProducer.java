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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.ReferenceCountUtil;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyProducer;
import org.apache.camel.http.base.cookie.CookieHandler;
import org.apache.camel.support.SynchronizationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.http.HttpUtil.isStatusCodeOk;

/**
 * HTTP based {@link NettyProducer}.
 */
public class NettyHttpProducer extends NettyProducer {

    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpProducer.class);

    private int minOkRange;
    private int maxOkRange;

    public NettyHttpProducer(NettyHttpEndpoint nettyEndpoint, NettyConfiguration configuration) {
        super(nettyEndpoint, configuration);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        String range = getEndpoint().getConfiguration().getOkStatusCodeRange();
        parseStatusRange(range);
    }

    private void parseStatusRange(String range) {
        if (!range.contains(",")) {
            if (!org.apache.camel.support.http.HttpUtil.parseStatusRange(range, this::setRanges)) {
                minOkRange = Integer.parseInt(range);
                maxOkRange = minOkRange;
            }
        }
    }

    private void setRanges(int minOkRange, int maxOkRange) {
        this.minOkRange = minOkRange;
        this.maxOkRange = maxOkRange;
    }

    @Override
    public NettyHttpEndpoint getEndpoint() {
        return (NettyHttpEndpoint) super.getEndpoint();
    }

    @Override
    public NettyHttpConfiguration getConfiguration() {
        return (NettyHttpConfiguration) super.getConfiguration();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (getConfiguration().isDisableStreamCache() || getConfiguration().isHttpProxy()) {
            exchange.getExchangeExtension().setStreamCacheDisabled(true);
        }

        return super.process(exchange, new NettyHttpProducerCallback(exchange, callback, getConfiguration()));
    }

    @Override
    protected Object getRequestBody(Exchange exchange) throws Exception {
        // creating the url to use takes 2-steps
        final NettyHttpEndpoint endpoint = getEndpoint();
        final String uri = NettyHttpHelper.createURL(exchange, endpoint);
        final URI u = NettyHttpHelper.createURI(exchange, uri);

        final NettyHttpBinding nettyHttpBinding = endpoint.getNettyHttpBinding();
        final HttpRequest request = nettyHttpBinding.toNettyRequest(exchange.getIn(), u.toString(), getConfiguration());
        exchange.getIn().setHeader(NettyHttpConstants.HTTP_URL, uri);
        // Need to check if we need to close the connection or not
        if (!HttpUtil.isKeepAlive(request)) {
            // just want to make sure we close the channel if the keepAlive is not true
            exchange.setProperty(NettyHttpConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, true);
        }
        if (getConfiguration().isBridgeEndpoint()) {
            // Need to remove the Host key as it should be not used when bridging/proxying
            exchange.getIn().removeHeader("host");
        }

        final CookieHandler cookieHandler = endpoint.getCookieHandler();
        if (cookieHandler != null) {
            Map<String, List<String>> cookieHeaders = cookieHandler.loadCookies(exchange, u);
            for (Map.Entry<String, List<String>> entry : cookieHeaders.entrySet()) {
                String key = entry.getKey();
                if (!entry.getValue().isEmpty()) {
                    request.headers().add(key, entry.getValue());
                }
            }
        }

        return request;
    }

    /**
     * Callback that ensures the channel is returned to the pool when we are done.
     */
    private final class NettyHttpProducerCallback implements AsyncCallback {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final NettyHttpConfiguration configuration;

        private NettyHttpProducerCallback(Exchange exchange, AsyncCallback callback, NettyHttpConfiguration configuration) {
            this.exchange = exchange;
            this.callback = callback;
            this.configuration = configuration;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                // only handle when we are done asynchronous as then the netty producer is done sending, and we have a response
                if (!doneSync) {
                    NettyHttpMessage nettyMessage = exchange.getMessage(NettyHttpMessage.class);
                    if (nettyMessage != null) {
                        final FullHttpResponse response = nettyMessage.getHttpResponse();
                        // Need to retain the ByteBuffer for producer to consumer
                        if (response != null) {
                            response.content().retain();

                            // need to release the response when we are done
                            exchange.getExchangeExtension().addOnCompletion(new SynchronizationAdapter() {
                                @Override
                                public void onDone(Exchange exchange) {
                                    if (response.refCnt() > 0) {
                                        LOG.debug("Releasing Netty HttpResponse ByteBuf");
                                        ReferenceCountUtil.release(response);
                                    }
                                }
                            });

                            // the actual url is stored on the IN message in the getRequestBody method as its accessed on-demand
                            String actualUrl = exchange.getIn().getHeader(NettyHttpConstants.HTTP_URL, String.class);
                            int code = response.status() != null ? response.status().code() : -1;
                            LOG.debug("Http responseCode: {}", code);

                            // if there was a http error code then check if we should throw an exception
                            boolean ok;
                            if (minOkRange > 0) {
                                ok = code >= minOkRange && code <= maxOkRange;
                            } else {
                                ok = isStatusCodeOk(code, configuration.getOkStatusCodeRange());
                            }

                            if (ok) {
                                removeCamelHeaders(exchange);
                            } else if (getConfiguration().isThrowExceptionOnFailure()) {
                                // operation failed so populate exception to throw
                                Exception cause = NettyHttpHelper.populateNettyHttpOperationFailedException(exchange, actualUrl,
                                        response, code, getConfiguration().isTransferException());
                                exchange.setException(cause);
                            }
                        }
                    }
                }
            } finally {
                // ensure we call the delegated callback
                callback.done(doneSync);
            }
        }
    }

    /**
     * Remove Camel headers from Out message
     *
     * @param exchange the exchange
     */
    protected void removeCamelHeaders(Exchange exchange) {
        List<String> headersToRemove = exchange.getMessage().getHeaders().keySet()
                .stream()
                .filter(key -> !key.equalsIgnoreCase(Exchange.HTTP_RESPONSE_CODE)
                        && !key.equalsIgnoreCase(Exchange.HTTP_RESPONSE_TEXT)
                        && key.startsWith("Camel"))
                .collect(Collectors.toList());

        headersToRemove.stream().forEach(header -> exchange.getMessage().removeHeaders(header));
    }
}
