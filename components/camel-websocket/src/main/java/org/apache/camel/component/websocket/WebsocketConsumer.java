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
package org.apache.camel.component.websocket;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

public class WebsocketConsumer extends DefaultConsumer {

    public WebsocketConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public void sendExchange(String connectionKey, String message) {
        Exchange exchange = this.getEndpoint().createExchange();

        // set header and body
        exchange.getIn().setHeader(WebsocketConstants.CONNECTION_KEY, connectionKey);
        exchange.getIn().setBody(message);

        // send exchange
        try {
            this.getProcessor().process(exchange);
        } catch (Exception e) {
            if (exchange.getException() != null) {
                this.getExceptionHandler().handleException(String.format("Error processing exchange for websocket consumer on message '%s'.", message), exchange, exchange.getException());
            }
        }
    }

}
