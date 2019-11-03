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
package org.apache.camel.component.nitrite;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.dizitart.no2.event.ChangeInfo;
import org.dizitart.no2.event.ChangeListener;
import org.dizitart.no2.event.ChangedItem;

/**
 * The Nitrite consumer.
 */
public class NitriteConsumer extends DefaultConsumer {
    private final NitriteEndpoint endpoint;
    private NitriteChangeListener changeListener;

    public NitriteConsumer(NitriteEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        changeListener = new NitriteChangeListener();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.getNitriteCollection().register(changeListener);
    }

    @Override
    protected void doStop() throws Exception {
        if (changeListener != null) {
            endpoint.getNitriteCollection().deregister(changeListener);
        }
        super.doStop();
    }

    private class NitriteChangeListener implements ChangeListener {

        @Override
        public void onChange(ChangeInfo changeInfo) {
            for (ChangedItem changedItem: changeInfo.getChangedItems()) {
                Exchange exchange = endpoint.createExchange();
                Message message = exchange.getMessage();
                message.setHeader(NitriteConstants.CHANGE_TIMESTAMP, changedItem.getChangeTimestamp());
                message.setHeader(NitriteConstants.CHANGE_TYPE, changedItem.getChangeType());
                message.setBody(changedItem.getDocument());

                try {
                    getProcessor().process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                } finally {
                    if (exchange.getException() != null) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                    }
                }
            }
        }
    }
}
