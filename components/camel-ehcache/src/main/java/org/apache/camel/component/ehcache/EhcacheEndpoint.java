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
package org.apache.camel.component.ehcache;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;

/**
 * Perform caching operations using <a href="http://www.ehcache.org">Ehcache</a>.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "ehcache", title = "Ehcache", syntax = "ehcache:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = EhcacheConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The new value of the entry, as it was put, with the key in the CamelEhcacheKey"
                                                 + " header, the event in CamelEhcacheEventType and the previous value in"
                                                 + " CamelEhcacheOldValue. A REMOVED, EXPIRED or EVICTED event has no body"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The value to store for PUT, PUT_IF_ABSENT and REPLACE, converted to the"
                                                + " valueType when set, and a Map of the entries for PUT_ALL, unless the"
                                                + " CamelEhcacheValue header gives it. The value is stored as it is: a stream"
                                                + " is read once, so convert it to text or bytes first. The other actions do"
                                                + " not use the body"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "action", value = "GET_ALL", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map"),
                                           @BodyType.When(option = "action", value = "PUT", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "PUT_ALL", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "PUT_IF_ABSENT",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "REPLACE", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "REMOVE", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "REMOVE_ALL",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "CLEAR", carriers = Carrier.UNCHANGED) },
                                   description = "By the action, which the CamelEhcacheAction header can set: the value of the"
                                                 + " key for GET, or the body as it was when the key is not in the cache, and"
                                                 + " for GET_ALL a Map of the keys in the CamelEhcacheKeys header to their"
                                                 + " values, null for a key not found. The other actions keep the body;"
                                                 + " PUT_IF_ABSENT and REPLACE put the previous value in the"
                                                 + " CamelEhcacheOldValue header"))
public class EhcacheEndpoint extends DefaultEndpoint {
    @UriPath(description = "the cache name")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final EhcacheConfiguration configuration;
    private EhcacheManager cacheManager;

    EhcacheEndpoint(String uri, EhcacheComponent component, String cacheName,
                    EhcacheConfiguration configuration) {
        super(uri, component);
        this.cacheName = cacheName;
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new EhcacheProducer(this, this.cacheName, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        EhcacheConsumer consumer = new EhcacheConsumer(this, this.cacheName, configuration, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public EhcacheComponent getComponent() {
        return (EhcacheComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        if (cacheManager == null) {
            cacheManager = getComponent().createCacheManager(configuration);
        }
        ServiceHelper.startService(cacheManager);
        cacheManager.incRef();
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(cacheManager);
        if (cacheManager != null) {
            cacheManager.decRef();
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(cacheManager);
    }

    EhcacheManager getManager() {
        return cacheManager;
    }

    EhcacheConfiguration getConfiguration() {
        return configuration;
    }

}
