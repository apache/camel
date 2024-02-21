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
package org.apache.camel.component.ignite;

import org.apache.ignite.Ignite;
import org.apache.ignite.cluster.ClusterGroup;

/**
 * Convenient set of commonly used {@link ClusterGroupExpression}s.
 */
public final class ClusterGroupExpressions {

    public static final ClusterGroupExpression FOR_CLIENTS = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forClients();
        }
    };

    public static final ClusterGroupExpression FOR_LOCAL = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forLocal();
        }
    };

    public static final ClusterGroupExpression FOR_OLDEST = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forOldest();
        }
    };

    public static final ClusterGroupExpression FOR_YOUNGEST = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forYoungest();
        }
    };

    public static final ClusterGroupExpression FOR_RANDOM = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forRandom();
        }
    };

    public static final ClusterGroupExpression FOR_REMOTES = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forRemotes();
        }
    };

    public static final ClusterGroupExpression FOR_SERVERS = new ClusterGroupExpression() {
        @Override
        public ClusterGroup getClusterGroup(Ignite ignite) {
            return ignite.cluster().forServers();
        }
    };

    private ClusterGroupExpressions() {
    }

}
