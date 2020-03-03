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
package org.apache.camel.component.hdfs;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.DFSUtil;
import org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider;

import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_CLIENT_FAILOVER_PROXY_PROVIDER_KEY_PREFIX;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_HA_NAMENODES_KEY_PREFIX;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_RPC_ADDRESS_KEY;

final class HaConfigurationBuilder {

    private static final String HFDS_NAMED_SERVICE = "hfdsNamedService";
    private static final String HFDS_NAMED_SERVICE_SEPARATOR = "_";
    private static final String HFDS_FS = "fs.defaultFS";

    private HaConfigurationBuilder() {
        // hidden
    }

    /**
     * Generates the correct HA configuration (normally read from xml) based on the namedNodes:
     * All named nodes have to be qualified: configuration.set("dfs.ha.namenodes.hfdsNamedService","namenode1,namenode2");
     * For each named node the following entries is added
     * <p>
     * configuration.set("dfs.namenode.rpc-address.hfdsNamedService.namenode1", "namenode1:1234");
     * <p>
     * Finally the proxy provider has to be specified:
     * <p>
     * configuration.set("dfs.client.failover.proxy.provider.hfdsNamedService", "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");
     * <p>
     *
     * @param configuration  - hdfs configuration that will be setup with the HA settings
     * @param endpointConfig - configuration with the HA settings configured on the endpoint
     */
    static void withClusterConfiguration(Configuration configuration, HdfsConfiguration endpointConfig) {
        String haNamedService = getSanitizedClusterName(endpointConfig.getHostName());
        withClusterConfiguration(configuration, haNamedService, endpointConfig.getNamedNodeList(), endpointConfig.getReplication());
    }

    /**
     * Generates the correct HA configuration (normally read from xml) based on the namedNodes:
     * All named nodes have to be qualified: configuration.set("dfs.ha.namenodes.hfdsNamedService","namenode1,namenode2");
     * For each named node the following entries is added
     * <p>
     * configuration.set("dfs.namenode.rpc-address.hfdsNamedService.namenode1", "namenode1:1234");
     * <p>
     * Finally the proxy provider has to be specified:
     * <p>
     * configuration.set("dfs.client.failover.proxy.provider.hfdsNamedService", "org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider");
     * <p>
     *
     * @param configuration     - hdfs configuration that will be setup with the HA settings
     * @param haNamedService    - how the ha named service that represents the cluster will be named (used to resolve the FS)
     * @param namedNodes        - All named nodes from the hadoop cluster
     * @param replicationFactor - dfs replication factor
     */
    static void withClusterConfiguration(Configuration configuration, String haNamedService, List<String> namedNodes, int replicationFactor) {
        configuration.set(DFSConfigKeys.DFS_REPLICATION_KEY, Integer.toString(replicationFactor));
        configuration.set(DFSConfigKeys.DFS_NAMESERVICES, haNamedService);
        configuration.set(
                DFSUtil.addKeySuffixes(DFS_HA_NAMENODES_KEY_PREFIX, haNamedService),
                nodeToString(namedNodes.stream().map(HaConfigurationBuilder::nodeToString).collect(Collectors.joining(",")))
        );

        namedNodes.forEach(nodeName ->
                configuration.set(
                        DFSUtil.addKeySuffixes(DFS_NAMENODE_RPC_ADDRESS_KEY, haNamedService, nodeToString(nodeName)),
                        nodeName)
        );

        configuration.set(DFS_CLIENT_FAILOVER_PROXY_PROVIDER_KEY_PREFIX + "." + haNamedService, ConfiguredFailoverProxyProvider.class.getName());

        configuration.set(HFDS_FS, "hdfs://" + haNamedService);

    }

    static String getSanitizedClusterName(String rawClusterName) {
        String clusterName = HFDS_NAMED_SERVICE;

        if (StringUtils.isNotEmpty(rawClusterName)) {
            clusterName = rawClusterName.replace(".", HFDS_NAMED_SERVICE_SEPARATOR);
        }

        return clusterName;
    }

    private static String nodeToString(String nodeName) {
        return nodeName.replaceAll(":[0-9]*", "").replaceAll("\\.", HFDS_NAMED_SERVICE_SEPARATOR);
    }

}
