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
package org.apache.camel.component.netty.http;

import java.net.URI;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyProducer;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * HTTP based {@link NettyProducer}.
 */
public class NettyHttpProducer extends NettyProducer {

    private final String uriParameters;

    public NettyHttpProducer(NettyHttpEndpoint nettyEndpoint, NettyConfiguration configuration, String uriParameters) {
        super(nettyEndpoint, configuration);
        this.uriParameters = uriParameters;
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
        String uri = NettyHttpHelper.createURL(exchange, getEndpoint(), uriParameters);
        URI u = NettyHttpHelper.createURI(exchange, uri, getEndpoint());

        HttpRequest request = getEndpoint().getNettyHttpBinding().toNettyRequest(exchange.getIn(), u.toString(), getConfiguration());
        String actualUri = request.getUri();
        exchange.getIn().setHeader(Exchange.HTTP_URL, actualUri);

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
                    HttpResponse response = nettyMessage.getHttpResponse();
                    if (response != null) {
                        // the actual url is stored on the IN message in the getRequestBody method as its accessed on-demand
                        String actualUrl = exchange.getIn().getHeader(Exchange.HTTP_URL, String.class);
                        int code = response.getStatus() != null ? response.getStatus().getCode() : -1;
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
