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
package org.apache.camel.component.infinispan.embedded;

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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.infinispan.InfinispanConstants.SCHEME_EMBEDDED;

/**
 * Read and write from/to Infinispan distributed key/value store and data grid.
 */
@UriEndpoint(firstVersion = "2.13.0", scheme = SCHEME_EMBEDDED, title = "Infinispan Embedded",
             syntax = "infinispan-embedded:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = InfinispanConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL, dependsOn = "customListener",
                                   description = "No body: the event type, the cache name and the key of the entry are in"
                                                 + " headers, and the value, when the event has one, in the"
                                                 + " CamelInfinispanEventData header"),
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
                                                          javaType = "org.infinispan.stats.Stats"),
                                           @BodyType.When(option = "operation", value = "COMPUTEASYNC", carriers = Carrier.POJO,
                                                          javaType = "java.util.concurrent.CompletableFuture") },
                                   description = "The result of the operation, which the CamelInfinispanOperation header can"
                                                 + " set: the value for GET, or null when the key is not in the cache, the"
                                                 + " previous value for PUT, the default, a Boolean for CONTAINSKEY, an Integer"
                                                 + " for SIZE, a List for QUERY and a CompletableFuture for the ASYNC"
                                                 + " operations; PUTALL and CLEAR keep the body. With resultHeader the result"
                                                 + " goes to that header and the body is kept"))
public class InfinispanEmbeddedEndpoint extends InfinispanEndpoint {

    @UriPath(description = "The name of the cache to use. Use current to use the existing cache name from the currently configured cached manager. Or use default for the default cache manager name.")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final InfinispanEmbeddedConfiguration configuration;

    public InfinispanEmbeddedEndpoint(String uri, String cacheName, InfinispanComponent component,
                                      InfinispanEmbeddedConfiguration configuration) {
        super(uri, component);
        this.cacheName = cacheName;
        this.configuration = configuration;
    }

    @Override
    public InfinispanEmbeddedComponent getComponent() {
        return (InfinispanEmbeddedComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new InfinispanEmbeddedProducer(this, cacheName, getComponent().getManager(), configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        InfinispanEmbeddedConsumer consumer
                = new InfinispanEmbeddedConsumer(this, processor, cacheName, getComponent().getManager(), configuration);

        configureConsumer(consumer);
        return consumer;
    }

    public String getCacheName() {
        return cacheName;
    }

    public InfinispanEmbeddedConfiguration getConfiguration() {
        return configuration;
    }
}
