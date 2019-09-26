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
package org.apache.camel.component.hdfs.kerberos;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class KerberosConfigurationTest {

    private KerberosConfiguration underTest;

    @Test
    public void newKerberosConfiguration() {
        // given
        List<String> namedNodes = Arrays.asList("kerb_node_01.example.com:8021", "kerb_node_02.example.com:8022");
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/test-kerb5.conf";
        int replicationFactor = 3;

        // when
        underTest = new KerberosConfiguration(namedNodes, kerberosConfigFileLocation, replicationFactor);

        // then
        assertThat(underTest, notNullValue());
        assertThat(underTest.get("hadoop.security.authentication"), is("kerberos"));
        assertThat(underTest.get(DFSConfigKeys.DFS_REPLICATION_KEY), is("3"));
        assertThat(underTest.get(DFSConfigKeys.DFS_NAMESERVICES), is("hfdsNamedService"));
        assertThat(underTest.get("dfs.ha.namenodes.hfdsNamedService"), is("kerb_node_01_example_com,kerb_node_02_example_com"));
        assertThat(underTest.get("dfs.namenode.rpc-address.hfdsNamedService.kerb_node_01_example_com"), is("kerb_node_01.example.com:8021"));
        assertThat(underTest.get("dfs.namenode.rpc-address.hfdsNamedService.kerb_node_02_example_com"), is("kerb_node_02.example.com:8022"));
        assertThat(underTest.get("dfs.client.failover.proxy.provider.hfdsNamedService"), is("org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"));
        assertThat(underTest.get("fs.defaultFS"), is("hdfs://hfdsNamedService"));
    }

    @Test(expected = FileNotFoundException.class)
    public void newKerberosConfigurationWithMissingKerberosConfigFile() {
        // given
        List<String> namedNodes = Arrays.asList("kerb_node_01.example.com:8021", "kerb_node_02.example.com:8022");
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/missing.conf";
        int replicationFactor = 3;

        // when
        underTest = new KerberosConfiguration(namedNodes, kerberosConfigFileLocation, replicationFactor);

        // then
        /* exception was thrown */
    }

    @Test(expected = FileNotFoundException.class)
    public void loginWithMissingKeytabFile() throws IOException {
        // given
        List<String> namedNodes = Arrays.asList("kerb_node_01.example.com:8021", "kerb_node_02.example.com:8022");
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/test-kerb5.conf";
        int replicationFactor = 3;
        underTest = new KerberosConfiguration(namedNodes, kerberosConfigFileLocation, replicationFactor);

        String username = "test_user";
        String keyTabFileLocation = pwd() + "/src/test/resources/kerberos/missing.bin";

        // when
        underTest.loginWithKeytab(username, keyTabFileLocation);

        // then
        /* exception was thrown */
    }

    private String pwd() {
        return new File(".").getAbsolutePath();
    }

}