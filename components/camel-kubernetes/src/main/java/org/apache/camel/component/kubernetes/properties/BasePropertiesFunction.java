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

import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Base for kubernetes {@link PropertiesFunction}.
 */
abstract class BasePropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    // keys in application.properties for mount paths
    public static final String MOUNT_PATH_CONFIGMAPS = "org.apache.camel.component.kubernetes.properties.mount-path-configmaps";
    public static final String MOUNT_PATH_SECRETS = "org.apache.camel.component.kubernetes.properties.mount-path-secrets";

    // use camel-k ENV for mount paths
    public static final String ENV_MOUNT_PATH_CONFIGMAPS = "camel.k.mount-path.configmaps";
    public static final String ENV_MOUNT_PATH_SECRETS = "camel.k.mount-path.secrets";

    private CamelContext camelContext;
    private KubernetesClient client;
    private String mountPathConfigMaps;
    private String mountPathSecrets;

    @Override
    protected void doInit() throws Exception {
        ObjectHelper.notNull(camelContext, "CamelContext");
        if (mountPathConfigMaps == null) {
            mountPathConfigMaps = camelContext.getPropertiesComponent().resolveProperty(MOUNT_PATH_CONFIGMAPS)
                    .orElseGet(() -> System.getProperty(ENV_MOUNT_PATH_CONFIGMAPS, System.getenv(ENV_MOUNT_PATH_CONFIGMAPS)));
        }
        if (mountPathSecrets == null) {
            mountPathSecrets = camelContext.getPropertiesComponent().resolveProperty(MOUNT_PATH_SECRETS)
                    .orElseGet(() -> System.getProperty(ENV_MOUNT_PATH_SECRETS, System.getenv(ENV_MOUNT_PATH_SECRETS)));
        }
        if (client == null) {
            client = CamelContextHelper.findSingleByType(camelContext, KubernetesClient.class);
        }
        if (client == null && getMountPath() == null) {
            throw new IllegalArgumentException("Either a mount path or the Kubernetes Client must be configured");
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

    public String getMountPathConfigMaps() {
        return mountPathConfigMaps;
    }

    /**
     * To use a volume mount to load configmaps, instead of using the Kubernetes API server
     */
    public void setMountPathConfigMaps(String mountPathConfigMaps) {
        this.mountPathConfigMaps = mountPathConfigMaps;
    }

    public String getMountPathSecrets() {
        return mountPathSecrets;
    }

    /**
     * To use a volume mount to load secrets, instead of using the Kubernetes API server.
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
