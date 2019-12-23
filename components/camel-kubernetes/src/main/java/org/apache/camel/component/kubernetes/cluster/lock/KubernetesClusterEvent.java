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
package org.apache.camel.component.kubernetes.cluster.lock;

import java.util.Optional;
import java.util.Set;

/**
 * Super interface for events produced by the Kubernetes cluster.
 */
@FunctionalInterface
public interface KubernetesClusterEvent {

    Object getData();

    /**
     * Event signalling that the list of members of the Kubernetes cluster has
     * changed.
     */
    interface KubernetesClusterMemberListChangedEvent extends KubernetesClusterEvent {
        @Override
        Set<String> getData();
    }

    /**
     * Event signalling the presence of a new leader.
     */
    interface KubernetesClusterLeaderChangedEvent extends KubernetesClusterEvent {
        @Override
        Optional<String> getData();
    }

}
