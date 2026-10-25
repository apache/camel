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
package org.apache.camel.component.jcache;

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

/**
 * Perform caching operations against JSR107/JCache.
 */
@UriEndpoint(firstVersion = "2.17.0", scheme = "jcache", title = "JCache", syntax = "jcache:cacheName",
             category = { Category.CACHE, Category.CLUSTERING }, headersClass = JCacheConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.UNKNOWN,
                                   description = "The value of the entry, as it was put, with the key in the CamelJCacheKey"
                                                 + " header and the event (CREATED, UPDATED, REMOVED or EXPIRED) in"
                                                 + " CamelJCacheEventType; with oldValueRequired the previous value is in the"
                                                 + " CamelJCacheOldValue header. A REMOVED or EXPIRED event has the old value as"
                                                 + " body when the provider gives it (Hazelcast does with oldValueRequired),"
                                                 + " else no body"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The value to store for PUT, PUTIFABSENT, REPLACE, GETANDPUT and GETANDREPLACE,"
                                                + " as it is, and a Map of the entries for PUTALL; no body, or a body that is"
                                                + " not a Map for PUTALL, fails. A provider that stores by value serializes it:"
                                                + " a stream fails, so convert it to text or bytes first. The other actions do"
                                                + " not use the body"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "action", value = "GETALL", carriers = Carrier.MAP,
                                                          javaType = "java.util.Map"),
                                           @BodyType.When(option = "action", value = "PUT", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "PUTALL", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "PUTIFABSENT",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "REPLACE", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "REMOVE", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "REMOVEALL",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "CLEAR", carriers = Carrier.UNCHANGED) },
                                   description = "By the action, which the CamelJCacheAction header can set: the value of the"
                                                 + " key for GET, or no body when it is not in the cache; the previous value,"
                                                 + " or no body, for GETANDPUT, GETANDREPLACE and GETANDREMOVE; a Map of the"
                                                 + " keys found to their values for GETALL; the result of the EntryProcessor"
                                                 + " for INVOKE, a Map of the keys to their EntryProcessorResult with"
                                                 + " CamelJCacheKeys. The other actions keep the body; PUTIFABSENT, REPLACE and"
                                                 + " REMOVE put whether they changed the cache in the CamelJCacheResult header"))
public class JCacheEndpoint extends DefaultEndpoint {

    @UriPath(description = "The name of the cache")
    @Metadata(required = true)
    private final String cacheName;
    @UriParam
    private final JCacheConfiguration configuration;

    private volatile JCacheManager<Object, Object> cacheManager;

    public JCacheEndpoint(String uri, JCacheComponent component, JCacheConfiguration configuration) {
        super(uri, component);

        this.cacheName = configuration.getCacheName();
        this.configuration = configuration;
    }

    public JCacheConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new JCacheProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        JCacheConsumer consumer = new JCacheConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        cacheManager = JCacheHelper.createManager(getCamelContext(), configuration);
    }

    @Override
    protected void doStop() throws Exception {
        if (cacheManager != null) {
            cacheManager.close();
        }
    }

    JCacheManager getManager() {
        return cacheManager;
    }
}
