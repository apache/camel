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
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.Search;
import org.infinispan.query.dsl.Query;

public final class InfinispanRemoteUtil extends InfinispanUtil {
    protected InfinispanRemoteUtil() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> RemoteCache<K, V> getCacheWithFlags(InfinispanRemoteManager manager, String cacheName, Flag... flags) {
        final RemoteCache<K, V> cache = manager.getCache(cacheName, RemoteCache.class);

        return flags == null || flags.length == 0 ? cache : cache.withFlags(flags);
    }

    public static Query<?> buildQuery(
            InfinispanConfiguration configuration, RemoteCache<Object, Object> cache, Message message) {

        InfinispanQueryBuilder builder = message.getHeader(InfinispanConstants.QUERY_BUILDER, InfinispanQueryBuilder.class);
        if (builder == null) {
            builder = configuration.getQueryBuilder();
        }

        return buildQuery(builder, cache);
    }

    public static Query<?> buildQuery(InfinispanConfiguration configuration, RemoteCache<Object, Object> cache) {
        return buildQuery(configuration.getQueryBuilder(), cache);
    }

    public static Query<?> buildQuery(InfinispanQueryBuilder queryBuilder, RemoteCache<Object, Object> cache) {
        return queryBuilder != null ? queryBuilder.build(Search.getQueryFactory(cache)) : null;
    }
}
