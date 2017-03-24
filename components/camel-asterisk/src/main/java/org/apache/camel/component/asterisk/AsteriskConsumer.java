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
package org.apache.camel.component.asterisk;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.event.ManagerEvent;

/**
 * The Asterisk consumer.
 */
public class AsteriskConsumer extends DefaultConsumer {
    private final AsteriskEndpoint endpoint;
    private final AsteriskConnection connection;
    private final ManagerEventListener listener;

    public AsteriskConsumer(AsteriskEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        this.endpoint = endpoint;
        this.connection = new AsteriskConnection(endpoint.getHostname(), endpoint.getUsername(), endpoint.getPassword());
        this.listener = new EventListener();
    }

    @Override
    protected void doStart() throws Exception {
        connection.connect();
        connection.addListener(listener);
        connection.login();

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        connection.removeListener(listener);
        connection.logoff();
    }

    // *******************************
    //
    // *******************************

    private final class EventListener implements ManagerEventListener {
        @Override
        public void onManagerEvent(ManagerEvent event) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(AsteriskConstants.EVENT_NAME, event.getClass().getSimpleName());
            exchange.getIn().setBody(event);

            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange.", exchange, e);
            }
        }
    }
}
