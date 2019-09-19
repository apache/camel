package org.apache.camel.component.hdfs.kerberos;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.DFSUtil;
import org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_CLIENT_FAILOVER_PROXY_PROVIDER_KEY_PREFIX;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_HA_NAMENODES_KEY_PREFIX;
import static org.apache.hadoop.hdfs.DFSConfigKeys.DFS_NAMENODE_RPC_ADDRESS_KEY;

public class KerberosConfiguration extends Configuration {

    private static final String HFDS_NAMED_SERVICE = "hfdsNamedService";

    private static final String AUTHENTICATION_MODE = "hadoop.security.authentication";
    private static final String HFDS_FS = "fs.defaultFS";

    /**
     * Add all the kerberos specific settings needed for this authentication mode
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
     * @param namedNodes                 - All named nodes from the hadoop cluster
     * @param kerberosConfigFileLocation - The location of the kerberos config file (on the server)
     * @param replicationFactor          - dfs replication factor
     */
    public KerberosConfiguration(List<String> namedNodes,
                                 String kerberosConfigFileLocation,
                                 int replicationFactor) throws IOException {

        HdfsKerberosConfigurationFactory.setKerberosConfigFile(kerberosConfigFileLocation);
        setupHdfsConfiguration(namedNodes, replicationFactor);
    }

    private void setupHdfsConfiguration(List<String> namedNodes, int replicationFactor) {
        this.set(AUTHENTICATION_MODE, "kerberos");

        this.set(DFSConfigKeys.DFS_REPLICATION_KEY, Integer.toString(replicationFactor));
        this.set(DFSConfigKeys.DFS_NAMESERVICES, HFDS_NAMED_SERVICE);
        this.set(
                DFSUtil.addKeySuffixes(DFS_HA_NAMENODES_KEY_PREFIX, HFDS_NAMED_SERVICE),
                nodeToString(namedNodes.stream().map(this::nodeToString).collect(Collectors.joining(",")))
        );

        namedNodes.forEach(nodeName ->
                this.set(
                        DFSUtil.addKeySuffixes(DFS_NAMENODE_RPC_ADDRESS_KEY, HFDS_NAMED_SERVICE, nodeToString(nodeName)),
                        nodeName)
        );

        this.set(DFS_CLIENT_FAILOVER_PROXY_PROVIDER_KEY_PREFIX + "." + HFDS_NAMED_SERVICE, ConfiguredFailoverProxyProvider.class.getName());

        this.set(HFDS_FS, "hdfs://" + HFDS_NAMED_SERVICE);
    }

    /**
     * In order to connect to a hadoop cluster using Kerberos you need to add your own filesystem to the cache of the FileSystem component.
     * This is done by setting the uri that you use in your camel route as the URI that is used to setup the connection.
     * The URI is used as key when adding it to the cache (default functionality of the static FileSystem.get(URI, Configuration) method).
     *
     * @param username           - Principal used to connect to the cluster
     * @param keyTabFileLocation - KeyTab file location (must be on the server)
     * @throws IOException - In case of error
     */
    public void loginWithKeytab(String username, String keyTabFileLocation) throws IOException {
        if (!new File(keyTabFileLocation).exists()) {
            throw new FileNotFoundException(format("KeyTab file [%s] could not be found.", keyTabFileLocation));
        }
        // we need to log in otherwise you cannot connect to the filesystem later on
        UserGroupInformation.setConfiguration(this);
        UserGroupInformation.loginUserFromKeytab(username, keyTabFileLocation);
    }

    private String nodeToString(String nodeName) {
        return nodeName.replaceAll(":[0-9]*", "").replaceAll("\\.", "_");
    }

}
