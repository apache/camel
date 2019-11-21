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
package org.apache.camel.component.infinispan;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.context.Flag;
import org.infinispan.manager.EmbeddedCacheManager;

public final class InfinispanUtil {
    private InfinispanUtil() {
    }

    public static boolean isEmbedded(BasicCacheContainer container) {
        try {
            return container instanceof EmbeddedCacheManager;
        } catch (Throwable e) {
            return false;
        }
    }

    public static <K, V> boolean isEmbedded(BasicCache<K, V> cache) {
        try {
            return cache instanceof Cache;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isRemote(BasicCacheContainer container) {
        try {
            return container instanceof RemoteCacheManager;
        } catch (Throwable e) {
            return false;
        }
    }

    public static RemoteCacheManager asRemote(BasicCacheContainer container) {
        return RemoteCacheManager.class.cast(container);
    }

    public static <K, V> boolean isRemote(BasicCache<K, V> cache) {
        try {
            return cache instanceof RemoteCache;
        } catch (Throwable e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Cache<K, V> asEmbedded(BasicCache<K, V> cache) {
        return Cache.class.cast(cache);
    }

    @SuppressWarnings("unchecked")
    public static <K, V>  RemoteCache<K, V> asRemote(BasicCache<K, V> cache) {
        return RemoteCache.class.cast(cache);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> AdvancedCache<K, V> asAdvanced(BasicCache<K, V> cache) {
        return Cache.class.cast(cache).getAdvancedCache();
    }

    public static <K, V> BasicCache<K, V> ignoreReturnValuesCache(BasicCache<K, V> cache) {
        if (isEmbedded(cache)) {
            return ((Cache<K, V>) cache).getAdvancedCache().withFlags(new Flag[]{Flag.IGNORE_RETURN_VALUES});
        } else {
            return cache;
        }
    }

    public static boolean isInHeaderEmpty(Exchange exchange, String header) {
        return isHeaderEmpty(exchange.getIn(), header);
    }

    public static boolean isHeaderEmpty(Message message, String header) {
        return ObjectHelper.isEmpty(message.getHeader(header));
    }

    public static Properties loadProperties(CamelContext camelContext, String uri) throws Exception {
        try (InputStream is = openInputStream(camelContext, uri)) {
            Properties properties = new Properties();
            properties.load(is);

            return properties;
        } catch (IOException e) {
        }

        throw new FileNotFoundException("Cannot find resource: " + uri);
    }

    public static InputStream openInputStream(CamelContext camelContext, String uri) throws Exception {
        if (camelContext != null) {
            uri = camelContext.resolvePropertyPlaceholders(uri);
            return ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, uri);
        }

        return Thread.currentThread().getContextClassLoader().getResourceAsStream(uri);
    }
}
