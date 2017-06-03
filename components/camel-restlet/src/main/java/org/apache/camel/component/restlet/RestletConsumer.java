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
package org.apache.camel.component.restlet;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.Uniform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Restlet consumer acts as a server to listen client requests.
 *
 * @version 
 */
public class RestletConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(RestletConsumer.class);
    private Restlet restlet;

    public RestletConsumer(Endpoint endpoint, Processor processor)
        throws Exception {
        super(endpoint, processor);
    }

    @Override
    public RestletEndpoint getEndpoint() {
        return (RestletEndpoint) super.getEndpoint();
    }

    protected Restlet createRestlet() {
        return new Restlet() {
            @Override
            public void handle(Request request, Response response) {
                // must call super according to restlet documentation
                super.handle(request, response);

                LOG.debug("Consumer restlet handle request method: {}", request.getMethod());

                Exchange exchange = null;
                try {
                    // we want to handle the UoW
                    exchange = getEndpoint().createExchange();
                    createUoW(exchange);

                    RestletBinding binding = getEndpoint().getRestletBinding();
                    binding.populateExchangeFromRestletRequest(request, response, exchange);

                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    }
                    binding.populateRestletResponseFromExchange(exchange, response);

                    // resetlet will call the callback when its done sending where it would be safe
                    // to call doneUoW
                    Uniform callback = newResponseUniform(exchange);
                    response.setOnError(callback);
                    response.setOnSent(callback);

                } catch (Throwable e) {
                    getExceptionHandler().handleException("Error processing request", exchange, e);
                    if (exchange != null) {
                        doneUoW(exchange);
                    }
                }
            }
        };
    }

    /**
     * Creates a new {@link org.restlet.Uniform} callback that restlet calls when its done sending the reply message.
     * <p/>
     * We use this to defer done on the exchange {@link org.apache.camel.spi.UnitOfWork} where resources is safe to be
     * cleaned up as part of the done process.
     */
    private Uniform newResponseUniform(final Exchange exchange) {
        return new Uniform() {
            @Override
            public void handle(Request request, Response response) {
                if (exchange != null) {
                    doneUoW(exchange);
                }
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        restlet = createRestlet();
        getEndpoint().connect(this);
        restlet.start();
    }

    @Override
    public void doStop() throws Exception {
        getEndpoint().disconnect(this);
        if (restlet != null) {
            restlet.stop();
        }
        super.doStop();
    }

    public Restlet getRestlet() {
        return restlet;
    }

}
