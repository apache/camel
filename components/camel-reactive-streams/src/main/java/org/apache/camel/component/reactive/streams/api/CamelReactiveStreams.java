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
package org.apache.camel.component.reactive.streams.api;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.engine.CamelReactiveStreamsServiceImpl;
import org.apache.camel.spi.FactoryFinder;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main entry-point for getting Camel streams associate to reactive-streams endpoints.
 *
 * It delegates main methods to an instance of {@link CamelReactiveStreamsService}. This component provides
 * a default implementation that can be overridden in a 'META-INF/services/reactive-streams/reactiveStreamsService' file.
 */
public final class CamelReactiveStreams {

    private static final Logger LOG = LoggerFactory.getLogger(CamelReactiveStreams.class);

    private static Map<CamelContext, CamelReactiveStreams> instances = new ConcurrentHashMap<>();

    private CamelReactiveStreamsService service;

    private CamelReactiveStreams(CamelReactiveStreamsService service) {
        this.service = service;
    }

    public static CamelReactiveStreams get(CamelContext context) {
        instances.computeIfAbsent(context, ctx -> {
            CamelReactiveStreamsService service = resolveReactiveStreamsService(context);
            try {
                ctx.addService(service, true, true);
            } catch (Exception ex) {
                throw new IllegalStateException("Cannot add the CamelReactiveStreamsService to the Camel context", ex);
            }
            return new CamelReactiveStreams(service);
        });

        return instances.get(context);
    }

    @SuppressWarnings("unchecked")
    private static CamelReactiveStreamsService resolveReactiveStreamsService(CamelContext context) {
        Class<? extends CamelReactiveStreamsService> serviceClass = null;
        try {
            FactoryFinder finder = context.getFactoryFinder("META-INF/services/reactive-streams/");
            LOG.trace("Using FactoryFinder: {}", finder);
            serviceClass = (Class<? extends CamelReactiveStreamsService>) finder.findClass("reactiveStreamsService");
        } catch (ClassNotFoundException e) {
            LOG.trace("'reactive.streams.service.class' not found", e);
        } catch (IOException e) {
            LOG.trace("No reactive stream service defined in 'META-INF/services/org/apache/camel/component/'", e);
        }

        CamelReactiveStreamsService service = null;
        if (serviceClass != null) {
            try {
                service = serviceClass.newInstance();
                LOG.info("Created reactive stream service from class: " + serviceClass.getName());
            } catch (Exception e) {
                LOG.debug("Unable to create a reactive stream service of class " + serviceClass.getName(), e);
            }
        }

        if (service == null) {
            LOG.info("Using default reactive stream service");
            service = new CamelReactiveStreamsServiceImpl();
        }

        return service;
    }

    /**
     * Allows retrieving the service responsible for binding camel routes to streams.
     *
     * @return the stream service
     */
    public CamelReactiveStreamsService getService() {
        return service;
    }

    /**
     * Returns the publisher associated to the given stream name.
     * A publisher can be used to push Camel exchanges to reactive-streams subscribers.
     *
     * @param name the stream name
     * @return the stream publisher
     */
    public Publisher<Exchange> getPublisher(String name) {
        Objects.requireNonNull(name, "name cannot be null");

        return service.getPublisher(name);
    }

    /**
     * Returns the publisher associated to the given stream name.
     * A publisher can be used to push Camel exchange to external reactive-streams subscribers.
     *
     * The publisher converts automatically exchanges to the given type.
     *
     * @param name the stream name
     * @param type the type of the emitted items
     * @param <T> the type of items emitted by the publisher
     * @return the publisher associated to the stream
     */
    public <T> Publisher<T> getPublisher(String name, Class<T> type) {
        Objects.requireNonNull(name, "name cannot be null");

        return service.getPublisher(name, type);
    }

    /**
     * Returns the subscriber associated to the given stream name.
     * A subscriber can be used to push items coming from external reactive-streams publishers to Camel routes.
     *
     * @param name the stream name
     * @return the subscriber associated with the stream
     */
    public Subscriber<Exchange> getSubscriber(String name) {
        Objects.requireNonNull(name, "name cannot be null");

        return service.getSubscriber(name);
    }

    /**
     * Returns the subscriber associated to the given stream name.
     * A subscriber can be used to push items coming from external reactive-streams publishers to Camel routes.
     *
     * The subscriber converts automatically items of the given type to exchanges before pushing them.
     *
     * @param name the stream name
     * @param type the publisher converts automatically exchanges to the given type.
     * @param <T> the type of items accepted by the subscriber
     * @return the subscriber associated with the stream
     */
    public <T> Subscriber<T> getSubscriber(String name, Class<T> type) {
        Objects.requireNonNull(name, "name cannot be null");

        return service.getSubscriber(name, type);
    }

}
