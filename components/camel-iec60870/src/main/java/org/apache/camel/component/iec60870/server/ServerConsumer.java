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
package org.apache.camel.component.iec60870.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.iec60870.ObjectAddress;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultMessage;
import org.eclipse.neoscada.protocol.iec60870.server.data.model.WriteModel.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConsumer.class);

    private final ServerInstance server;
    private final ServerEndpoint endpoint;

    public ServerConsumer(final ServerEndpoint endpoint, final Processor processor, final ServerInstance server) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.server = server;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.server.setListener(this.endpoint.getAddress(), this::updateValue);
    }

    @Override
    protected void doStop() throws Exception {
        this.server.setListener(this.endpoint.getAddress(), null);
        super.doStop();
    }

    private CompletionStage<Void> updateValue(final Request<?> value) {
        try {
            // create exchange

            final Exchange exchange = getEndpoint().createExchange();
            exchange.setIn(mapMessage(value));

            // create new future

            final CompletableFuture<Void> result = new CompletableFuture<>();

            // process and map async callback to our future

            getAsyncProcessor().process(exchange, doneSync -> result.complete(null));

            // return future

            return result;

        } catch (final Exception e) {

            // we failed triggering the process

            LOG.debug("Failed to process message", e);

            // create a future

            final CompletableFuture<Void> result = new CompletableFuture<>();

            // complete it right away

            result.completeExceptionally(e);

            // return it

            return result;
        }
    }

    private Message mapMessage(final Request<?> request) {
        final DefaultMessage message = new DefaultMessage(this.endpoint.getCamelContext());

        message.setBody(request);

        message.setHeader("address", ObjectAddress.valueOf(request.getHeader().getAsduAddress(), request.getAddress()));
        message.setHeader("value", request.getValue());
        message.setHeader("informationObjectAddress", request.getAddress());
        message.setHeader("asduHeader", request.getHeader());
        message.setHeader("type", request.getType());
        message.setHeader("execute", request.isExecute());

        return message;
    }
}
