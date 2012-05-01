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
package org.apache.camel.component.avro;

import org.apache.avro.ipc.Callback;
import org.apache.avro.ipc.Requestor;
import org.apache.avro.ipc.Transceiver;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ServicePoolAware;
import org.apache.camel.impl.DefaultAsyncProducer;

public abstract class AvroProducer extends DefaultAsyncProducer implements ServicePoolAware {

    Transceiver transceiver;
    Requestor requestor;

    public AvroProducer(Endpoint endpoint) {
        super(endpoint);
    }

    public abstract Transceiver createTransceiver() throws Exception;

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        Object request = exchange.getIn().getBody();

        if (transceiver == null) {
            try {
                transceiver = createTransceiver();
                requestor = new AvroRequestor(getEndpoint().getProtocol(), transceiver);
            } catch (Exception e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        try {
            requestor.request(exchange.getIn().getHeader(AvroConstants.AVRO_MESSAGE_NAME, String.class), wrapObjectToArray(request), new Callback<Object>() {
                @Override
                public void handleResult(Object result) {
                    // got result from avro, so set it on the exchange and invoke the callback
                    try {
                        exchange.getOut().setBody(result);
                    } finally {
                        callback.done(false);
                    }
                }

                @Override
                public void handleError(Throwable error) {
                    // got error from avro, so set it on the exchange and invoke the callback
                    try {
                        exchange.setException(error);
                    } finally {
                        callback.done(false);
                    }
                }
            });
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // okay we continue routing asynchronously
        return false;
    }

    public Object[] wrapObjectToArray(Object object) {
        if (object instanceof Object[]) {
            return (Object[]) object;
        } else {
            Object[] wrapper = new Object[1];
            wrapper[0] = object;
            return wrapper;
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (transceiver != null) {
            transceiver.close();
        }
    }

    @Override
    public AvroEndpoint getEndpoint() {
        return (AvroEndpoint) super.getEndpoint();
    }
}
