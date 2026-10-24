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
package org.apache.camel.component.infinispan.remote;

import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.infinispan.InfinispanComponent;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanEndpoint;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.component.infinispan.InfinispanConstants.SCHEME_INFINISPAN;

/**
 * Read and write from/to Infinispan distributed key/value store and data grid.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = SCHEME_INFINISPAN, title = "Infinispan", syntax = "infinispan:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = InfinispanConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL, dependsOn = "customListener",
                                   description = "No body: the event type, the cache name and the key of the entry are in"
                                                 + " headers, and for the events of a continuous query (queryBuilder) the value"
                                                 + " in the CamelInfinispanEventData header"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The body is not used: the key, the value and the Map for PUTALL come from the"
                                                + " CamelInfinispanKey, CamelInfinispanValue and CamelInfinispanMap headers, or"
                                                + " the key and value options"),
           producerOut = @BodyType(value = Carrier.UNKNOWN, dependsOn = "resultHeader",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "PUTASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture"),
                                           @BodyType.When(option = "operation", value = "PUTALL", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "PUTALLASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture"),
                                           @BodyType.When(option = "operation", value = "PUTIFABSENTASYNC",
                                                          carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture"),
                                           @BodyType.When(option = "operation", value = "CONTAINSKEY", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "CONTAINSVALUE",
                                                          carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "REMOVEASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture"),
                                           @BodyType.When(option = "operation", value = "REPLACEASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture"),
                                           @BodyType.When(option = "operation", value = "SIZE", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Integer"),
                                           @BodyType.When(option = "operation", value = "CLEAR", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "operation", value = "CLEARASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture"),
                                           @BodyType.When(option = "operation", value = "QUERY",
                                                          carriers = { Carrier.LIST, Carrier.UNCHANGED }),
                                           @BodyType.When(option = "operation", value = "STATS", carriers = Carrier.POJO,
                                                          javaType = "org.infinispan.client.hotrod.ServerStatistics"),
                                           @BodyType.When(option = "operation", value = "COMPUTEASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture") },
                                   description = "The result of the operation, which the CamelInfinispanOperation header can"
                                                 + " set: the value for GET, the previous value for PUT, the default, which is"
                                                 + " null unless the FORCE_RETURN_VALUE flag is set, a Boolean for CONTAINSKEY,"
                                                 + " an Integer for SIZE, a List for QUERY and a CompletableFuture for the"
                                                 + " ASYNC operations; PUTALL and CLEAR keep the body. With resultHeader the"
                                                 + " result goes to that header and the body is kept"))
public class InfinispanRemoteEndpoint extends InfinispanEndpoint implements EndpointServiceLocation {

    @UriPath(description = "The name of the cache to use. Use current to use the existing cache name from the currently configured cached manager. Or use default for the default cache manager name.")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final InfinispanRemoteConfiguration configuration;

    private final InfinispanRemoteManager manager;

    public InfinispanRemoteEndpoint(String uri, String cacheName, InfinispanComponent component,
                                    InfinispanRemoteConfiguration configuration) {
        super(uri, component);

        this.cacheName = cacheName;
        this.configuration = configuration;
        this.manager = new InfinispanRemoteManager(component.getCamelContext(), configuration);
    }

    @Override
    public String getServiceUrl() {
        if (configuration.getHosts() != null) {
            return configuration.getHosts();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "infinispan";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (configuration.getUsername() != null) {
            return Map.of("username", configuration.getUsername());
        }
        return null;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(manager);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(manager);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new InfinispanRemoteProducer(this, cacheName, manager, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        InfinispanRemoteConsumer consumer = new InfinispanRemoteConsumer(this, processor, cacheName, manager, configuration);

        configureConsumer(consumer);
        return consumer;
    }

    public String getCacheName() {
        return cacheName;
    }

    public InfinispanRemoteConfiguration getConfiguration() {
        return configuration;
    }
}
