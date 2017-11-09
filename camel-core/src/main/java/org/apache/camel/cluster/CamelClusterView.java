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
package org.apache.camel.cluster;

import java.util.List;
import java.util.Optional;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;

/**
 * Represents the View of the cluster at some given period of time.
 */
public interface CamelClusterView extends Service, CamelContextAware {
    /**
     * @return the cluster.
     */
    CamelClusterService getClusterService();

    /**
     * @return the namespace for this view.
     */
    String getNamespace();

    /**
     * Provides the master member if elected.
     *
     * @return the master member.
     * @deprecated use {@link #getLeader()}
     */
    @Deprecated
    default Optional<CamelClusterMember> getMaster() {
        return getLeader();
    }

    /**
     * Provides the leader member if elected.
     *
     * @return the leader member.
     */
    Optional<CamelClusterMember> getLeader();

    /**
     * Provides the local member.
     *
     * @return the local member.
     */
    CamelClusterMember getLocalMember();

    /**
     * Provides the list of members of the cluster.
     *
     * @return the list of members.
     */
    List<CamelClusterMember> getMembers();

    /**
     * Add an event listener.
     *
     * @param listener the event listener.
     */
    void addEventListener(CamelClusterEventListener listener);

    /**
     * Remove the event listener.
     *
     * @param listener the event listener.
     */
    void removeEventListener(CamelClusterEventListener listener);

    /**
     * Access the underlying concrete CamelClusterView implementation to
     * provide access to further features.
     *
     * @param clazz the proprietary class or interface of the underlying concrete CamelClusterView.
     * @return an instance of the underlying concrete CamelClusterView as the required type.
     */
    default <T extends CamelClusterView> T unwrap(Class<T> clazz) {
        if (CamelClusterView.class.isAssignableFrom(clazz)) {
            return clazz.cast(this);
        }

        throw new IllegalArgumentException(
            "Unable to unwrap this CamelClusterView type (" + getClass() + ") to the required type (" + clazz + ")"
        );
    }
}
