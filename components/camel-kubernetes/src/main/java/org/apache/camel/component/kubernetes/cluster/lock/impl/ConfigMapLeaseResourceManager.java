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
package org.apache.camel.component.kubernetes.cluster.lock.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.component.kubernetes.cluster.lock.KubernetesLeaseResourceManager;
import org.apache.camel.component.kubernetes.cluster.lock.LeaderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMapLeaseResourceManager implements KubernetesLeaseResourceManager<ConfigMap> {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigMapLeaseResourceManager.class);

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssX";

    private static final String LEADER_PREFIX = "leader.pod.";

    private static final String LOCAL_TIMESTAMP_PREFIX = "leader.local.timestamp.";

    @Override
    public LeaderInfo decodeLeaderInfo(ConfigMap configMap, Set<String> members, String group) {
        return new LeaderInfo(group, getLeader(configMap, group), getLocalTimestamp(configMap, group), members, null);
    }

    @Override
    public ConfigMap fetchLeaseResource(KubernetesClient client, String namespace, String name, String group) {
        return client.configMaps()
                .inNamespace(namespace)
                .withName(name).get();
    }

    @Override
    public ConfigMap optimisticDeleteLeaderInfo(KubernetesClient client, ConfigMap leaseResource, String group) {
        ConfigMap updatedConfigMap = getConfigMapWithoutLeader(leaseResource, group);
        return client.configMaps()
                .inNamespace(leaseResource.getMetadata().getNamespace())
                .resource(updatedConfigMap)
                .lockResourceVersion(leaseResource.getMetadata().getResourceVersion()).update();
    }

    @Override
    public ConfigMap optimisticAcquireLeadership(KubernetesClient client, ConfigMap leaseResource, LeaderInfo newLeaderInfo) {
        ConfigMap updatedConfigMap = getConfigMapWithNewLeader(leaseResource, newLeaderInfo);
        return client.configMaps()
                .inNamespace(leaseResource.getMetadata().getNamespace())
                .resource(updatedConfigMap)
                .lockResourceVersion(leaseResource.getMetadata().getResourceVersion()).update();
    }

    @Override
    public ConfigMap createNewLeaseResource(
            KubernetesClient client, String namespace, String leaseResourceName, LeaderInfo leaderInfo) {
        ConfigMap newConfigMap
                = new ConfigMapBuilder().withNewMetadata().withName(leaseResourceName).addToLabels("provider", "camel")
                        .addToLabels("kind", "locks").endMetadata()
                        .addToData(LEADER_PREFIX + leaderInfo.getGroupName(), leaderInfo.getLeader())
                        .addToData(LOCAL_TIMESTAMP_PREFIX + leaderInfo.getGroupName(),
                                formatDate(leaderInfo.getLocalTimestamp()))
                        .build();

        return client.configMaps()
                .inNamespace(namespace)
                .resource(newConfigMap)
                .create();
    }

    @Override
    public ConfigMap refreshLeaseRenewTime(KubernetesClient client, ConfigMap leaseResource, int minUpdateIntervalSeconds) {
        // Configmap does not store renew information
        return leaseResource;
    }

    private static ConfigMap getConfigMapWithNewLeader(ConfigMap configMap, LeaderInfo leaderInfo) {
        return new ConfigMapBuilder(configMap).addToData(LEADER_PREFIX + leaderInfo.getGroupName(), leaderInfo.getLeader())
                .addToData(LOCAL_TIMESTAMP_PREFIX + leaderInfo.getGroupName(), formatDate(leaderInfo.getLocalTimestamp()))
                .build();
    }

    private static ConfigMap getConfigMapWithoutLeader(ConfigMap configMap, String group) {
        return new ConfigMapBuilder(configMap).removeFromData(LEADER_PREFIX + group)
                .removeFromData(LOCAL_TIMESTAMP_PREFIX + group)
                .build();
    }

    private static Date getLocalTimestamp(ConfigMap configMap, String group) {
        String timestamp = getConfigMapValue(configMap, LOCAL_TIMESTAMP_PREFIX + group);
        if (timestamp == null) {
            return null;
        }

        try {
            return new SimpleDateFormat(DATE_TIME_FORMAT).parse(timestamp);
        } catch (Exception e) {
            LOG.warn("Unable to parse time string '{}' using format {}", timestamp, DATE_TIME_FORMAT, e);
        }

        return null;
    }

    private static String getLeader(ConfigMap configMap, String group) {
        return getConfigMapValue(configMap, LEADER_PREFIX + group);
    }

    private static String getConfigMapValue(ConfigMap configMap, String key) {
        if (configMap == null || configMap.getData() == null) {
            return null;
        }
        return configMap.getData().get(key);
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        try {
            return new SimpleDateFormat(DATE_TIME_FORMAT).format(date);
        } catch (Exception e) {
            LOG.warn("Unable to format date '{}' using format {}", date, DATE_TIME_FORMAT, e);
        }

        return null;
    }

}
