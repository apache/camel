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
package org.apache.camel.component.netty4.http;

import java.net.URI;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty4.NettyConfiguration;
import org.apache.camel.component.netty4.NettyConstants;
import org.apache.camel.component.netty4.NettyProducer;


/**
 * HTTP based {@link NettyProducer}.
 */
public class NettyHttpProducer extends NettyProducer {

    public NettyHttpProducer(NettyHttpEndpoint nettyEndpoint, NettyConfiguration configuration) {
        super(nettyEndpoint, configuration);
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
        return super.process(exchange, new NettyHttpProducerCallback(exchange, callback));
    }

    @Override
    protected Object getRequestBody(Exchange exchange) throws Exception {
        // creating the url to use takes 2-steps
        String uri = NettyHttpHelper.createURL(exchange, getEndpoint());
        URI u = NettyHttpHelper.createURI(exchange, uri, getEndpoint());

        HttpRequest request = getEndpoint().getNettyHttpBinding().toNettyRequest(exchange.getIn(), u.toString(), getConfiguration());
        String actualUri = request.getUri();
        exchange.getIn().setHeader(Exchange.HTTP_URL, actualUri);
        // Need to check if we need to close the connection or not
        if (!HttpHeaders.isKeepAlive(request)) {
            // just want to make sure we close the channel if the keepAlive is not true
            exchange.setProperty(NettyConstants.NETTY_CLOSE_CHANNEL_WHEN_COMPLETE, true);
        }
        if (getConfiguration().isBridgeEndpoint()) {
            // Need to remove the Host key as it should be not used when bridging/proxying
            exchange.getIn().removeHeader("host");
        }

        return request;
    }

    /**
     * Callback that ensures the channel is returned to the pool when we are done.
     */
    private final class NettyHttpProducerCallback implements AsyncCallback {

        private final Exchange exchange;
        private final AsyncCallback callback;

        private NettyHttpProducerCallback(Exchange exchange, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void done(boolean doneSync) {
            try {
                NettyHttpMessage nettyMessage = exchange.hasOut() ? exchange.getOut(NettyHttpMessage.class) : exchange.getIn(NettyHttpMessage.class);
                if (nettyMessage != null) {
                    FullHttpResponse response = nettyMessage.getHttpResponse();
                    // Need to retain the ByteBuffer for producer to consumer
                    // TODO Remove this part of ByteBuffer right away
                    if (response != null) {
                        response.content().retain();
                        // the actual url is stored on the IN message in the getRequestBody method as its accessed on-demand
                        String actualUrl = exchange.getIn().getHeader(Exchange.HTTP_URL, String.class);
                        int code = response.getStatus() != null ? response.getStatus().code() : -1;
                        log.debug("Http responseCode: {}", code);

                        // if there was a http error code (300 or higher) then check if we should throw an exception
                        if (code >= 300 && getConfiguration().isThrowExceptionOnFailure()) {
                            // operation failed so populate exception to throw
                            Exception cause = NettyHttpHelper.populateNettyHttpOperationFailedException(exchange, actualUrl, response, code, getConfiguration().isTransferException());
                            exchange.setException(cause);
                        }
                    }
                }
            } finally {
                // ensure we call the delegated callback
                callback.done(doneSync);
            }
        }
    }
}
