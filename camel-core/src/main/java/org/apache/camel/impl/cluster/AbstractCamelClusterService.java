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
package org.apache.camel.impl.cluster;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

import org.apache.camel.CamelContext;
import org.apache.camel.Ordered;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ReferenceCount;
import org.apache.camel.util.concurrent.LockHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCamelClusterService<T extends CamelClusterView> extends ServiceSupport implements CamelClusterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCamelClusterService.class);

    private final Map<String, ViewHolder<T>> views;
    private final Map<String, Object> attributes;
    private final StampedLock lock;
    private int order;
    private String id;
    private CamelContext camelContext;

    protected AbstractCamelClusterService() {
        this(null, null);
    }

    protected AbstractCamelClusterService(String id) {
        this(id, null);
    }

    protected AbstractCamelClusterService(String id, CamelContext camelContext) {
        this.order = Ordered.LOWEST;
        this.id = id;
        this.camelContext = camelContext;
        this.views = new HashMap<>();
        this.lock = new StampedLock();
        this.attributes = new HashMap<>();
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
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
                for (ViewHolder<T> holder : views.values()) {
                    holder.get().setCamelContext(camelContext);
                }
            }
        );
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes.clear();
        this.attributes.putAll(attributes);
    }

    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    protected void doStart() throws Exception {
        LockHelper.doWithReadLockT(
            lock,
            () -> {
                for (ViewHolder<T> holder : views.values()) {
                    holder.get().start();
                }
            }
        );
    }

    @Override
    protected void doStop() throws Exception {
        LockHelper.doWithReadLockT(
            lock,
            () -> {
                for (ViewHolder<T> holder : views.values()) {
                    holder.get().stop();
                }
            }
        );
    }

    @Override
    public CamelClusterView getView(String namespace) throws Exception {
        return LockHelper.callWithWriteLock(
            lock,
            () -> {
                ViewHolder<T> holder = views.get(namespace);

                if (holder == null) {
                    T view = createView(namespace);
                    view.setCamelContext(this.camelContext);

                    holder = new ViewHolder<>(view);

                    views.put(namespace, holder);
                }

                // Add reference and eventually start the route.
                return holder.retain();
            }
        );
    }

    @Override
    public void releaseView(CamelClusterView view) throws Exception {
        LockHelper.doWithWriteLock(
            lock,
            () -> {
                ViewHolder<T> holder = views.get(view.getNamespace());

                if (holder != null) {
                    holder.release();
                }
            }
        );
    }

    @Override
    public Collection<String> getNamespaces() {
        return LockHelper.supplyWithReadLock(
            lock,
            () -> {
                // copy the key set so it is not modifiable and thread safe
                // thus a little inefficient.
                return new HashSet<>(views.keySet());
            }
        );
    }

    @Override
    public void startView(String namespace) throws Exception {
        LockHelper.doWithWriteLockT(
            lock,
            () -> {
                ViewHolder<T> holder = views.get(namespace);

                if (holder != null) {
                    LOGGER.info("Force start of view {}", namespace);
                    holder.startView();
                } else {
                    LOGGER.warn("Error forcing start of view {}: it does not exist", namespace);
                }
            }
        );
    }

    @Override
    public void stopView(String namespace) throws Exception {
        LockHelper.doWithWriteLockT(
            lock,
            () -> {
                ViewHolder<T> holder = views.get(namespace);

                if (holder != null) {
                    LOGGER.info("Force stop of view {}", namespace);
                    holder.stopView();
                } else {
                    LOGGER.warn("Error forcing stop of view {}: it does not exist", namespace);
                }
            }
        );
    }

    @Override
    public boolean isLeader(String namespace) {
        return LockHelper.supplyWithReadLock(
            lock,
            () -> {
                ViewHolder<T> holder = views.get(namespace);
                if (holder != null) {
                    CamelClusterMember member = holder.get().getLocalMember();
                    if (member != null) {
                        return member.isLeader();
                    }
                }

                return false;
            }
        );
    }

    // **********************************
    // Implementation
    // **********************************

    protected abstract T createView(String namespace) throws Exception;

    // **********************************
    // Helpers
    // **********************************

    private final class ViewHolder<V extends CamelClusterView> {
        private final V view;
        private final ReferenceCount count;

        ViewHolder(V view) {
            this.view = view;
            this.count = ReferenceCount.on(
                () -> {
                    try {
                        this.startView();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> {
                    try {
                        this.stopView();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        V get() {
            return view;
        }

        V retain() {
            LOGGER.debug("Retain view {}, old-refs={}", view.getNamespace(), count.get());

            count.retain();

            return get();
        }

        void release() {
            LOGGER.debug("Release view {}, old-refs={}", view.getNamespace(), count.get());

            count.release();
        }

        void startView() throws Exception {
            if (AbstractCamelClusterService.this.isRunAllowed()) {
                LOGGER.debug("Start view {}", view.getNamespace());
                view.start();
            } else {
                LOGGER.debug("Can't start view {} as cluster service is not running, view will be started on service start-up", view.getNamespace());
            }
        }

        void stopView() throws Exception {
            LOGGER.debug("Stop view {}", view.getNamespace());
            view.stop();
        }
    }
}
