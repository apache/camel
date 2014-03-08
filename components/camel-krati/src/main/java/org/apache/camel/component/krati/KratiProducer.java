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
package org.apache.camel.component.krati;

import krati.store.DataStore;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Krati producer.
 */
public class KratiProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(KratiProducer.class);

    protected final KratiEndpoint endpoint;
    protected final DataStore<Object, Object> dataStore;

    public KratiProducer(KratiEndpoint endpoint, DataStore<Object, Object> dataStore) {
        super(endpoint);
        this.endpoint = endpoint;
        this.dataStore = dataStore;
    }

    public void process(Exchange exchange) throws Exception {
        String operation = getOperation(exchange);
        Object key = getKey(exchange);

        LOG.trace("Processing {} operation on '[{}]'", operation, exchange);
        if (KratiConstants.KRATI_OPERATION_GET.equals(operation) && key != null) {
            // preserve headers and attachments
            exchange.getOut().setHeaders(exchange.getIn().getHeaders());
            exchange.getOut().setAttachments(exchange.getIn().getAttachments());
            exchange.getOut().setBody(dataStore.get(key));
        } else if (KratiConstants.KRATI_OPERATION_DELETE.equals(operation) && key != null) {
            boolean status;
            synchronized (dataStore) {
                status = dataStore.delete(key);
                dataStore.persist();
            }
            if (status) {
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                exchange.getOut().setAttachments(exchange.getIn().getAttachments());
                exchange.getOut().setHeader(KratiConstants.KRATI_OPERATION_STATUS, KratiConstants.KRATI_OPERATION_SUCESSFUL);
            } else {
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                exchange.getOut().setAttachments(exchange.getIn().getAttachments());
                exchange.getOut().setHeader(KratiConstants.KRATI_OPERATION_STATUS, KratiConstants.KRATI_OPERATION_FAILURE);
            }
        } else if (KratiConstants.KRATI_OPERATION_DELETEALL.equals(operation)) {
            try {
                dataStore.clear();
                exchange.getOut().setHeaders(exchange.getIn().getHeaders());
                exchange.getOut().setAttachments(exchange.getIn().getAttachments());
                exchange.getOut().setHeader(KratiConstants.KRATI_OPERATION_STATUS, KratiConstants.KRATI_OPERATION_SUCESSFUL);
            } catch (Exception e) {
                LOG.warn("Error clearing all entries from store", e);
                // This is not so good to ignore exceptions, the end user have not access the exception, and cannot use Camel error handling
                exchange.getOut().setHeader(KratiConstants.KRATI_OPERATION_STATUS, KratiConstants.KRATI_OPERATION_FAILURE);
            }
        } else {
            Object value = getValue(exchange);
            //Its required to have only one thread putting stuff there at any given time per store.
            synchronized (endpoint.getPath().intern()) {
                dataStore.put(key, value);
            }
        }
    }

    /**
     * Retrieves the operation from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    public String getOperation(Exchange exchange) {
        String operation = ((KratiEndpoint) getEndpoint()).getOperation();

        if (exchange.getIn().getHeader(KratiConstants.KRATI_OPERATION) != null) {
            operation = (String) exchange.getIn().getHeader(KratiConstants.KRATI_OPERATION);
        }
        return operation;
    }


    /**
     * Retrieves the key from the URI or from the exchange headers. The header will take precedence over the URI.
     */
    public Object getKey(Exchange exchange) {
        Object key = ((KratiEndpoint) getEndpoint()).getKey();

        if (exchange.getIn().getHeader(KratiConstants.KEY) != null) {
            key =  exchange.getIn().getHeader(KratiConstants.KEY);
        }
        return key;
    }

    /**
     * Retrieves the value from the URI or from the exchange headers/body. The header/body will take precedence over the URI.
     */
    public Object getValue(Exchange exchange) {
        Object value = ((KratiEndpoint) getEndpoint()).getValue();

        if (exchange.getIn().getHeader(KratiConstants.VALUE) != null) {
            value = exchange.getIn().getHeader(KratiConstants.VALUE);
        }

        if (exchange.getIn().getBody() != null) {
            value = exchange.getIn().getBody();
        }
        return value;
    }

}
