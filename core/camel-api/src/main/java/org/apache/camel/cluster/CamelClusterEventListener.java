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
package org.apache.camel.cluster;

import java.util.Optional;

/**
 * Marker interface for cluster events
 */
public interface CamelClusterEventListener {

    interface Leadership extends CamelClusterEventListener {

        /**
         * Notify a change in the leadership for a particular cluster.
         *
         * @param view the cluster view
         * @param leader the optional new leader
         */
        void leadershipChanged(CamelClusterView view, Optional<CamelClusterMember> leader);

    }

    interface Membership extends CamelClusterEventListener {

        /**
         * Notify a change (addition) in the cluster composition.
         *
         * @param view the cluster view
         * @param member the member that has been added
         */
        void memberAdded(CamelClusterView view, CamelClusterMember member);

        /**
         * Notify a change (removal) in the cluster composition.
         *
         * @param view the cluster view
         * @param member the member that has been removed
         */
        void memberRemoved(CamelClusterView view, CamelClusterMember member);

    }
}
