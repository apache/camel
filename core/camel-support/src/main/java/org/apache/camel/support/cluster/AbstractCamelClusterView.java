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
package org.apache.camel.support.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.concurrent.LockHelper;

public abstract class AbstractCamelClusterView extends ServiceSupport implements CamelClusterView {
    private final CamelClusterService clusterService;
    private final String namespace;
    private final List<CamelClusterEventListener> listeners;
    private final StampedLock lock;
    private CamelContext camelContext;

    protected AbstractCamelClusterView(CamelClusterService cluster, String namespace) {
        this.clusterService = cluster;
        this.namespace = namespace;
        this.listeners = new ArrayList<>();
        this.lock = new StampedLock();
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
    public CamelClusterService getClusterService() {
        return this.clusterService;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public void addEventListener(CamelClusterEventListener listener) {
        if (listener == null) {
            return;
        }

        LockHelper.doWithWriteLock(
            lock,
            () -> {
                listeners.add(listener);

                if (isRunAllowed()) {
                    // if the view has already been started, fire known events so
                    // the consumer can catch up.

                    if (CamelClusterEventListener.Leadership.class.isInstance(listener)) {
                        CamelClusterEventListener.Leadership.class.cast(listener).leadershipChanged(this, getLeader());
                    }

                    if (CamelClusterEventListener.Membership.class.isInstance(listener)) {
                        CamelClusterEventListener.Membership ml = CamelClusterEventListener.Membership.class.cast(listener);

                        for (CamelClusterMember member: getMembers()) {
                            ml.memberAdded(this, member);
                        }
                    }
                }
            }
        );
    }

    @Override
    public void removeEventListener(CamelClusterEventListener listener) {
        if (listener == null) {
            return;
        }

        LockHelper.doWithWriteLock(lock, () -> listeners.removeIf(l -> l == listener));
    }

    // **************************************
    // Events
    // **************************************

    private <T extends CamelClusterEventListener> void doWithListener(Class<T> type, Consumer<T> consumer) {
        LockHelper.doWithReadLock(
            lock,
            () -> {
                for (int i = 0; i < listeners.size(); i++) {
                    CamelClusterEventListener listener = listeners.get(i);

                    if (type.isInstance(listener)) {
                        consumer.accept(type.cast(listener));
                    }
                }
            }
        );
    }

    protected void fireLeadershipChangedEvent(Optional<CamelClusterMember> leader) {
        doWithListener(
            CamelClusterEventListener.Leadership.class,
            listener -> listener.leadershipChanged(this, leader)
        );
    }

    protected void fireMemberAddedEvent(CamelClusterMember member) {
        doWithListener(
            CamelClusterEventListener.Membership.class,
            listener -> listener.memberAdded(this, member)
        );
    }

    protected void fireMemberRemovedEvent(CamelClusterMember member) {
        doWithListener(
            CamelClusterEventListener.Membership.class,
            listener -> listener.memberRemoved(this, member)
        );
    }
}
