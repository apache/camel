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
package org.apache.camel.component.jgroups.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;

import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.impl.cluster.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;
import org.jgroups.JChannel;
import org.jgroups.blocks.locking.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class JGroupsLockClusterView extends AbstractCamelClusterView {

    private static final transient Logger LOG = LoggerFactory.getLogger(JGroupsLockClusterView.class);
    private final CamelClusterMember localMember = new JGropusLocalMember();
    private String jgroupsConfig;
    private String jgroupsClusterName;
    private String lockName;
    private JChannel channel;
    private LockService lockService;
    private Lock lock;
    private ScheduledExecutorService executor;
    private volatile boolean isMaster;

    protected JGroupsLockClusterView(CamelClusterService cluster, String namespace, String jgroupsConfig, String jgroupsClusterName) {
        super(cluster, namespace);
        lockName = namespace;
        this.jgroupsConfig = jgroupsConfig;
        this.jgroupsClusterName = jgroupsClusterName;
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        if (isMaster) {
            return Optional.of(localMember);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        return new ArrayList<CamelClusterMember>() {{ add(localMember); }};
    }

    @Override
    protected void doStart() throws Exception {
        if (lock != null) {
            lock.unlock();
            lock = null;
        }
        if (channel == null) {
            channel = new JChannel(jgroupsConfig);
            lockService = new LockService(channel);
        }
        channel.connect(jgroupsClusterName);
        lock = lockService.getLock(lockName);

        // Camel context should be set at this stage.
        final CamelContext context = ObjectHelper.notNull(getCamelContext(), "CamelContext");
        executor = context.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "JGroupsLockClusterView-" + getClusterService().getId() + "-" + lockName);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                LOG.info("Attempting to become master acquiring the lock for group: " + lockName + " in JGroups cluster" + jgroupsClusterName + " with configuration: " + jgroupsConfig);
                lock.lock();
                isMaster = true;
                fireLeadershipChangedEvent(Optional.ofNullable(localMember));
                LOG.info("Became master by acquiring the lock for group: " + lockName + " in JGroups cluster" + jgroupsClusterName + " with configuration: " + jgroupsConfig);
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        shutdownExecutor();
        isMaster = false;
        fireLeadershipChangedEvent(Optional.empty());
        clearLock();
        channel.disconnect();
    }

    @Override
    protected void doShutdown() throws Exception {
        shutdownExecutor();
        isMaster = false;
        fireLeadershipChangedEvent(Optional.empty());
        clearLock();
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    private void clearLock() {
        if (lock != null) {
            lock.unlock();
            lock = null;
        }
    }

    private void shutdownExecutor() {
        CamelContext context = getCamelContext();
        if (executor != null) {
            if (context != null) {
                context.getExecutorServiceManager().shutdown(executor);
            } else {
                executor.shutdown();
            }
            executor = null;
        }
    }

    private final class JGropusLocalMember implements CamelClusterMember {
        @Override
        public boolean isLeader() {
            return isMaster;
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return getClusterService().getId();
        }
    }
}
