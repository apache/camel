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

import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanConsumer;
import org.apache.camel.component.infinispan.InfinispanConsumerHandler;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.infinispan.Cache;

public final class InfinispanConsumerEmbeddedHandler implements InfinispanConsumerHandler {
    public static final InfinispanConsumerHandler INSTANCE = new InfinispanConsumerEmbeddedHandler();

    private InfinispanConsumerEmbeddedHandler() {
    }

    @Override
    public InfinispanEventListener start(InfinispanConsumer consumer) {
        Cache<?, ?> embeddedCache = InfinispanUtil.asEmbedded(consumer.getCache());
        InfinispanConfiguration configuration = consumer.getConfiguration();
        InfinispanEventListener listener;
        if (configuration.hasCustomListener()) {
            listener = configuration.getCustomListener();
            listener.setInfinispanConsumer(consumer);
        } else if (configuration.isClusteredListener()) {
            if (configuration.isSync()) {
                listener = new InfinispanSyncClusteredEventListener(consumer, configuration.getEventTypes());
            } else {
                listener = new InfinispanAsyncClusteredEventListener(consumer, configuration.getEventTypes());
            }
        } else {
            if (configuration.isSync()) {
                listener = new InfinispanSyncLocalEventListener(consumer, configuration.getEventTypes());
            } else {
                listener = new InfinispanAsyncLocalEventListener(consumer, configuration.getEventTypes());
            }
        }
        embeddedCache.addListener(listener);
        return listener;
    }

    @Override
    public void stop(InfinispanConsumer consumer) {
        Cache<?, ?> embeddedCache = InfinispanUtil.asEmbedded(consumer.getCache());
        embeddedCache.removeListener(consumer.getListener());
    }
}
