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

import java.util.List;

import org.apache.camel.Message;
import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.apache.camel.component.infinispan.InfinispanQueryBuilder;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.query.Query;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.StoreConfiguration;
import org.infinispan.context.Flag;
import org.infinispan.remoting.transport.Address;

public final class InfinispanEmbeddedUtil extends InfinispanUtil {

    protected InfinispanEmbeddedUtil() {
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Cache<K, V> getCacheWithFlags(InfinispanEmbeddedManager manager, String cacheName, Flag... flags) {
        final Cache<K, V> cache = manager.getCache(cacheName, Cache.class);

        return flags == null || flags.length == 0 ? cache : cache.getAdvancedCache().withFlags(flags);
    }

    /**
     * Whether the cache keeps its entries only in the memory of this JVM, so they are lost when the application stops.
     * A cache store keeps them, unless it purges on startup. A replicated cache, or a distributed one with more than
     * one owner, keeps copies on the other nodes of the cluster, when there are any. A local cache without a store
     * loses them, which is the default.
     */
    public static boolean isInMemory(BasicCache<?, ?> cache) {
        if (!(cache instanceof Cache<?, ?> embedded)) {
            return false;
        }
        Configuration configuration = embedded.getCacheConfiguration();
        for (StoreConfiguration store : configuration.persistence().stores()) {
            if (!store.purgeOnStartup()) {
                return false;
            }
        }
        CacheMode mode = configuration.clustering().cacheMode();
        if (mode.isReplicated() || (mode.isDistributed() && configuration.clustering().hash().numOwners() > 1)) {
            List<Address> members = embedded.getCacheManager().getMembers();
            return members == null || members.size() <= 1;
        }
        return true;
    }

    public static Query<?> buildQuery(
            InfinispanConfiguration configuration, Cache<Object, Object> cache, Message message) {

        return buildQuery(resolveQueryBuilder(configuration, message), cache);
    }

    /**
     * The query builder to use for a message: the one carried by the {@link InfinispanConstants#QUERY_BUILDER} header
     * if there is one, the one configured on the endpoint otherwise. Returns {@code null} when neither is set.
     */
    public static InfinispanQueryBuilder resolveQueryBuilder(InfinispanConfiguration configuration, Message message) {
        InfinispanQueryBuilder builder = message.getHeader(InfinispanConstants.QUERY_BUILDER, InfinispanQueryBuilder.class);
        if (builder == null) {
            builder = configuration.getQueryBuilder();
        }

        return builder;
    }

    public static Query<?> buildQuery(InfinispanConfiguration configuration, Cache<Object, Object> cache) {
        return buildQuery(configuration.getQueryBuilder(), cache);
    }

    public static Query<?> buildQuery(InfinispanQueryBuilder queryBuilder, Cache<Object, Object> cache) {
        return queryBuilder != null ? queryBuilder.build(cache) : null;
    }
}
