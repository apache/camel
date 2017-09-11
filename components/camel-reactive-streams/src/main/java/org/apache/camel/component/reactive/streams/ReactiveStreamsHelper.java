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
package org.apache.camel.component.reactive.streams;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsServiceFactory;
import org.apache.camel.component.reactive.streams.api.DispatchCallback;
import org.apache.camel.component.reactive.streams.engine.ReactiveStreamsEngineConfiguration;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;

public final class ReactiveStreamsHelper {
    private ReactiveStreamsHelper() {
    }

    public static DispatchCallback<Exchange> getCallback(Exchange exchange) {
        return exchange.getIn().getHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_CALLBACK, DispatchCallback.class);
    }

    public static DispatchCallback<Exchange> attachCallback(Exchange exchange, DispatchCallback<Exchange> callback) {
        exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_CALLBACK, callback);

        return callback;
    }

    public static DispatchCallback<Exchange> detachCallback(Exchange exchange) {
        DispatchCallback<Exchange> callback = getCallback(exchange);
        if (callback != null) {
            exchange.getIn().removeHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_CALLBACK);
        }

        return callback;
    }

    public static boolean invokeDispatchCallback(Exchange exchange) {
        return invokeDispatchCallback(exchange, null);
    }

    public static boolean invokeDispatchCallback(Exchange exchange, Throwable error) {
        DispatchCallback<Exchange> callback = getCallback(exchange);
        if (callback != null) {
            callback.processed(exchange, error);
            return true;
        }

        return false;
    }

    public static Exchange convertToExchange(CamelContext context, Object data) {
        Exchange exchange;
        if (data instanceof Exchange) {
            exchange = (Exchange) data;
        } else {
            exchange = new DefaultExchange(context);
            exchange.setPattern(ExchangePattern.InOut);
            exchange.getIn().setBody(data);
        }

        return exchange;
    }

    public static <T> T findInstance(CamelContext context, String name, Class<T> type) {
        return ObjectHelper.isEmpty(name)
            ? CamelContextHelper.findByType(context, type)
            : CamelContextHelper.lookup(context, name, type);
    }

    /**
     * Helper to lookup/create an instance of {@link CamelReactiveStreamsService}
     */
    public static CamelReactiveStreamsService resolveReactiveStreamsService(CamelContext context, String serviceType, ReactiveStreamsEngineConfiguration configuration) {
        // First try to find out if a service has already been bound to the registry
        CamelReactiveStreamsService service = ReactiveStreamsHelper.findInstance(context, serviceType, CamelReactiveStreamsService.class);

        if (service != null) {
            // If the service is bound to the registry we assume it is already
            // configured so let's return it as it is.
            return service;
        } else {
            // Then try to find out if a service factory is bound to the registry
            CamelReactiveStreamsServiceFactory factory = ReactiveStreamsHelper.findInstance(context, serviceType, CamelReactiveStreamsServiceFactory.class);

            if (factory == null) {
                // Try to find out a service factory with service loader style
                // using the provided service with fallback to default one
                factory = resolveServiceFactory(context, serviceType != null ? serviceType : ReactiveStreamsConstants.DEFAULT_SERVICE_NAME);
            }

            return factory.newInstance(context, configuration);
        }
    }

    @SuppressWarnings("unchecked")
    public static CamelReactiveStreamsServiceFactory resolveServiceFactory(CamelContext context, String serviceType) {
        try {
            FactoryFinder finder = context.getFactoryFinder(ReactiveStreamsConstants.SERVICE_PATH);
            Class<?> serviceClass = finder.findClass(serviceType);

            return (CamelReactiveStreamsServiceFactory)context.getInjector().newInstance(serviceClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class referenced in '" + ReactiveStreamsConstants.SERVICE_PATH + serviceType + "' not found", e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create the reactive stream service defined in '" + ReactiveStreamsConstants.SERVICE_PATH + serviceType + "'", e);
        }
    }
}
