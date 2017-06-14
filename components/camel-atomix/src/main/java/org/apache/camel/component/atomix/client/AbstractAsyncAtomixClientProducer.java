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
package org.apache.camel.component.atomix.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.atomix.AtomixAsyncMessageProcessor;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.AsyncProcessorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAsyncAtomixClientProducer<E extends AbstractAtomixClientEndpoint> extends DefaultProducer implements AsyncProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAsyncAtomixClientProducer.class);
    private final Map<AtomixClientAction, AtomixAsyncMessageProcessor> processors;

    protected AbstractAsyncAtomixClientProducer(E endpoint) {
        super(endpoint);

        this.processors = new HashMap<>();
    }

    @Override
    protected void doStart() throws Exception {
        for (final Method method : getClass().getDeclaredMethods()) {
            AsyncInvokeOnHeaders annotations = method.getAnnotation(AsyncInvokeOnHeaders.class);
            if (annotations != null) {
                for (AsyncInvokeOnHeader annotation : annotations.value()) {
                    bind(annotation, method);
                }
            } else {
                AsyncInvokeOnHeader annotation = method.getAnnotation(AsyncInvokeOnHeader.class);
                if (annotation != null) {
                    bind(annotation, method);
                }
            }
        }

        super.doStart();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        final Message message = exchange.getIn();
        final AtomixClientAction action = getAction(message);

        AtomixAsyncMessageProcessor processor = this.processors.get(action);
        if (processor != null) {
            try {
                return processor.process(message, callback);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        } else {
            throw new RuntimeCamelException("No handler for action " + action);
        }
    }

    @SuppressWarnings("unchecked")
    protected E getAtomixEndpoint() {
        return (E)super.getEndpoint();
    }

    protected abstract AtomixClientAction getAction(Message message);

    // ************************************
    // Binding helpers
    // ************************************

    private void bind(AsyncInvokeOnHeader annotation, final Method method) {
        if (method.getParameterCount() == 2) {
            method.setAccessible(true);

            if (!Message.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("First argument should be of type Message");
            }
            if (!AsyncCallback.class.isAssignableFrom(method.getParameterTypes()[1])) {
                throw new IllegalArgumentException("Second argument should be of type AsyncCallback");
            }

            LOGGER.debug("bind key={}, class={}, method={}",
                annotation.value(), this.getClass(), method.getName());

            this.processors.put(annotation.value(), (m, c) -> (boolean)method.invoke(this, m, c));
        } else {
            throw new IllegalArgumentException(
                "Illegal number of parameters for method: " + method.getName() + ", required: 2, found: " + method.getParameterCount()
            );
        }
    }

    // ************************************
    // Annotations
    // ************************************

    @Repeatable(AsyncInvokeOnHeaders.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AsyncInvokeOnHeader {
        AtomixClientAction value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AsyncInvokeOnHeaders {
        AsyncInvokeOnHeader[] value();
    }
}

