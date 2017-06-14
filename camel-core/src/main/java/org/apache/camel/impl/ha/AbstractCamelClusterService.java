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
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.concurrent.LockHelper;

public abstract class AbstractCamelClusterService<T extends CamelClusterView> extends ServiceSupport implements CamelClusterService {
    private final Map<String, T> views;
    private final StampedLock lock;
    private String id;
    private CamelContext camelContext;

    protected AbstractCamelClusterService() {
        this(null, null);
    }

    protected AbstractCamelClusterService(String id) {
        this(id, null);
    }

    protected AbstractCamelClusterService(String id, CamelContext camelContext) {
        this.id = id;
        this.camelContext = camelContext;
        this.views = new HashMap<>();
        this.lock = new StampedLock();
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;

        LockHelper.doWithWriteLock(
            lock,
            () -> {
                for (T view : views.values()) {
                    view.setCamelContext(camelContext);
                }
            }
        );
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        LockHelper.doWithReadLockT(
            lock,
            () -> {
                for (T view : views.values()) {
                    view.start();
                }
            }
        );
    }

    @Override
    protected void doStop() throws Exception {
        LockHelper.doWithReadLockT(
            lock,
            () -> {
                for (T view : views.values()) {
                    view.stop();
                }
            }
        );
    }

    @Override
    public CamelClusterView getView(String namespace) throws Exception {
        return LockHelper.callWithWriteLock(
            lock,
            () -> {
                T view = views.get(namespace);

                if (view == null) {
                    view = createView(namespace);
                    view.setCamelContext(this.camelContext);

                    views.put(namespace, view);

                    if (AbstractCamelClusterService.this.isRunAllowed()) {
                        view.start();
                    }
                }

                return view;
            }
        );
    }

    // **********************************
    // Implementation
    // **********************************

    protected abstract T createView(String namespace) throws Exception;
}
