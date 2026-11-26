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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.cluster.CamelPreemptiveClusterService;
import org.apache.camel.cluster.CamelPreemptiveClusterView;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link RebalancingCamelClusterService} adds rebalancing capabilities to an underlying
 * {@link CamelPreemptiveClusterService}. Each view is treated as a partition by this cluster service, and it makes sure
 * that all services belonging to the cluster own a balanced number of partitions (same number or difference at most 1
 * when not possible).
 */
public class RebalancingCamelClusterService extends ServiceSupport implements CamelPreemptiveClusterService {

    private static final Logger LOG = LoggerFactory.getLogger(RebalancingCamelClusterService.class);

    // set to null by doStop: a pass that runs on an executor that is no longer this one does not schedule the next pass
    protected volatile ScheduledExecutorService serializedExecutor;
    protected CamelPreemptiveClusterService delegate;
    protected CamelContext camelContext;
    protected final long periodMillis;

    // whether the previous pass failed (only used by the passes, one at a time)
    private boolean previousPassFailed;

    public RebalancingCamelClusterService(CamelPreemptiveClusterService delegate, long periodMillis) {
        this.delegate = ObjectHelper.notNull(delegate, "delegate");
        this.periodMillis = periodMillis;
    }

    public RebalancingCamelClusterService(CamelContext camelContext, CamelPreemptiveClusterService delegate,
                                          long periodMillis) {
        this.camelContext = ObjectHelper.notNull(camelContext, "camelContext");
        this.delegate = ObjectHelper.notNull(delegate, "delegate");
        this.periodMillis = periodMillis;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        ServiceHelper.initService(delegate);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);
        super.doStart();
        ServiceHelper.startService(delegate);
        if (serializedExecutor == null) {
            ScheduledExecutorService executor = getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "RebalancingClusterService");
            previousPassFailed = false;
            serializedExecutor = executor;
            executor.execute(() -> reconcileAndReschedule(executor));
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ScheduledExecutorService executor = serializedExecutor;
        serializedExecutor = null;
        if (executor != null) {
            // the pending pass is dropped (a scheduled executor runs its delayed tasks after a plain shutdown), so a
            // start does not add a second chain of passes
            getCamelContext().getExecutorServiceManager().shutdownNow(executor);
        }
        ServiceHelper.stopService(delegate);
    }

    public CamelPreemptiveClusterService getDelegate() {
        return delegate;
    }

    public long getPeriodMillis() {
        return periodMillis;
    }

    public void setDelegate(CamelPreemptiveClusterService delegate) {
        this.delegate = delegate;
    }

    /**
     * Runs a rebalancing pass, and schedules the next one on the same executor while it is the executor of this
     * service, also when the pass fails.
     */
    private void reconcileAndReschedule(ScheduledExecutorService executor) {
        try {
            reconcile();
            previousPassFailed = false;
        } catch (Throwable e) {
            if (previousPassFailed) {
                LOG.debug("Error while rebalancing the partitions (will try again in {} ms)", periodMillis, e);
            } else {
                LOG.warn("Error while rebalancing the partitions (will try again in {} ms): {}", periodMillis,
                        e.toString());
                LOG.debug("Exception thrown while rebalancing the partitions", e);
            }
            previousPassFailed = true;
        } finally {
            if (executor == serializedExecutor) {
                try {
                    executor.schedule(() -> reconcileAndReschedule(executor), periodMillis, TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException e) {
                    LOG.debug("Cannot schedule the next rebalancing pass as the service is stopping");
                }
            }
        }
    }

    protected void reconcile() {
        Integer n = members();
        List<String> partitions = partitionList();
        int k = partitions.size();

        if (n == null || n == 0 || k == 0) {
            return;
        }

        int threshold = 0;
        while (threshold <= k) {
            threshold += n;
        }
        threshold -= n;

        int quota = threshold / n;

        List<String> main = new ArrayList<>();
        List<String> remaining = new ArrayList<>();
        for (int i = 0; i < threshold; i++) {
            main.add(partitions.get(i));
        }
        for (int i = threshold; i < partitions.size(); i++) {
            remaining.add(partitions.get(i));
        }

        rebalanceGroup(main, quota);
        rebalanceGroup(remaining, 1);
    }

    protected void rebalanceGroup(List<String> partitions, int quota) {
        List<String> owned = owned(partitions);
        if (owned == null) {
            return;
        }

        if (owned.size() < quota) {
            // Open all (to let the controller choose which ones)
            for (String partition : partitions) {
                setDisabled(partition, false);
            }
        } else if (owned.size() > quota) {
            for (int i = 0; i < owned.size() - quota; i++) {
                setDisabled(owned.get(i), true);
            }
        } else {
            // We're fine, but we prevent this instance from stealing locks that are not needed
            Set<String> ownedSet = new HashSet<>(owned);
            for (String partition : partitions) {
                if (!ownedSet.contains(partition)) {
                    setDisabled(partition, true);
                }
            }
        }
    }

    protected void setDisabled(String partition, boolean disabled) {
        try {
            CamelPreemptiveClusterView view = retainViewInUse(partition);
            if (view == null) {
                LOG.debug("Partition {} is not in use", partition);
                return;
            }
            try {
                LOG.debug("Setting partition {} to disabled={}...", partition, disabled);
                if (view.isDisabled() != disabled) {
                    view.setDisabled(disabled);
                }
            } finally {
                releaseRetainedView(view);
            }
        } catch (Exception ex) {
            LOG.warn("Could not get view {}", partition, ex);
        }
    }

    protected List<String> owned(List<String> partitions) {
        List<String> owned = new ArrayList<>(partitions.size());
        for (String partition : partitions) {
            try {
                CamelPreemptiveClusterView view = retainViewInUse(partition);
                if (view == null) {
                    // released since the partitions were listed
                    continue;
                }
                try {
                    if (!view.isDisabled() && view.getLocalMember().isLeader()) {
                        owned.add(partition);
                    }
                } finally {
                    releaseRetainedView(view);
                }
            } catch (Exception ex) {
                LOG.warn("Could not get view {}", partition, ex);
                return null;
            }
        }
        return owned;
    }

    protected List<String> partitionList() {
        List<String> partitions = new ArrayList<>();
        for (String namespace : this.getNamespaces()) {
            // a view that no route (or other user) holds any more is not a partition to balance
            if (isViewInUse(namespace)) {
                partitions.add(namespace);
            }
        }
        Collections.sort(partitions);
        return partitions;
    }

    protected Integer members() {
        Set<String> members = null;
        for (String group : partitionList()) {
            try {
                CamelPreemptiveClusterView view = retainViewInUse(group);
                if (view == null) {
                    // released since the partitions were listed
                    continue;
                }
                try {
                    Set<String> viewMembers
                            = view.getMembers().stream().map(CamelClusterMember::getId).collect(Collectors.toSet());
                    if (members != null && !members.equals(viewMembers)) {
                        LOG.debug("View members don't match: {} vs {}", members, viewMembers);
                        return null;
                    }
                    members = viewMembers;
                } finally {
                    releaseRetainedView(view);
                }
            } catch (Exception ex) {
                LOG.warn("Could not get view {}", group, ex);
                return null;
            }
        }
        return members != null ? members.size() : 0;
    }

    /**
     * Whether the view of the partition is held by a route or another user. The rebalancing only looks at those views,
     * so it does not start a view again after its last user has released it.
     */
    private boolean isViewInUse(String partition) {
        if (delegate instanceof AbstractCamelClusterService<?> service) {
            return service.isViewInUse(partition);
        }
        return true;
    }

    /**
     * Takes a reference to the view of the partition if it is in use, or returns {@code null}. The caller releases it
     * with {@link #releaseRetainedView(CamelClusterView)}, so the rebalancing does not keep a view running after its
     * last user has released it. A delegate that is not an {@link AbstractCamelClusterService} has no such lookup, so
     * its views are taken with {@code getView} (which may start a released view until it is released again).
     */
    private CamelPreemptiveClusterView retainViewInUse(String partition) throws Exception {
        if (delegate instanceof AbstractCamelClusterService<?> service) {
            return (CamelPreemptiveClusterView) service.retainViewIfInUse(partition);
        }
        return delegate.getView(partition);
    }

    private void releaseRetainedView(CamelClusterView view) {
        try {
            delegate.releaseView(view);
        } catch (Exception ex) {
            LOG.warn("Could not release view {}", view.getNamespace(), ex);
        }
    }

    @Override
    public CamelPreemptiveClusterView getView(String namespace) throws Exception {
        return delegate.getView(namespace);
    }

    @Override
    public void releaseView(CamelClusterView view) throws Exception {
        delegate.releaseView(view);
    }

    @Override
    public Collection<String> getNamespaces() {
        return delegate.getNamespaces();
    }

    @Override
    public void startView(String namespace) throws Exception {
        delegate.startView(namespace);
    }

    @Override
    public void stopView(String namespace) throws Exception {
        delegate.stopView(namespace);
    }

    @Override
    public boolean isLeader(String namespace) {
        return delegate.isLeader(namespace);
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        delegate.setCamelContext(camelContext);
    }

    @Override
    public CamelContext getCamelContext() {
        return this.camelContext;
    }

    @Override
    public void setId(String id) {
        delegate.setId(id);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }
}
