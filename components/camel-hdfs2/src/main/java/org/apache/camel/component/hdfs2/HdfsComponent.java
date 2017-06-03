/**
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
package org.apache.camel.component.hdfs2;

import java.net.URL;
import java.util.Map;
import javax.security.auth.login.Configuration;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.hadoop.fs.FsUrlStreamHandlerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HdfsComponent extends UriEndpointComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HdfsComponent.class);

    public HdfsComponent() {
        super(HdfsEndpoint.class);
        initHdfs();
    }

    public HdfsComponent(CamelContext context) {
        super(context, HdfsEndpoint.class);
        initHdfs();
    }

    protected final Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HdfsEndpoint hdfsEndpoint = new HdfsEndpoint(uri, this.getCamelContext());
        setProperties(hdfsEndpoint.getConfig(), parameters);
        return hdfsEndpoint;
    }

    protected void initHdfs() {
        try {
            URL.setURLStreamHandlerFactory(new FsUrlStreamHandlerFactory());
        } catch (Throwable e) {
            // ignore as its most likely already set
            LOG.debug("Cannot set URLStreamHandlerFactory due " + e.getMessage() + ". This exception will be ignored.", e);
        }
    }

    static Configuration getJAASConfiguration() {
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
    static void setJAASConfiguration(Configuration auth) {
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

}
