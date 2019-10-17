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

import java.io.IOException;
import java.net.URI;

import org.apache.camel.component.hdfs.kerberos.KerberosAuthentication;
import org.apache.camel.component.hdfs.kerberos.KerberosConfigurationBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

class HdfsInfoFactory {

    private final HdfsConfiguration endpointConfig;

    HdfsInfoFactory(HdfsConfiguration endpointConfig) {
        this.endpointConfig = endpointConfig;
    }

    HdfsInfo newHdfsInfo(String hdfsPath) throws IOException {
        return newHdfsInfo(hdfsPath, endpointConfig);
    }

    HdfsInfo newHdfsInfoWithoutAuth(String hdfsPath) throws IOException {
        return newHdfsInfoWithoutAuth(hdfsPath, endpointConfig);
    }

    HdfsConfiguration getEndpointConfig() {
        return endpointConfig;
    }

    private static HdfsInfo newHdfsInfo(String hdfsPath, HdfsConfiguration endpointConfig) throws IOException {
        // need to remember auth as Hadoop will override that, which otherwise means the Auth is broken afterwards
        javax.security.auth.login.Configuration auth = HdfsComponent.getJAASConfiguration();
        try {
            return newHdfsInfoWithoutAuth(hdfsPath, endpointConfig);
        } finally {
            HdfsComponent.setJAASConfiguration(auth);
        }
    }

    private static HdfsInfo newHdfsInfoWithoutAuth(String hdfsPath, HdfsConfiguration endpointConfig) throws IOException {
        Configuration configuration = newConfiguration(endpointConfig);

        authenticate(configuration, endpointConfig);

        FileSystem fileSystem = newFileSystem(configuration, hdfsPath, endpointConfig);
        Path path = new Path(hdfsPath);

        return new HdfsInfo(configuration, fileSystem, path);
    }

    static Configuration newConfiguration(HdfsConfiguration endpointConfig) {
        Configuration configuration = new Configuration();

        if (endpointConfig.isKerberosAuthentication()) {
            KerberosConfigurationBuilder.withKerberosConfiguration(configuration, endpointConfig);
        }

        if (endpointConfig.hasClusterConfiguration()) {
            HaConfigurationBuilder.withClusterConfiguration(configuration, endpointConfig);
        }

        return configuration;
    }

    static void authenticate(Configuration configuration, HdfsConfiguration endpointConfig) throws IOException {
        if (endpointConfig.isKerberosAuthentication()) {
            String userName = endpointConfig.getKerberosUsername();
            String keytabLocation = endpointConfig.getKerberosKeytabLocation();
            new KerberosAuthentication(configuration, userName, keytabLocation).loginWithKeytab();
        }
    }

    /**
     * this will connect to the hadoop hdfs file system, and in case of no connection
     * then the hardcoded timeout in hadoop is 45 x 20 sec = 15 minutes
     */
    static FileSystem newFileSystem(Configuration configuration, String hdfsPath, HdfsConfiguration endpointConfig) throws IOException {
        FileSystem fileSystem;
        if (endpointConfig.hasClusterConfiguration()) {
            // using default FS that was set during in the cluster configuration (@see org.apache.camel.component.hdfs.HaConfigurationBuilder)
            fileSystem = FileSystem.get(configuration);
        } else {
            fileSystem = FileSystem.get(URI.create(hdfsPath), configuration);
        }

        return fileSystem;
    }

}
