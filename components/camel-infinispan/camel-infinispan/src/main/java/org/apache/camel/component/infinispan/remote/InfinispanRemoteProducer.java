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

import org.apache.camel.Message;
import org.apache.camel.component.infinispan.InfinispanEndpoint;
import org.apache.camel.component.infinispan.InfinispanProducer;
import org.apache.camel.spi.InvokeOnHeader;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.query.dsl.Query;

public class InfinispanRemoteProducer extends InfinispanProducer<InfinispanRemoteManager, InfinispanRemoteConfiguration> {
    public InfinispanRemoteProducer(
                                    InfinispanEndpoint endpoint,
                                    String cacheName,
                                    InfinispanRemoteManager manager,
                                    InfinispanRemoteConfiguration configuration) {

        super(endpoint, cacheName, manager, configuration);
    }

    // ************************************
    // Operations
    // ************************************

    @SuppressWarnings("unchecked")
    @InvokeOnHeader("STATS")
    public void onStats(Message message) {
        final RemoteCache<Object, Object> cache = getManager().getCache(message, getCacheName(), RemoteCache.class);
        final Object result = cache.serverStatistics();

        setResult(message, result);
    }

    @SuppressWarnings("unchecked")
    @InvokeOnHeader("QUERY")
    public void onQuery(Message message) {
        final RemoteCache<Object, Object> cache = getManager().getCache(message, getCacheName(), RemoteCache.class);
        final Query<?> query = InfinispanRemoteUtil.buildQuery(getConfiguration(), cache, message);

        if (query != null) {
            setResult(message, query.execute().list());
        }
    }
}
