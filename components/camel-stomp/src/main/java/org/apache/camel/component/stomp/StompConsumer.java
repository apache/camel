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
package org.apache.camel.component.stomp;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.fusesource.hawtbuf.AsciiBuffer;

public class StompConsumer extends DefaultConsumer {

    AsciiBuffer id;

    public StompConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        id = getEndpoint().getNextId();
    }

    @Override
    public StompEndpoint getEndpoint() {
        return (StompEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        getEndpoint().addConsumer(this);
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().removeConsumer(this);
        super.doStop();
    }

    void processExchange(Exchange exchange) {
        try {
            exchange.getIn().getHeaders().entrySet().removeIf(e -> getEndpoint().getHeaderFilterStrategy()
                    .applyFilterToExternalHeaders(e.getKey(), e.getValue(), exchange));
            getProcessor().process(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
        }

        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange.", exchange, exchange.getException());
        }
    }
}
