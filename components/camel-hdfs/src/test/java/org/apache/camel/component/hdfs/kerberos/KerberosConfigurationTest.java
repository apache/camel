package org.apache.camel.component.hdfs.kerberos;

import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.*;

public class KerberosConfigurationTest {

    private KerberosConfiguration underTest;

    @Test
    public void newKerberosConfiguration() throws IOException {
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
    public void newKerberosConfigurationWithMissingKerberosConfigFile() throws IOException {
        // given
        List<String> namedNodes = Arrays.asList("kerb_node_01.example.com:8021", "kerb_node_02.example.com:8022");
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/missing.conf";
        int replicationFactor = 3;

        // when
        underTest = new KerberosConfiguration(namedNodes, kerberosConfigFileLocation, replicationFactor);

        // then
        /* exception was thrown */
    }

    @Test
    public void loginWithKeytab() throws IOException {
        // given
        List<String> namedNodes = Arrays.asList("kerb_node_01.example.com:8021", "kerb_node_02.example.com:8022");
        String kerberosConfigFileLocation = pwd() + "/src/test/resources/kerberos/test-kerb5.conf";
        int replicationFactor = 3;
        underTest = new KerberosConfiguration(namedNodes, kerberosConfigFileLocation, replicationFactor);

        String username = "test_user";
        String keyTabFileLocation = pwd() + "/src/test/resources/kerberos/test-keytab.bin";

        // when
        underTest.loginWithKeytab(username, keyTabFileLocation);

        // then

    }

    private String pwd() {
        return new File(".").getAbsolutePath();
    }

}