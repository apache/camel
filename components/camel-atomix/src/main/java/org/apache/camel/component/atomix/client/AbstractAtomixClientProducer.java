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
package org.apache.camel.component.atomix.client;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.atomix.resource.Resource;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.AtomixAsyncMessageProcessor;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_ACTION_HAS_RESULT;
import static org.apache.camel.component.atomix.client.AtomixClientConstants.RESOURCE_NAME;
import static org.apache.camel.support.ObjectHelper.invokeMethodSafe;

public abstract class AbstractAtomixClientProducer<E extends AbstractAtomixClientEndpoint, R extends Resource> extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractAtomixClientProducer.class);

    private final Map<String, AtomixAsyncMessageProcessor> processors;
    private ConcurrentMap<String, R> resources;

    protected AbstractAtomixClientProducer(E endpoint) {
        super(endpoint);

        this.processors = new HashMap<>();
        this.resources = new ConcurrentHashMap<>();
    }

    @Override
    protected void doStart() throws Exception {
        for (final Method method : getClass().getDeclaredMethods()) {
            InvokeOnHeader[] annotations = method.getAnnotationsByType(InvokeOnHeader.class);
            if (annotations != null && annotations.length > 0) {
                for (InvokeOnHeader annotation : annotations) {
                    bind(annotation, method);
                }
            }
        }

        super.doStart();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        final Message message = exchange.getIn();
        final String key = getProcessorKey(message);

        AtomixAsyncMessageProcessor processor = this.processors.get(key);
        if (processor != null) {
            try {
                return processor.process(message, callback);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        } else {
            throw new RuntimeCamelException("No handler for action " + key);
        }
    }

    // **********************************
    //
    // **********************************

    @SuppressWarnings("unchecked")
    protected E getAtomixEndpoint() {
        return (E)super.getEndpoint();
    }

    protected void processResult(Message message, AsyncCallback callback, Object result) {
        if (result != null && !(result instanceof Void)) {
            message.setHeader(RESOURCE_ACTION_HAS_RESULT, true);

            String resultHeader = getAtomixEndpoint().getConfiguration().getResultHeader();
            if (resultHeader != null) {
                message.setHeader(resultHeader, result);
            } else {
                message.setBody(result);
            }
        } else {
            message.setHeader(RESOURCE_ACTION_HAS_RESULT, false);
        }

        callback.done(false);
    }

    protected R getResource(Message message) {
        String resourceName = getResourceName(message);

        ObjectHelper.notNull(resourceName, RESOURCE_NAME);

        return resources.computeIfAbsent(resourceName, name -> createResource(name));
    }

    protected abstract String getProcessorKey(Message message);

    protected abstract String getResourceName(Message message);

    protected abstract R createResource(String name);

    // ************************************
    // Binding helpers
    // ************************************

    private void bind(InvokeOnHeader annotation, final Method method) {
        if (method.getParameterCount() == 2) {

            if (!Message.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("First argument should be of type Message");
            }
            if (!AsyncCallback.class.isAssignableFrom(method.getParameterTypes()[1])) {
                throw new IllegalArgumentException("Second argument should be of type AsyncCallback");
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("bind key={}, class={}, method={}",
                        annotation.value(), this.getClass(), method.getName());
            }

            this.processors.put(annotation.value(), (m, c) -> (boolean)invokeMethodSafe(method, this, m, c));
        } else {
            throw new IllegalArgumentException(
                "Illegal number of parameters for method: " + method.getName() + ", required: 2, found: " + method.getParameterCount()
            );
        }
    }
}

