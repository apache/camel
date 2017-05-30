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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import io.atomix.group.DistributedGroup;
import io.atomix.group.GroupMember;
import io.atomix.group.LocalMember;
import org.apache.camel.ha.CamelClusterMember;
import org.apache.camel.ha.CamelClusterView;
import org.apache.camel.impl.ha.AbstractCamelClusterView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomixClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtomixClusterView.class);

    private final DistributedGroup group;
    private final AtomixLocalMember localMember;

    AtomixClusterView(AtomixCluster cluster, String namespace, DistributedGroup group) {
        super(cluster, namespace);

        this.group = group;
        this.localMember = new AtomixLocalMember(group);
    }

    @Override
    public CamelClusterMember getMaster() {
        return asCamelClusterMember(this.group.election().term().leader());
    }

    @Override
    public CamelClusterMember getLocalMember() {
        return this.localMember;
    }

    @Override
    public List<CamelClusterMember> getMembers() {
        // TODO: Dummy implementation for testing purpose
        return this.group.members().stream()
            .map(this::asCamelClusterMember)
            .collect(Collectors.toList());
    }

    private AtomixClusterMember asCamelClusterMember(GroupMember member) {
        return new AtomixClusterMember(group, member);
    }

    @Override
    protected void doStart() throws Exception {
        if (!this.localMember.hasJoined()) {
            this.localMember.join();
            this.group.election().onElection(
                t -> fireEvent(CamelClusterView.Event.LEADERSHIP_CHANGED, asCamelClusterMember(t.leader()))
            );
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
        private final DistributedGroup group;
        private LocalMember member;

        AtomixLocalMember(DistributedGroup group) {
            this.group = group;
        }

        @Override
        public String getId() {
            if (member == null) {
                throw new IllegalStateException("Cluster not yet joined");
            }

            return member.id();
        }

        @Override
        public boolean isMaster() {
            if (member == null) {
                return false;
            }

            return group.election().term().leader().equals(member);
        }

        boolean hasJoined() {
            return member != null;
        }

        AtomixLocalMember join() throws ExecutionException, InterruptedException {
            if (member == null) {
                LOGGER.debug("Joining group {}", group);
                member = this.group.join().get();
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

        LocalMember get() {
            return member;
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
            return group.election().term().leader().equals(member);
        }

        GroupMember get() {
            return member;
        }
    }
}
