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
package org.apache.camel.component.guava.eventbus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guava EventBus (http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/eventbus/EventBus.html)
 * consumer reading messages from the bus and forwarding them to the Camel routes.
 */
public class GuavaEventBusConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(GuavaEventBusConsumer.class);

    private final EventBus eventBus;
    private final Object eventHandler;

    public GuavaEventBusConsumer(GuavaEventBusEndpoint endpoint, Processor processor, EventBus eventBus, Class<?> eventClass, Class<?> listenerInterface) {
        super(endpoint, processor);

        if (eventClass != null && listenerInterface != null) {
            throw new IllegalStateException("You cannot set both 'eventClass' and 'listenerInterface' parameters.");
        }

        this.eventBus = eventBus;
        if (listenerInterface != null) {
            this.eventHandler = createListenerInterfaceProxy(endpoint, processor, listenerInterface);
        } else {
            this.eventHandler = new FilteringCamelEventHandler(endpoint, processor, eventClass);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Registering event handler: {} to EventBus: {}", eventHandler, eventBus);
        eventBus.register(eventHandler);
    }

    @Override
    protected void doStop() throws Exception {
        LOG.debug("Unregistering event handler: {} from EventBus: {}", eventHandler, eventBus);
        eventBus.unregister(eventHandler);
        super.doStop();
    }

    private Object createListenerInterfaceProxy(GuavaEventBusEndpoint endpoint, Processor processor, Class<?> listenerInterface) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return Proxy.newProxyInstance(classLoader, new Class[]{listenerInterface}, new ListenerInterfaceHandler(endpoint, processor));
    }

    private static final class ListenerInterfaceHandler implements InvocationHandler {

        private static final Logger LOG = LoggerFactory.getLogger(ListenerInterfaceHandler.class);

        private final CamelEventHandler delegateHandler;

        private ListenerInterfaceHandler(GuavaEventBusEndpoint endpoint, Processor processor) {
            this.delegateHandler = new CamelEventHandler(endpoint, processor);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getAnnotation(Subscribe.class) != null) {
                delegateHandler.doEventReceived(args[0]);
            } else {
                LOG.warn("Non @Subscribe method {} called on ListenerInterface proxy.", method);
            }
            return null;
        }

    }

}

