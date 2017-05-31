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
package org.apache.camel.impl.ha;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.StampedLock;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.ha.CamelCluster;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.support.ServiceSupport;

public abstract class AbstractCamelClusterView extends ServiceSupport implements CamelClusterView {
    private final CamelCluster cluster;
    private final String namespace;
    private final List<FilteringConsumer> consumers;
    private final StampedLock lock;
    private CamelContext camelContext;

    protected AbstractCamelClusterView(CamelCluster cluster, String namespace) {
        this.cluster = cluster;
        this.namespace = namespace;
        this.consumers = new ArrayList<>();
        this.lock = new StampedLock();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return null;
    }

    @Override
    public CamelCluster getCluster() {
        return  this.cluster;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public void addEventListener(BiConsumer<Event, Object> consumer) {
        long stamp = lock.writeLock();

        try {
            consumers.add(new FilteringConsumer(e -> true, consumer));
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void addEventListener(Predicate<Event> predicate, BiConsumer<Event, Object> consumer) {
        long stamp = lock.writeLock();

        try {
            consumers.add(new FilteringConsumer(predicate, consumer));
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public void removeEventListener(BiConsumer<Event, Object> consumer) {
        long stamp = lock.writeLock();

        try {
            consumers.removeIf(c -> c.getConsumer().equals(consumer));
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // **************************************
    // Events
    // **************************************

    protected void fireEvent(CamelClusterView.Event event, Object payload) {
        long stamp = lock.readLock();

        try {
            for (int i = 0; i < consumers.size(); i++) {
                consumers.get(i).accept(event, payload);
            }
        } finally {
            lock.unlockRead(stamp);
        }
    }

    // **************************************
    // Helpers
    // **************************************

    private final class FilteringConsumer implements BiConsumer<Event, Object> {
        private final Predicate<Event> predicate;
        private final BiConsumer<Event, Object> consumer;

        FilteringConsumer(Predicate<Event> predicate,  BiConsumer<Event, Object> consumer) {
            this.predicate = predicate;
            this.consumer = consumer;
        }

        @Override
        public void accept(CamelClusterView.Event event, Object payload) {
            if (predicate.test(event)) {
                consumer.accept(event, payload);
            }
        }

        public BiConsumer<Event, Object> getConsumer() {
            return this.consumer;
        }
    }
}
