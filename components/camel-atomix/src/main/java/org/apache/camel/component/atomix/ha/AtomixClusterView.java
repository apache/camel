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
package org.apache.camel.component.atomix.ha;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import io.atomix.Atomix;
import io.atomix.group.DistributedGroup;
import io.atomix.group.GroupMember;
import io.atomix.group.LocalMember;
import org.apache.camel.component.atomix.AtomixConfiguration;
import org.apache.camel.component.atomix.AtomixConfigurationAware;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterService;
import org.apache.camel.impl.ha.AbstractCamelClusterView;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AtomixClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClusterView.class);

    private final Atomix atomix;
    private final AtomixLocalMember localMember;
    private DistributedGroup group;

    AtomixClusterView(CamelClusterService cluster, String namespace, Atomix atomix) {
        super(cluster, namespace);

        this.atomix = atomix;
        this.localMember = new AtomixLocalMember();
    }

    @Override
    public Optional<CamelClusterMember> getMaster() {
        if (group == null) {
            return Optional.empty();
        }

        GroupMember leader = group.election().term().leader();
        if (leader == null) {
            return Optional.empty();
        }

        return Optional.of(asCamelClusterMember(leader));
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
            .map(this::asCamelClusterMember)
            .collect(Collectors.toList());
    }

    private AtomixClusterMember asCamelClusterMember(GroupMember member) {
        return new AtomixClusterMember(group, member);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void doStart() throws Exception {
        if (!localMember.hasJoined()) {
            LOGGER.debug("Get group {}", getNamespace());

            final AtomixConfigurationAware service = AtomixConfigurationAware.class.cast(getClusterService());
            final AtomixConfiguration<?> configuration = service.getConfiguration();

            group = this.atomix.getGroup(
                getNamespace(),
                new DistributedGroup.Config(configuration.getResourceConfig(getNamespace())),
                new DistributedGroup.Options(configuration.getResourceOptions(getNamespace()))
            ).get();

            LOGGER.debug("Join group {}", getNamespace());
            localMember.join();

            LOGGER.debug("Listen election events");
            group.election().onElection(term -> fireLeadershipChangedEvent(asCamelClusterMember(term.leader())));

            LOGGER.debug("Listen join events");
            group.onJoin(member -> fireMemberAddedEvent(asCamelClusterMember(member)));

            LOGGER.debug("Listen leave events");
            group.onLeave(member -> fireMemberRemovedEvent(asCamelClusterMember(member)));
        }
    }

    @Override
    protected void doStop() throws Exception {
        localMember.leave();
    }

    // ***********************************************
    //
    // ***********************************************

    class AtomixLocalMember implements CamelClusterMember {
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
        public boolean isMaster() {
            if (member == null) {
                return false;
            }

            return member.equals(group.election().term().leader());
        }

        boolean hasJoined() {
            return member != null;
        }

        AtomixLocalMember join() throws ExecutionException, InterruptedException {
            if (member == null && group != null) {
                String id = getClusterService().getId();
                if (ObjectHelper.isNotEmpty(id)) {
                    LOGGER.debug("Joining group: {}, with id: {}", group, id);
                    member = group.join(id).join();
                } else {
                    LOGGER.debug("Joining group: {} ", group);
                    member = group.join().join();
                }
            }

            return this;
        }

        AtomixLocalMember leave() {
            if (member != null) {
                LOGGER.debug("Leaving group {}", group);
                member.leave();
            }

            return this;
        }
    }

    class AtomixClusterMember implements CamelClusterMember {
        private final DistributedGroup group;
        private final GroupMember member;

        AtomixClusterMember(DistributedGroup group, GroupMember member) {
            this.group = group;
            this.member = member;
        }

        @Override
        public String getId() {
            return member.id();
        }

        @Override
        public boolean isMaster() {
            return member.equals(group.election().term().leader());
        }
    }
}
