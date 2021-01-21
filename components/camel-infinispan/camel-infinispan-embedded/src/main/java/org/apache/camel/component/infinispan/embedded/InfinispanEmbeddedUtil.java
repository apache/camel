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

import org.apache.camel.Message;
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.Query;

public final class InfinispanEmbeddedUtil extends InfinispanUtil {
    protected InfinispanEmbeddedUtil() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Cache<K, V> getCacheWithFlags(InfinispanEmbeddedManager manager, String cacheName, Flag... flags) {
        final Cache<K, V> cache = manager.getCache(cacheName, Cache.class);

        return flags == null || flags.length == 0 ? cache : cache.getAdvancedCache().withFlags(flags);
    }

    public static Query<?> buildQuery(
            InfinispanConfiguration configuration, Cache<Object, Object> cache, Message message) {

        InfinispanQueryBuilder builder = message.getHeader(InfinispanConstants.QUERY_BUILDER, InfinispanQueryBuilder.class);
        if (builder == null) {
            builder = configuration.getQueryBuilder();
        }

        return buildQuery(builder, cache);
    }

    public static Query<?> buildQuery(InfinispanConfiguration configuration, Cache<Object, Object> cache) {
        return buildQuery(configuration.getQueryBuilder(), cache);
    }

    public static Query<?> buildQuery(InfinispanQueryBuilder queryBuilder, Cache<Object, Object> cache) {
        return queryBuilder != null ? queryBuilder.build(Search.getQueryFactory(cache)) : null;
    }
}
