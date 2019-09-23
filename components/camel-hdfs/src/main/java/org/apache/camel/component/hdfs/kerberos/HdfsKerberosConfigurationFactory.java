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

import static java.lang.String.format;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HdfsKerberosConfigurationFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HdfsKerberosConfigurationFactory.class);

    private static final String KERBEROS_5_SYS_ENV = "java.security.krb5.conf";

    private HdfsKerberosConfigurationFactory() {
        // factory class
    }

    public static void setKerberosConfigFile(String kerberosConfigFileLocation) throws FileNotFoundException {
        if (!new File(kerberosConfigFileLocation).exists()) {
            throw new FileNotFoundException(format("KeyTab file [%s] could not be found.", kerberosConfigFileLocation));
        }

        String krb5Conf = System.getProperty(KERBEROS_5_SYS_ENV);
        if (krb5Conf == null || !krb5Conf.isEmpty()) {
            System.setProperty(KERBEROS_5_SYS_ENV, kerberosConfigFileLocation);
        } else if (!krb5Conf.equalsIgnoreCase(kerberosConfigFileLocation)) {
            LOGGER.warn("[{}] was already configured with: [{}] config file", KERBEROS_5_SYS_ENV, krb5Conf);
        }
    }

}
