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
package org.apache.camel.component.stitch;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.stitch.client.models.StitchResponse;
import org.apache.camel.component.stitch.operations.StitchProducerOperations;
import org.apache.camel.support.DefaultAsyncProducer;

public class StitchProducer extends DefaultAsyncProducer {

    private StitchProducerOperations operations;

    public StitchProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        operations = new StitchProducerOperations(getEndpoint().getStitchClient(), getConfiguration());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return operations.sendEvents(exchange.getMessage(),
                    response -> setDataOnExchange(response, exchange), callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

    }

    @Override
    public StitchEndpoint getEndpoint() {
        return (StitchEndpoint) super.getEndpoint();
    }

    public StitchConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private void setDataOnExchange(final StitchResponse response, final Exchange exchange) {
        final Message message = exchange.getIn();

        // set response message
        message.setBody(response.getMessage());
        // set headers
        message.setHeader(StitchConstants.CODE, response.getHttpStatusCode());
        message.setHeader(StitchConstants.STATUS, response.getStatus());
        message.setHeader(StitchConstants.HEADERS, response.getHeaders());
    }
}
