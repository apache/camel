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

import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class HaConfigurationBuilderTest {

    @Test
    public void withClusterConfiguration() {
        // given
        Configuration configuration = new Configuration();
        String haClusterName = "haCluster";
        List<String> namedNodes = Arrays.asList("kerb_node_01.example.com:8021", "kerb_node_02.example.com:8022");
        int replicationFactor = 3;

        // when
        HaConfigurationBuilder.withClusterConfiguration(configuration, haClusterName, namedNodes, replicationFactor);

        // then
        assertThat(configuration, notNullValue());
        assertThat(configuration.get(DFSConfigKeys.DFS_REPLICATION_KEY), is("3"));
        assertThat(configuration.get(DFSConfigKeys.DFS_NAMESERVICES), is("haCluster"));
        assertThat(configuration.get("dfs.ha.namenodes.haCluster"), is("kerb_node_01_example_com,kerb_node_02_example_com"));
        assertThat(configuration.get("dfs.namenode.rpc-address.haCluster.kerb_node_01_example_com"), is("kerb_node_01.example.com:8021"));
        assertThat(configuration.get("dfs.namenode.rpc-address.haCluster.kerb_node_02_example_com"), is("kerb_node_02.example.com:8022"));
        assertThat(configuration.get("dfs.client.failover.proxy.provider.haCluster"), is("org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"));
        assertThat(configuration.get("fs.defaultFS"), is("hdfs://haCluster"));
    }

    @Test
    public void getSanitizedClusterNameWithNull() {
        // given
        String haClusterName = null;

        // when
        String actual = HaConfigurationBuilder.getSanitizedClusterName(haClusterName);

        // then
        assertThat(actual, notNullValue());
        assertThat(actual, is("hfdsNamedService"));
    }

    @Test
    public void getSanitizedClusterNameWithHostName() {
        // given
        String haClusterName = "this.is.a.cluster.host";

        // when
        String actual = HaConfigurationBuilder.getSanitizedClusterName(haClusterName);

        // then
        assertThat(actual, notNullValue());
        assertThat(actual, is("this_is_a_cluster_host"));
    }

}
