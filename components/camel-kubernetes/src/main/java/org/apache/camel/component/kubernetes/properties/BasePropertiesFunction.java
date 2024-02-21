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
package org.apache.camel.component.kubernetes.properties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.LocationHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.SensitiveUtils;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for kubernetes {@link PropertiesFunction}.
 */
abstract class BasePropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    // keys in application.properties
    public static final String CLIENT_ENABLED = "camel.kubernetes-config.client-enabled";
    public static final String LOCAL_MODE = "camel.kubernetes-config.local-mode";
    public static final String MOUNT_PATH_CONFIGMAPS = "camel.kubernetes-config.mount-path-configmaps";
    public static final String MOUNT_PATH_SECRETS = "camel.kubernetes-config.mount-path-secrets";

    // use camel-k ENV for mount paths
    public static final String JVM_PROP_MOUNT_PATH_CONFIGMAPS = "camel.k.mount-path.configmaps";
    public static final String ENV_MOUNT_PATH_CONFIGMAPS = "CAMEL_K_MOUNT_PATH_CONFIGMAPS";
    public static final String JVM_PROP_MOUNT_PATH_SECRETS = "camel.k.mount-path.secrets";
    public static final String ENV_MOUNT_PATH_SECRETS = "CAMEL_K_MOUNT_PATH_SECRETS";
    private static final Logger LOG = LoggerFactory.getLogger(BasePropertiesFunction.class);

    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private CamelContext camelContext;
    private KubernetesClient client;
    private Boolean localMode;
    private Boolean clientEnabled;
    private String mountPathConfigMaps;
    private String mountPathSecrets;

    @Override
    protected void doInit() {
        ObjectHelper.notNull(camelContext, "CamelContext");
        if (localMode == null) {
            localMode = "true"
                    .equalsIgnoreCase(camelContext.getPropertiesComponent().resolveProperty(LOCAL_MODE).orElse("false"));
        }
        if (!localMode) {
            doInitKubernetesClient();
        }
    }

    protected void doInitKubernetesClient() {
        if (clientEnabled == null) {
            clientEnabled = "true"
                    .equalsIgnoreCase(camelContext.getPropertiesComponent().resolveProperty(CLIENT_ENABLED).orElse("true"));
        }
        if (mountPathConfigMaps == null) {
            mountPathConfigMaps = camelContext.getPropertiesComponent().resolveProperty(MOUNT_PATH_CONFIGMAPS)
                    .orElseGet(
                            () -> System.getProperty(JVM_PROP_MOUNT_PATH_CONFIGMAPS, System.getenv(ENV_MOUNT_PATH_CONFIGMAPS)));
        }
        if (mountPathSecrets == null) {
            mountPathSecrets = camelContext.getPropertiesComponent().resolveProperty(MOUNT_PATH_SECRETS)
                    .orElseGet(() -> System.getProperty(JVM_PROP_MOUNT_PATH_SECRETS, System.getenv(ENV_MOUNT_PATH_SECRETS)));
        }
        if (clientEnabled && client == null) {
            client = CamelContextHelper.findSingleByType(camelContext, KubernetesClient.class);
        }
        if (clientEnabled && client == null) {
            // try to auto-configure via properties
            PropertiesComponent pc = camelContext.getPropertiesComponent();
            OrderedLocationProperties properties = (OrderedLocationProperties) pc
                    .loadProperties(k -> k.startsWith("camel.kubernetes-config.client."),
                            k -> k.replace("camel.kubernetes-config.client.", ""));
            if (!properties.isEmpty()) {
                ConfigBuilder config = new ConfigBuilder();

                PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(camelContext)
                        .resolvePropertyConfigurer(ConfigBuilder.class.getName(), camelContext);

                // use copy to keep track of which options was configured or not
                OrderedLocationProperties copy = new OrderedLocationProperties();
                copy.putAll(properties);

                PropertyBindingSupport.build()
                        .withProperties(copy.asMap())
                        .withFluentBuilder(true)
                        .withIgnoreCase(true)
                        .withReflection(false)
                        .withConfigurer(configurer)
                        .withTarget(config)
                        .withCamelContext(camelContext)
                        .withRemoveParameters(true)
                        .bind();
                if (!copy.isEmpty()) {
                    // some options were not possible to configure
                    for (var e : copy.entrySet()) {
                        properties.remove(e.getKey());
                    }
                }
                client = new KubernetesClientBuilder().withConfig(config.build()).build();
                LOG.info("Auto-configuration KubernetesClient summary");
                for (var entry : properties.entrySet()) {
                    String k = entry.getKey().toString();
                    Object v = entry.getValue();
                    String loc = LocationHelper.locationSummary(properties, k);
                    if (SensitiveUtils.containsSensitive(k)) {
                        LOG.info("    {} {}=xxxxxx", loc, k);
                    } else {
                        LOG.info("    {} {}={}", loc, k, v);
                    }
                }
                if (!copy.isEmpty()) {
                    for (var e : copy.entrySet()) {
                        LOG.warn("Property not auto-configured: camel.kubernetes-config.client.{}={}", e.getKey(),
                                e.getValue());
                    }
                }
            } else {
                // create a default client to use
                client = new KubernetesClientBuilder().build();
                LOG.debug("Created default KubernetesClient (auto-configured by itself)");
            }
            // add to registry so the client can be reused
            camelContext.getRegistry().bind("camelKubernetesClient", client);
        }

        if (clientEnabled && client == null && getMountPath() == null) {
            throw new IllegalArgumentException("Either a mount path or the Kubernetes Client must be configured");
        }
        if (!clientEnabled && getMountPath() == null) {
            throw new IllegalArgumentException("Mount path must be configured");
        }
        if (client != null && LOGGED.compareAndSet(false, true)) {
            // only log once
            LOG.info("KubernetesClient using masterUrl: {} with namespace: {}", client.getMasterUrl(), client.getNamespace());
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public KubernetesClient getClient() {
        return client;
    }

    /**
     * To use an existing kubernetes client to use
     */
    public void setClient(KubernetesClient client) {
        this.client = client;
    }

    public boolean isClientEnabled() {
        return clientEnabled;
    }

    /**
     * Whether to use KubernetesClient to lookup from the Kubernetes API server. Is by default enabled.
     */
    public void setClientEnabled(boolean clientEnabled) {
        this.clientEnabled = clientEnabled;
    }

    public String getMountPathConfigMaps() {
        return mountPathConfigMaps;
    }

    /**
     * To use a volume mount to load configmaps (first), and fallback to using the Kubernetes API server
     */
    public void setMountPathConfigMaps(String mountPathConfigMaps) {
        this.mountPathConfigMaps = mountPathConfigMaps;
    }

    public String getMountPathSecrets() {
        return mountPathSecrets;
    }

    /**
     * To use a volume mount to load secrets (first), and fallback to using the Kubernetes API server
     */
    public void setMountPathSecrets(String mountPathSecrets) {
        this.mountPathSecrets = mountPathSecrets;
    }

    @Override
    public String apply(String remainder) {
        String defaultValue = StringHelper.after(remainder, ":");
        String name = StringHelper.before(remainder, "/");
        String key = StringHelper.after(remainder, "/");
        if (name == null || key == null) {
            return defaultValue;
        }

        // local-mode will not lookup in kubernetes but as local properties
        if (localMode) {
            String localKey = name + "/" + key;
            return getCamelContext().getPropertiesComponent().resolveProperty(localKey).orElse(defaultValue);
        }

        String answer = null;
        Path root = getMountPath();
        if (root != null) {
            Path file = root.resolve(name.toLowerCase(Locale.US)).resolve(key);
            if (Files.exists(file) && !Files.isDirectory(file)) {
                try {
                    answer = Files.readString(file, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        if (answer == null && client != null) {
            answer = lookup(name, key, defaultValue);
        }
        if (answer == null) {
            answer = defaultValue;
        }

        return answer;
    }

    abstract Path getMountPath();

    abstract String lookup(String name, String key, String defaultValue);
}
