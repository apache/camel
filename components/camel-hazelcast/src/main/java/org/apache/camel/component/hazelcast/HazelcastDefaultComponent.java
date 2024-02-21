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
package org.apache.camel.component.hazelcast;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_CONFIGU_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_CONFIGU_URI_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_INSTANCE_NAME_PARAM;
import static org.apache.camel.component.hazelcast.HazelcastConstants.HAZELCAST_INSTANCE_PARAM;

public abstract class HazelcastDefaultComponent extends DefaultComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastDefaultComponent.class);

    private final Set<HazelcastInstance> customHazelcastInstances;
    @Metadata(label = "advanced")
    private HazelcastInstance hazelcastInstance;
    @Metadata(label = "advanced", defaultValue = "" + HazelcastConstants.HAZELCAST_NODE_MODE)
    private String hazelcastMode = HazelcastConstants.HAZELCAST_NODE_MODE;

    protected HazelcastDefaultComponent() {
        this.customHazelcastInstances = new LinkedHashSet<>();
    }

    protected HazelcastDefaultComponent(final CamelContext context) {
        super(context);
        this.customHazelcastInstances = new LinkedHashSet<>();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        // use the given hazelcast Instance or create one if not given
        HazelcastInstance hzInstance;
        if (ObjectHelper.equal(getHazelcastMode(), HazelcastConstants.HAZELCAST_NODE_MODE)) {
            hzInstance = getOrCreateHzInstance(getCamelContext(), parameters);
        } else {
            hzInstance = getOrCreateHzClientInstance(getCamelContext(), parameters);
        }

        String defaultOperation
                = getAndRemoveOrResolveReferenceParameter(parameters, HazelcastConstants.OPERATION_PARAM, String.class);
        if (defaultOperation == null) {
            defaultOperation = getAndRemoveOrResolveReferenceParameter(parameters, "defaultOperation", String.class);
        }

        HazelcastDefaultEndpoint endpoint = doCreateEndpoint(uri, remaining, parameters, hzInstance);
        if (defaultOperation != null) {
            endpoint.setDefaultOperation(HazelcastOperation.getHazelcastOperation(defaultOperation));
        }

        setProperties(endpoint, parameters);

        return endpoint;
    }

    protected abstract HazelcastDefaultEndpoint doCreateEndpoint(
            String uri, String remaining, Map<String, Object> parameters, HazelcastInstance hzInstance)
            throws Exception;

    @Override
    public void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public void doStop() throws Exception {
        for (HazelcastInstance hazelcastInstance : customHazelcastInstances) {
            hazelcastInstance.getLifecycleService().shutdown();
        }

        customHazelcastInstances.clear();

        super.doStop();
    }

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    /**
     * The hazelcast instance reference which can be used for hazelcast endpoint. If you don't specify the instance
     * reference, camel use the default hazelcast instance from the camel-hazelcast instance.
     */
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    public String getHazelcastMode() {
        return hazelcastMode;
    }

    /**
     * The hazelcast mode reference which kind of instance should be used. If you don't specify the mode, then the node
     * mode will be the default.
     */
    public void setHazelcastMode(String hazelcastMode) {
        this.hazelcastMode = hazelcastMode;
    }

    protected HazelcastInstance getOrCreateHzInstance(CamelContext context, Map<String, Object> parameters) throws Exception {
        HazelcastInstance hzInstance = null;
        Config config = null;

        // Query param named 'hazelcastInstance' (if exists) overrides the instance that was set
        hzInstance = resolveAndRemoveReferenceParameter(parameters, HAZELCAST_INSTANCE_PARAM, HazelcastInstance.class);

        // Check if an already created instance is given then just get instance by its name.
        if (hzInstance == null && parameters.get(HAZELCAST_INSTANCE_NAME_PARAM) != null) {
            hzInstance = Hazelcast.getHazelcastInstanceByName((String) parameters.get(HAZELCAST_INSTANCE_NAME_PARAM));
        }

        // If instance neither supplied nor found by name, try to lookup its config
        // as reference or as xml configuration file.
        if (hzInstance == null) {
            config = resolveAndRemoveReferenceParameter(parameters, HAZELCAST_CONFIGU_PARAM, Config.class);
            if (config == null) {
                String configUri = getAndRemoveParameter(parameters, HAZELCAST_CONFIGU_URI_PARAM, String.class);
                if (configUri != null) {
                    configUri = getCamelContext().resolvePropertyPlaceholders(configUri);
                }
                if (configUri != null) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, configUri);
                    config = new XmlConfigBuilder(is).build();
                }
            }

            if (hazelcastInstance == null && config == null) {
                config = new XmlConfigBuilder().build();
                // Disable the version check
                config.getProperties().setProperty("hazelcast.version.check.enabled", "false");
                config.getProperties().setProperty("hazelcast.phone.home.enabled", "false");

                hzInstance = Hazelcast.newHazelcastInstance(config);
            } else if (config != null) {
                if (ObjectHelper.isNotEmpty(config.getInstanceName())) {
                    hzInstance = Hazelcast.getOrCreateHazelcastInstance(config);
                } else {
                    hzInstance = Hazelcast.newHazelcastInstance(config);
                }
            }

            if (hzInstance != null) {
                if (this.customHazelcastInstances.add(hzInstance)) {
                    LOGGER.debug("Add managed HZ instance {}", hzInstance.getName());
                }
            }
        }

        return hzInstance == null ? hazelcastInstance : hzInstance;
    }

    protected HazelcastInstance getOrCreateHzClientInstance(CamelContext context, Map<String, Object> parameters)
            throws Exception {
        HazelcastInstance hzInstance = null;
        ClientConfig config = null;

        // Query param named 'hazelcastInstance' (if exists) overrides the instance that was set
        hzInstance = resolveAndRemoveReferenceParameter(parameters, HAZELCAST_INSTANCE_PARAM, HazelcastInstance.class);

        // Check if an already created instance is given then just get instance by its name.
        if (hzInstance == null && parameters.get(HAZELCAST_INSTANCE_NAME_PARAM) != null) {
            hzInstance = HazelcastClient.getHazelcastClientByName((String) parameters.get(HAZELCAST_INSTANCE_NAME_PARAM));
        }

        // If instance neither supplied nor found by name, try to lookup its config
        // as reference or as xml configuration file.
        if (hzInstance == null) {
            config = resolveAndRemoveReferenceParameter(parameters, HAZELCAST_CONFIGU_PARAM, ClientConfig.class);
            if (config == null) {
                String configUri = getAndRemoveParameter(parameters, HAZELCAST_CONFIGU_URI_PARAM, String.class);
                if (configUri != null) {
                    configUri = getCamelContext().resolvePropertyPlaceholders(configUri);
                }
                if (configUri != null) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, configUri);
                    config = new XmlClientConfigBuilder(is).build();
                }
            }

            if (hazelcastInstance == null && config == null) {
                config = new XmlClientConfigBuilder().build();
                // Disable the version check
                config.getProperties().setProperty("hazelcast.version.check.enabled", "false");
                config.getProperties().setProperty("hazelcast.phone.home.enabled", "false");

                hzInstance = HazelcastClient.newHazelcastClient(config);
            } else if (config != null) {
                hzInstance = HazelcastClient.newHazelcastClient(config);
            }

            if (hzInstance != null) {
                if (this.customHazelcastInstances.add(hzInstance)) {
                    LOGGER.debug("Add managed HZ instance {}", hzInstance.getName());
                }
            }
        }

        return hzInstance == null ? hazelcastInstance : hzInstance;
    }
}
