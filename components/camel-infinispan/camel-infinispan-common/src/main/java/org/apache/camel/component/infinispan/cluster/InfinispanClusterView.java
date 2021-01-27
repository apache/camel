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
package org.apache.camel.component.infinispan.cluster;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class InfinispanClusterView extends AbstractCamelClusterView {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanClusterView.class);

    protected InfinispanClusterView(CamelClusterService cluster, String namespace) {
        super(cluster, namespace);
    }

    protected abstract boolean isLeader(String id);

    // ***********************************************
    //
    // ***********************************************

    protected final class LocalMember implements CamelClusterMember {
        private final AtomicBoolean leader = new AtomicBoolean();
        private final String id;

        public LocalMember(String id) {
            this.id = id;
        }

        public void setLeader(boolean master) {
            if (master && this.leader.compareAndSet(false, true)) {
                LOGGER.debug("Leadership taken for id: {}", id);

                fireLeadershipChangedEvent(Optional.of(this));
                return;
            }
            if (!master && this.leader.compareAndSet(true, false)) {
                LOGGER.debug("Leadership lost for id: {}", id);

                fireLeadershipChangedEvent(getLeader());
                return;
            }
        }

        @Override
        public boolean isLeader() {
            return leader.get();
        }

        @Override
        public boolean isLocal() {
            return true;
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public String toString() {
            return "LocalMember{" + "leader=" + leader + '}';
        }
    }

    protected final class ClusterMember implements CamelClusterMember {
        private final String id;

        public ClusterMember(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isLeader() {
            return InfinispanClusterView.this.isLeader(id);
        }

        @Override
        public boolean isLocal() {
            if (id == null) {
                return false;
            }

            return Objects.equals(id, getLocalMember().getId());
        }

        @Override
        public String toString() {
            return "ClusterMember{" + "id='" + id + '\'' + '}';
        }
    }
}
