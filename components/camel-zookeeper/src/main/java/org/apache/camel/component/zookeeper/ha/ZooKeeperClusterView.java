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
package org.apache.camel.component.zookeeper.ha;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.impl.ha.AbstractCamelClusterView;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.recipes.leader.Participant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ZooKeeperClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperClusterView.class);

    private final ZooKeeperCuratorConfiguration configuration;
    private final CuratorFramework client;
    private final CuratorLocalMember localMember;
    private LeaderSelector leaderSelector;

    public ZooKeeperClusterView(CamelClusterService cluster, ZooKeeperCuratorConfiguration configuration, CuratorFramework client, String namespace) {
        super(cluster, namespace);

        this.localMember = new CuratorLocalMember();
        this.configuration = configuration;
        this.client = client;
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return this.localMember;
    }

    @Override
    public Optional<CamelClusterMember> getMaster() {
        if (leaderSelector == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new CuratorClusterMember(leaderSelector.getLeader()));
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        if (leaderSelector == null) {
            return Collections.emptyList();
        }

        try {
            return leaderSelector.getParticipants()
                .stream()
                .map(CuratorClusterMember::new)
                .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (leaderSelector == null) {
            leaderSelector = new LeaderSelector(client, configuration.getBasePath(), new CamelLeaderElectionListener());
            leaderSelector.setId(getClusterService().getId());
            leaderSelector.start();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (leaderSelector != null) {
            leaderSelector.interruptLeadership();
            leaderSelector.close();
            leaderSelector = null;
        }
    }

    class CamelLeaderElectionListener extends LeaderSelectorListenerAdapter {
        @Override
        public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
            localMember.setMaster(true);
            fireLeadershipChangedEvent(localMember);

            while (isRunAllowed()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    break;
                }
            }
            
            localMember.setMaster(false);
            getMaster().ifPresent(leader -> fireLeadershipChangedEvent(leader));
        }
    }

    // ***********************************************
    //
    // ***********************************************

    private final class CuratorLocalMember implements CamelClusterMember {
        private AtomicBoolean master = new AtomicBoolean(false);

        void setMaster(boolean master) {
            this.master.set(master);
        }

        @Override
        public boolean isMaster() {
            return master.get();
        }

        @Override
        public String getId() {
            return getClusterService().getId();
        }
    }

    private final class CuratorClusterMember implements CamelClusterMember {
        private final Participant participant;

        CuratorClusterMember(Participant participant) {
            this.participant = participant;
        }

        @Override
        public String getId() {
            return participant.getId();
        }

        @Override
        public boolean isMaster() {
            try {
                return leaderSelector.getLeader().equals(this.participant);
            } catch (Exception e) {
                LOGGER.debug("", e);
                return false;
            }
        }
    }
}
