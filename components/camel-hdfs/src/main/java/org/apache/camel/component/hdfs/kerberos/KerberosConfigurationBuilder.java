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

import org.apache.camel.component.hdfs.HdfsConfiguration;
import org.apache.hadoop.conf.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KerberosConfigurationBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(KerberosConfigurationBuilder.class);

    private static final String KERBEROS_5_SYS_ENV = "java.security.krb5.conf";
    private static final String AUTHENTICATION_MODE = "hadoop.security.authentication";

    private KerberosConfigurationBuilder() {
        // hidden
    }

    /**
     * Add all the kerberos specific settings needed for this authentication mode
     *
     * @param endpointConfig - configuration with the HA settings configured on the endpoint
     */
    public static void withKerberosConfiguration(Configuration configuration, HdfsConfiguration endpointConfig) {
        setKerberosConfigFile(endpointConfig.getKerberosConfigFileLocation());
        configuration.set(AUTHENTICATION_MODE, "kerberos");

    }

    /**
     * To use kerberos authentication, set the value of the 'java.security.krb5.conf' environment variable to an existing file.
     * If the environment variable is already set, warn if different than the specified parameter
     *
     * @param kerberosConfigFileLocation - kerb5.conf file (https://web.mit.edu/kerberos/krb5-1.12/doc/admin/conf_files/krb5_conf.html)
     */
    public static void setKerberosConfigFile(String kerberosConfigFileLocation) {
        if (!new File(kerberosConfigFileLocation).exists()) {
            LOG.warn("Kerberos configuration file [{}}] could not be found.", kerberosConfigFileLocation);
            return;
        }

        String krb5Conf = System.getProperty(KERBEROS_5_SYS_ENV);
        if (krb5Conf == null || !krb5Conf.isEmpty()) {
            System.setProperty(KERBEROS_5_SYS_ENV, kerberosConfigFileLocation);
        } else if (!krb5Conf.equalsIgnoreCase(kerberosConfigFileLocation)) {
            LOG.warn("[{}] was already configured with: [{}] config file", KERBEROS_5_SYS_ENV, krb5Conf);
        }
    }

}
