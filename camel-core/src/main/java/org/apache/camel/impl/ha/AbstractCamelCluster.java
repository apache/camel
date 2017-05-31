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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

import org.apache.camel.CamelContext;
import org.apache.camel.ha.CamelCluster;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.support.ServiceSupport;

public abstract class AbstractCamelCluster<T extends CamelClusterView> extends ServiceSupport implements CamelCluster {
    private final String id;
    private final Map<String, T> views;
    private final StampedLock lock;
    private CamelContext camelContext;

    protected AbstractCamelCluster(String id) {
        this(id, null);
    }

    protected AbstractCamelCluster(String id, CamelContext camelContext) {
        this.id = id;
        this.camelContext = camelContext;
        this.views = new HashMap<>();
        this.lock = new StampedLock();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        long stamp = lock.readLock();

        try {
            for (T view : views.values()) {
                view.start();
            }
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    protected void doStop() throws Exception {
        long stamp = lock.readLock();

        try {
            for (T view : views.values()) {
                view.stop();
            }
        } finally {
            lock.unlockRead(stamp);
        }
    }

    @Override
    public CamelClusterView createView(String namespace) throws Exception {
        long stamp = lock.writeLock();

        try {
            T view = views.get(namespace);

            if (view == null) {
                view = doCreateView(namespace);
                view.setCamelContext(this.camelContext);

                views.put(namespace, view);

                if (AbstractCamelCluster.this.isRunAllowed()) {
                    view.start();
                }
            }

            return view;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // **********************************
    // Implementation
    // **********************************

    protected abstract T doCreateView(String namespace) throws Exception;
}
