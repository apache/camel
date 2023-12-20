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
package org.apache.camel.component.zookeeper.cluster;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.zookeeper.ZooKeeperCuratorConfiguration;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.task.BlockingTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.support.task.budget.IterationBoundedBudget;
import org.apache.camel.util.ObjectHelper;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.apache.curator.framework.recipes.leader.Participant;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ZooKeeperClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZooKeeperClusterView.class);

    private final ZooKeeperCuratorConfiguration configuration;
    private final CuratorFramework client;
    private final CuratorLocalMember localMember;
    private volatile LeaderSelector leaderSelector;

    public ZooKeeperClusterView(CamelClusterService cluster, ZooKeeperCuratorConfiguration configuration,
                                CuratorFramework client, String namespace) {
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
    public Optional<CamelClusterMember> getLeader() {
        if (leaderSelector == null || isStoppingOrStopped()) {
            return Optional.empty();
        }

        try {
            Participant participant = leaderSelector.getLeader();

            return ObjectHelper.equal(participant.getId(), localMember.getId())
                    ? Optional.of(localMember)
                    : Optional.of(new CuratorClusterMember(participant));
        } catch (KeeperException.NoNodeException e) {
            LOGGER.debug("Failed to get get master because node '{}' does not yet exist (error: '{}')",
                    getFullPath(),
                    e.getMessage());
            return Optional.empty();
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
        } catch (KeeperException.NoNodeException e) {
            LOGGER.debug("Failed to get members because node '{}' does not yet exist (error: '{}')",
                    getFullPath(),
                    e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (leaderSelector == null) {
            leaderSelector = new LeaderSelector(client, getFullPath(), new CamelLeaderElectionListener());
            leaderSelector.setId(getClusterService().getId());
            leaderSelector.start();
        } else {
            leaderSelector.requeue();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (leaderSelector != null) {
            leaderSelector.interruptLeadership();
            fireLeadershipChangedEvent(getLeader());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        if (leaderSelector != null) {
            leaderSelector.close();
        }
    }

    private String getFullPath() {
        return configuration.getBasePath() + "/" + getNamespace();
    }

    // ***********************************************
    //
    // ***********************************************

    private final class CamelLeaderElectionListener extends LeaderSelectorListenerAdapter {
        @Override
        public void takeLeadership(CuratorFramework curatorFramework) throws Exception {
            fireLeadershipChangedEvent(Optional.of(localMember));

            BlockingTask task = Tasks.foregroundTask().withBudget(Budgets.iterationBudget()
                    .withMaxIterations(IterationBoundedBudget.UNLIMITED_ITERATIONS)
                    .withInterval(Duration.ofSeconds(5))
                    .build())
                    .build();

            task.run(() -> !isRunAllowed());

            fireLeadershipChangedEvent(getLeader());
        }
    }

    private final class CuratorLocalMember implements CamelClusterMember {
        @Override
        public boolean isLeader() {
            return leaderSelector != null && leaderSelector.hasLeadership();
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
        public boolean isLocal() {
            return participant.getId() != null && ObjectHelper.equal(participant.getId(), localMember.getId());
        }

        @Override
        public boolean isLeader() {
            try {
                return leaderSelector.getLeader().equals(this.participant);
            } catch (Exception e) {
                LOGGER.debug("{}", e.getMessage(), e);
                return false;
            }
        }
    }
}
