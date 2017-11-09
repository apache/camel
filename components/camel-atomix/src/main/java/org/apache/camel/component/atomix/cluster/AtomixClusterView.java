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
package org.apache.camel.component.atomix.cluster;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import io.atomix.Atomix;
import io.atomix.group.DistributedGroup;
import io.atomix.group.GroupMember;
import io.atomix.group.LocalMember;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.atomix.AtomixConfiguration;
import org.apache.camel.impl.cluster.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AtomixClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClusterView.class);

    private final Atomix atomix;
    private final AtomixLocalMember localMember;
    private final AtomixConfiguration<?> configuration;
    private DistributedGroup group;

    AtomixClusterView(CamelClusterService cluster, String namespace, Atomix atomix, AtomixConfiguration<?> configuration) {
        super(cluster, namespace);

        this.atomix = atomix;
        this.configuration = configuration;
        this.localMember = new AtomixLocalMember();
    }

    @Override
    public Optional<CamelClusterMember> getLeader() {
        if (group == null) {
            return Optional.empty();
        }

        GroupMember leader = group.election().term().leader();
        if (leader == null) {
            return Optional.empty();
        }

        return Optional.of(new AtomixClusterMember(leader));
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        if (group == null) {
            return Collections.emptyList();
        }

        return this.group.members().stream()
            .map(AtomixClusterMember::new)
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doStart() throws Exception {
        if (!localMember.hasJoined()) {
            LOGGER.debug("Get group {}", getNamespace());

            group = this.atomix.getGroup(
                getNamespace(),
                new DistributedGroup.Config(configuration.getResourceConfig(getNamespace())),
                new DistributedGroup.Options(configuration.getResourceOptions(getNamespace()))
            ).get();

            LOGGER.debug("Listen election events");
            group.election().onElection(term -> {
                if (isRunAllowed()) {
                    fireLeadershipChangedEvent(Optional.of(toClusterMember(term.leader())));
                }
            });

            LOGGER.debug("Listen join events");
            group.onJoin(member -> {
                if (isRunAllowed()) {
                    fireMemberAddedEvent(toClusterMember(member));
                }
            });

            LOGGER.debug("Listen leave events");
            group.onLeave(member -> {
                if (isRunAllowed()) {
                    fireMemberRemovedEvent(toClusterMember(member));
                }
            });

            LOGGER.debug("Join group {}", getNamespace());
            localMember.join();
        }
    }

    @Override
    protected void doStop() throws Exception {
        localMember.leave();
    }

    protected CamelClusterMember toClusterMember(GroupMember member)  {
        return localMember != null && localMember.is(member)
            ? localMember
            : new AtomixClusterMember(member);
    }

    // ***********************************************
    //
    // ***********************************************

    final class AtomixLocalMember implements CamelClusterMember {
        private LocalMember member;

        @Override
        public String getId() {
            String id = getClusterService().getId();
            if (ObjectHelper.isNotEmpty(id)) {
                return id;
            }

            if (member == null) {
                throw new IllegalStateException("The view has not yet joined the cluster");
            }

            return member.id();
        }

        @Override
        public boolean isLeader() {
            if (member == null) {
                return false;
            }

            return member.equals(group.election().term().leader());
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        boolean is(GroupMember member) {
            return this.member != null
                ? this.member.equals(member)
                : false;
        }

        boolean hasJoined() {
            return member != null;
        }

        AtomixLocalMember join() throws ExecutionException, InterruptedException {
            if (member == null && group != null) {
                String id = getClusterService().getId();
                if (ObjectHelper.isEmpty(id) || configuration.isEphemeral()) {
                    LOGGER.debug("Joining group: {} ", group);
                    member = group.join().join();
                    LOGGER.debug("Group {} joined with id {}", group, member.id());
                } else {
                    LOGGER.debug("Joining group: {}, with id: {}", group, id);
                    member = group.join(id).join();
                }
            }

            return this;
        }

        AtomixLocalMember leave() {
            if (member != null) {
                String id = member.id();

                LOGGER.debug("Member {} : leave group {}", id, group);

                member.leave().join();
                group.remove(id).join();

                member = null;
                fireLeadershipChangedEvent(Optional.empty());
            }

            return this;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("AtomixLocalMember{");
            sb.append("member=").append(member);
            sb.append('}');
            return sb.toString();
        }
    }

    final class AtomixClusterMember implements CamelClusterMember {
        private final GroupMember member;

        AtomixClusterMember(GroupMember member) {
            this.member = member;
        }

        @Override
        public String getId() {
            return member.id();
        }

        @Override
        public boolean isLeader() {
            if (group == null) {
                return false;
            }
            if (member == null) {
                return false;
            }

            return member.equals(group.election().term().leader());
        }

        @Override
        public boolean isLocal() {
            return localMember != null ? localMember.is(member) : false;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("AtomixClusterMember{");
            sb.append("group=").append(group);
            sb.append(", member=").append(member);
            sb.append('}');
            return sb.toString();
        }
    }
}
