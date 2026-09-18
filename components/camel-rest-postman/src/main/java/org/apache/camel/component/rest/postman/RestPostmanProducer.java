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
package org.apache.camel.component.rest.postman;

import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProducer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.component.rest.postman.support.PostmanRequestBinding;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * Invokes a single request of a Postman collection, by delegating to a {@code rest} endpoint.
 * <p>
 * The message body and headers of the exchange are what actually go on the wire; the collection only supplies the
 * method, URL and any headers the message does not already carry.
 */
public class RestPostmanProducer extends DelegateAsyncProcessor implements AsyncProducer {

    private final Producer delegate;
    private final boolean removeHostHeader;
    private final PostmanRequestBinding binding;

    public RestPostmanProducer(Producer delegate, boolean removeHostHeader, PostmanRequestBinding binding) {
        super(delegate);
        this.delegate = delegate;
        this.removeHostHeader = removeHostHeader;
        this.binding = binding;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message message = exchange.getMessage();

        if (removeHostHeader) {
            // the target host is fixed by the collection or by configuration, so a Host header carried over from
            // some other HTTP input must not override it
            message.removeHeader("Host");
        }

        applyDefaults(message, binding);

        message.setHeader(RestPostmanConstants.REQUEST_ID, binding.id());
        message.setHeader(RestPostmanConstants.REQUEST_NAME, binding.item().getName());
        if (!binding.item().getFolderPath().isEmpty()) {
            message.setHeader(RestPostmanConstants.FOLDER_PATH, String.join("/", binding.item().getFolderPath()));
        }

        return super.process(exchange, callback);
    }

    /**
     * Applies the headers and path values declared in the collection, without overwriting anything the caller set.
     */
    static void applyDefaults(Message message, PostmanRequestBinding binding) {
        for (Map.Entry<String, String> header : binding.staticHeaders().entrySet()) {
            if (header.getValue() != null && message.getHeader(header.getKey()) == null) {
                message.setHeader(header.getKey(), header.getValue());
            }
        }
        for (Map.Entry<String, String> value : binding.defaultPathValues().entrySet()) {
            if (message.getHeader(value.getKey()) == null) {
                message.setHeader(value.getKey(), value.getValue());
            }
        }
    }

    @Override
    public Endpoint getEndpoint() {
        return delegate.getEndpoint();
    }

    @Override
    public boolean isSingleton() {
        return delegate.isSingleton();
    }
}
