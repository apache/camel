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
package org.apache.camel.component.hazelcast;

import com.hazelcast.cluster.Member;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public final class HazelcastUtil {
    private HazelcastUtil() {
    }

    public static HazelcastInstance newInstance() {
        Config cfg = new XmlConfigBuilder().build();

        // hazelcast.version.check.enabled is deprecated
        cfg.setProperty(
                "hazelcast.phone.home.enabled",
                System.getProperty("hazelcast.phone.home.enabled", "false"));
        cfg.setProperty(
                "hazelcast.logging.type",
                System.getProperty("hazelcast.logging.type", "slf4j"));
        HazelcastSerializationFilterHelper.applyDefault(cfg);

        return newInstance(cfg);
    }

    public static HazelcastInstance newInstance(Config cfg) {
        return Hazelcast.newHazelcastInstance(cfg);
    }

    /**
     * Whether the entries of the map are kept only in the memory of this JVM, so they are lost when the application
     * stops. This is decided when asked, as the cluster changes: a client or a lite member keeps no entries in this
     * JVM, and a map store or data persistence keeps them elsewhere. A member alone in its cluster loses them. With
     * other data members, the backups decide: a backup count above 0 (1 is the default) keeps a copy on another member.
     */
    public static boolean isInMemory(HazelcastInstance instance, String mapName) {
        if (!isDataMember(instance)) {
            return false;
        }
        MapConfig config = instance.getConfig().findMapConfig(mapName);
        if (config.getMapStoreConfig().isEnabled() || config.getDataPersistenceConfig().isEnabled()) {
            return false;
        }
        return config.getTotalBackupCount() == 0 || !hasOtherDataMembers(instance);
    }

    /**
     * Whether the entries of a replicated map are kept only in the memory of this JVM: a replicated map has a copy on
     * every data member, so only a member alone in its cluster loses them. A client or a lite member keeps none.
     */
    public static boolean isReplicatedInMemory(HazelcastInstance instance) {
        return isDataMember(instance) && !hasOtherDataMembers(instance);
    }

    private static boolean isDataMember(HazelcastInstance instance) {
        // the local endpoint of a client is not a member
        return instance != null && instance.getLifecycleService().isRunning()
                && instance.getLocalEndpoint() instanceof Member member && !member.isLiteMember();
    }

    private static boolean hasOtherDataMembers(HazelcastInstance instance) {
        for (Member member : instance.getCluster().getMembers()) {
            if (!member.localMember() && !member.isLiteMember()) {
                return true;
            }
        }
        return false;
    }
}
