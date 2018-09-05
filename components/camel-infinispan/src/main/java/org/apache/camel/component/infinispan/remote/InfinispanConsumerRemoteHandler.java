/**
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

import org.apache.camel.component.infinispan.InfinispanConfiguration;
import org.apache.camel.component.infinispan.InfinispanConsumer;
import org.apache.camel.component.infinispan.InfinispanConsumerHandler;
import org.apache.camel.component.infinispan.InfinispanEventListener;
import org.apache.camel.component.infinispan.InfinispanUtil;
import org.infinispan.client.hotrod.RemoteCache;

public final class InfinispanConsumerRemoteHandler implements InfinispanConsumerHandler {
    public static final InfinispanConsumerHandler INSTANCE = new InfinispanConsumerRemoteHandler();

    private InfinispanConsumerRemoteHandler() {
    }

    @Override
    public InfinispanEventListener start(InfinispanConsumer consumer) {
        if (consumer.getConfiguration().isSync()) {
            throw new UnsupportedOperationException("Sync listeners not supported for remote caches.");
        }
        RemoteCache<?, ?> remoteCache = InfinispanUtil.asRemote(consumer.getCache());
        InfinispanConfiguration configuration = consumer.getConfiguration();
        InfinispanEventListener listener;
        if (configuration.hasCustomListener()) {
            listener = configuration.getCustomListener();
            listener.setInfinispanConsumer(consumer);
        } else {
            listener = new InfinispanRemoteEventListener(consumer, configuration.getEventTypes());
        }
        remoteCache.addClientListener(listener);
        listener.setCacheName(remoteCache.getName());
        return listener;
    }

    @Override
    public void stop(InfinispanConsumer consumer) {
        InfinispanUtil.asRemote(consumer.getCache()).removeClientListener(consumer.getListener());
    }

}
