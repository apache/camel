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
package org.apache.camel.component.infinispan.embedded;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.infinispan.InfinispanComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(InfinispanEmbeddedComponent.SCHEME)
public class InfinispanEmbeddedComponent extends InfinispanComponent {
    public static final String SCHEME = "infinispan-embedded";

    private static final Logger LOG = LoggerFactory.getLogger(InfinispanEmbeddedComponent.class);

    @Metadata(description = "The configuration of the component, which each endpoint copies and then changes with its own"
                            + " options. The cache manager of the component uses only this configuration.")
    private InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();

    private InfinispanEmbeddedManager manager;

    public InfinispanEmbeddedComponent() {
    }

    public InfinispanEmbeddedComponent(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfinispanEmbeddedConfiguration conf = configuration.clone();

        InfinispanEmbeddedEndpoint endpoint = new InfinispanEmbeddedEndpoint(uri, remaining, this, conf);
        setProperties(endpoint, parameters);
        warnAboutIgnoredOptions(uri, conf);
        return endpoint;
    }

    /**
     * All the endpoints use the one cache manager of the component, which is created from the configuration of the
     * component, and its flags. The values of the endpoint for these options are not used.
     */
    private void warnAboutIgnoredOptions(String uri, InfinispanEmbeddedConfiguration conf) {
        List<String> ignored = new ArrayList<>();
        if (conf.getCacheContainer() != configuration.getCacheContainer()) {
            ignored.add("cacheContainer");
        }
        if (conf.getCacheContainerConfiguration() != configuration.getCacheContainerConfiguration()) {
            ignored.add("cacheContainerConfiguration");
        }
        if (!Objects.equals(conf.getConfigurationUri(), configuration.getConfigurationUri())) {
            ignored.add("configurationUri");
        }
        if (!Arrays.equals(conf.getFlags(), configuration.getFlags())) {
            ignored.add("flags");
        }
        if (!ignored.isEmpty()) {
            LOG.warn("The options {} of endpoint {} are not used: every infinispan-embedded endpoint uses the cache"
                     + " manager and the flags of the component. Set them on the component instead.",
                    ignored, URISupport.sanitizeUri(uri));
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        // the CamelContext lets the manager read the configurationUri with the resource loader of Camel
        manager = new InfinispanEmbeddedManager(getCamelContext(), configuration);
        ServiceHelper.initService(manager);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(manager);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(manager);
    }

    public InfinispanEmbeddedManager getManager() {
        return manager;
    }

    public void setConfiguration(InfinispanEmbeddedConfiguration configuration) {
        this.configuration = configuration;
    }

    public InfinispanEmbeddedConfiguration getConfiguration() {
        return configuration;
    }
}
