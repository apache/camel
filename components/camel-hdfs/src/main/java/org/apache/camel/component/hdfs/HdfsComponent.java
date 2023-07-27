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

import java.net.URL;
import java.util.Map;

import javax.security.auth.login.Configuration;

import org.apache.camel.Endpoint;
import org.apache.camel.component.hdfs.kerberos.KerberosConfigurationBuilder;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.hadoop.fs.FsUrlStreamHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("hdfs")
public class HdfsComponent extends HealthCheckComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HdfsComponent.class);

    private static String kerberosConfigFileLocation;

    public HdfsComponent() {
        initHdfs();
    }

    @Override
    protected final Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HdfsEndpoint hdfsEndpoint = new HdfsEndpoint(uri, this);
        setProperties(hdfsEndpoint, parameters);
        return hdfsEndpoint;
    }

    protected void initHdfs() {
        try {
            URL.setURLStreamHandlerFactory(new FsUrlStreamHandlerFactory());
        } catch (Error e) {
            // The setURLStreamHandlerFactory throws an error if the factory is already set, which is why
            // we have the unusual catch for a java.lang.Error
            LOG.debug("Cannot set URLStreamHandlerFactory due {}. This exception will be ignored.", e.getMessage(), e);
        }
    }

    public static Configuration getJAASConfiguration() {
        Configuration auth = null;
        try {
            auth = Configuration.getConfiguration();
            LOG.trace("Existing JAAS Configuration {}", auth);
        } catch (SecurityException e) {
            LOG.trace("Cannot load existing JAAS configuration", e);
        }
        return auth;
    }

    /**
     * To use the given configuration for security with JAAS.
     */
    @Metadata(label = "security")
    public static void setJAASConfiguration(Configuration auth) {
        if (auth != null) {
            LOG.trace("Restoring existing JAAS Configuration {}", auth);
            try {
                Configuration.setConfiguration(auth);
            } catch (SecurityException e) {
                LOG.trace("Cannot restore JAAS Configuration. This exception is ignored.", e);
            }
        } else {
            LOG.trace("No JAAS Configuration to restore");
        }
    }

    /**
     * To use kerberos authentication, set the value of the 'java.security.krb5.conf' environment variable to an
     * existing file. If the environment variable is already set, warn if different than the specified parameter
     *
     * @param kerberosConfigFileLocation - kerb5.conf file
     *                                   (https://web.mit.edu/kerberos/krb5-1.12/doc/admin/conf_files/krb5_conf.html)
     */
    @Metadata(label = "security")
    public static void setKerberosConfigFile(String kerberosConfigFileLocation) {
        HdfsComponent.kerberosConfigFileLocation = kerberosConfigFileLocation;
        KerberosConfigurationBuilder.setKerberosConfigFile(kerberosConfigFileLocation);
    }

    public static String getKerberosConfigFile() {
        return kerberosConfigFileLocation;
    }

}
