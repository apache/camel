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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;

import static java.lang.String.format;

public class KerberosAuthentication {

    private final String username;
    private final String keyTabFileLocation;
    private final Configuration configuration;

    /**
     * @param configuration      - hdfs configuration
     * @param username           - Principal used to authenticate to the kerberos server
     * @param keyTabFileLocation - keyTab file location
     */
    public KerberosAuthentication(Configuration configuration, String username, String keyTabFileLocation) {
        this.configuration = configuration;
        this.username = username;
        this.keyTabFileLocation = keyTabFileLocation;
    }

    /**
     * In order to connect to a hadoop cluster using Kerberos you need to add your own filesystem to the cache of the FileSystem component.
     * This is done by setting the uri that you use in your camel route as the URI that is used to setup the connection.
     * The URI is used as key when adding it to the cache (default functionality of the static FileSystem.get(URI, Configuration) method).
     *
     * @throws IOException - In case of error
     */
    public void loginWithKeytab() throws IOException {
        if (!new File(keyTabFileLocation).exists()) {
            throw new FileNotFoundException(format("KeyTab file [%s] could not be found.", keyTabFileLocation));
        }
        // we need to log in otherwise you cannot connect to the filesystem later on
        UserGroupInformation.setConfiguration(configuration);
        UserGroupInformation.loginUserFromKeytab(username, keyTabFileLocation);
    }

}
