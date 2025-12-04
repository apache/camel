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

package org.apache.camel.component.hashicorp.vault.vault;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.hashicorp.vault.HashicorpVaultPropertiesFunction;
import org.apache.camel.spi.ContextReloadStrategy;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.annotations.PeriodicTask;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * Period task which checks if Hashicorp Vault secrets has been updated and can trigger Camel to be reloaded.
 */
@PeriodicTask("hashicorp-secret-refresh")
public class HashicorpVaultReloadTriggerTask extends ServiceSupport implements CamelContextAware, Runnable {

    private static final String CAMEL_HASHICORP_VAULT_TOKEN_ENV = "CAMEL_HASHICORP_VAULT_TOKEN";
    private static final String CAMEL_HASHICORP_VAULT_HOST_ENV = "CAMEL_HASHICORP_VAULT_HOST";
    private static final String CAMEL_HASHICORP_VAULT_PORT_ENV = "CAMEL_HASHICORP_VAULT_PORT";
    private static final String CAMEL_HASHICORP_VAULT_SCHEME_ENV = "CAMEL_HASHICORP_VAULT_SCHEME";
    private static final String CAMEL_HASHICORP_VAULT_CLOUD_ENV = "CAMEL_HASHICORP_VAULT_CLOUD";
    private static final String CAMEL_HASHICORP_VAULT_NAMESPACE_ENV = "CAMEL_HASHICORP_VAULT_NAMESPACE";

    private static final Logger LOG = LoggerFactory.getLogger(HashicorpVaultReloadTriggerTask.class);

    private CamelContext camelContext;
    private boolean reloadEnabled = true;
    private String secrets;
    private VaultTemplate client;
    private HashicorpVaultPropertiesFunction propertiesFunction;
    private volatile Instant lastCheckTime;
    private volatile Instant lastReloadTime;
    private final Map<String, Instant> updates = new HashMap<>();
    private final Map<String, Integer> versionsMap = new HashMap<>();
    private boolean cloud;
    private String namespace;

    public HashicorpVaultReloadTriggerTask() {}

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public boolean isReloadEnabled() {
        return reloadEnabled;
    }

    /**
     * Whether Camel should be reloaded on Hashicorp Vault secret updated
     */
    public void setReloadEnabled(boolean reloadEnabled) {
        this.reloadEnabled = reloadEnabled;
    }

    /**
     * A map of the updated secrets with the latest updated time.
     */
    public Map<String, Instant> getUpdates() {
        return Collections.unmodifiableMap(updates);
    }

    /**
     * Last time this task checked Hashicorp Vault for updated secrets.
     */
    public Instant getLastCheckTime() {
        return lastCheckTime;
    }

    /**
     * Last time Hashicorp Vault secrets update triggered reload.
     */
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // auto-detect secrets in-use
        PropertiesComponent pc = camelContext.getPropertiesComponent();
        PropertiesFunction pf = pc.getPropertiesFunction("hashicorp");
        if (pf instanceof HashicorpVaultPropertiesFunction) {
            propertiesFunction = (HashicorpVaultPropertiesFunction) pf;
            LOG.debug("Auto-detecting secrets from properties-function: {}", pf.getName());
        }
        // specific secrets
        HashicorpVaultConfiguration hashicorpVaultConfiguration =
                getCamelContext().getVaultConfiguration().hashicorp();
        secrets = hashicorpVaultConfiguration.getSecrets();
        if (ObjectHelper.isEmpty(secrets) && propertiesFunction == null) {
            throw new IllegalArgumentException("Secrets must be configured on Hashicorp vault configuration");
        }

        String token = System.getenv(CAMEL_HASHICORP_VAULT_TOKEN_ENV);
        String host = System.getenv(CAMEL_HASHICORP_VAULT_HOST_ENV);
        String port = System.getenv(CAMEL_HASHICORP_VAULT_PORT_ENV);
        String scheme = System.getenv(CAMEL_HASHICORP_VAULT_SCHEME_ENV);
        if (System.getenv(CAMEL_HASHICORP_VAULT_CLOUD_ENV) != null) {
            cloud = Boolean.parseBoolean(System.getenv(CAMEL_HASHICORP_VAULT_CLOUD_ENV));
        }
        namespace = System.getenv(CAMEL_HASHICORP_VAULT_NAMESPACE_ENV);

        if (ObjectHelper.isEmpty(token)
                && ObjectHelper.isEmpty(host)
                && ObjectHelper.isEmpty(port)
                && ObjectHelper.isEmpty(scheme)
                && ObjectHelper.isEmpty(namespace)) {
            if (ObjectHelper.isNotEmpty(hashicorpVaultConfiguration)) {
                token = hashicorpVaultConfiguration.getToken();
                host = hashicorpVaultConfiguration.getHost();
                port = hashicorpVaultConfiguration.getPort();
                scheme = hashicorpVaultConfiguration.getScheme();
                cloud = hashicorpVaultConfiguration.isCloud();
                if (hashicorpVaultConfiguration.isCloud()) {
                    namespace = hashicorpVaultConfiguration.getNamespace();
                }
            }
        }

        if (ObjectHelper.isNotEmpty(token)
                && ObjectHelper.isNotEmpty(host)
                && ObjectHelper.isNotEmpty(port)
                && ObjectHelper.isNotEmpty(scheme)) {
            VaultEndpoint vaultEndpoint = new VaultEndpoint();
            vaultEndpoint.setHost(host);
            vaultEndpoint.setPort(Integer.parseInt(port));
            vaultEndpoint.setScheme(scheme);

            client = new VaultTemplate(vaultEndpoint, new TokenAuthentication(token));
        } else {
            throw new RuntimeCamelException(
                    "Using the Hashicorp Vault Secrets Refresh Task requires setting Token, Host, port and scheme properties");
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();

        client = null;
        updates.clear();
        versionsMap.clear();
    }

    @Override
    public void run() {
        lastCheckTime = Instant.now();
        boolean triggerReloading = false;

        try {
            // Get set of secrets to check
            Set<String> secretsToCheck = new HashSet<>();
            if (secrets != null) {
                Collections.addAll(secretsToCheck, secrets.split(","));
            }
            if (propertiesFunction != null) {
                secretsToCheck.addAll(propertiesFunction.getSecrets());
            }

            // Check each secret for updates
            for (String secretName : secretsToCheck) {
                if (matchSecret(secretName)) {
                    try {
                        // Query metadata endpoint to get current version
                        String metadataPath = buildMetadataPath(secretName);
                        VaultResponse response = client.read(metadataPath);

                        if (response != null && response.getData() != null) {
                            Object currentVersionObj = response.getData().get("current_version");
                            if (currentVersionObj != null) {
                                Integer currentVersion = Integer.valueOf(currentVersionObj.toString());
                                Integer lastKnownVersion = versionsMap.get(secretName);

                                if (lastKnownVersion == null) {
                                    // First time seeing this secret, just record the version
                                    versionsMap.put(secretName, currentVersion);
                                    LOG.debug("Tracking secret {} at version {}", secretName, currentVersion);
                                } else if (!currentVersion.equals(lastKnownVersion)) {
                                    // Version changed, trigger reload
                                    versionsMap.put(secretName, currentVersion);
                                    updates.put(secretName, Instant.now());
                                    if (isReloadEnabled()) {
                                        LOG.info(
                                                "Update for Hashicorp Vault secret: {} detected (version {} -> {}), triggering CamelContext reload",
                                                secretName,
                                                lastKnownVersion,
                                                currentVersion);
                                        triggerReloading = true;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn(
                                "Error checking secret {} for updates: {}. Will retry on next run.",
                                secretName,
                                e.getMessage());
                        LOG.debug("Exception details:", e);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn(
                    "Error during Hashicorp Vault Secrets Refresh Task due to {}. This exception is ignored. Will try again on next run.",
                    e.getMessage(),
                    e);
        }

        if (triggerReloading) {
            ContextReloadStrategy reload = camelContext.hasService(ContextReloadStrategy.class);
            if (reload != null) {
                // trigger reload
                lastReloadTime = Instant.now();
                reload.onReload(this);
            }
        }
    }

    protected boolean matchSecret(String name) {
        Set<String> set = new HashSet<>();
        if (secrets != null) {
            Collections.addAll(set, secrets.split(","));
        }
        if (propertiesFunction != null) {
            set.addAll(propertiesFunction.getSecrets());
        }

        for (String part : set) {
            boolean result = name.contains(part) || PatternHelper.matchPattern(name, part);
            LOG.trace("Matching secret id: {}={} -> {}", name, part, result);
            if (result) {
                return true;
            }
        }

        return false;
    }

    private String buildMetadataPath(String secretName) {
        // Extract engine and secret name from the secret identifier
        // Format can be "engine:secretname" or just "secretname" (defaults to "secret" engine)
        // Use ':' as separator since secret names can contain '/'
        String engine = "secret"; // default engine
        String actualSecretName = secretName;

        // Check if secretName contains ':' which separates engine from secret path
        if (secretName.contains(":")) {
            int colonIndex = secretName.indexOf(':');
            engine = secretName.substring(0, colonIndex);
            actualSecretName = secretName.substring(colonIndex + 1);
        }

        String metadataPath;
        if (!cloud) {
            metadataPath = engine + "/" + "metadata" + "/" + actualSecretName;
        } else {
            if (ObjectHelper.isNotEmpty(namespace)) {
                metadataPath = namespace + "/" + engine + "/" + "metadata" + "/" + actualSecretName;
            } else {
                metadataPath = engine + "/" + "metadata" + "/" + actualSecretName;
            }
        }
        return metadataPath;
    }

    @Override
    public String toString() {
        return "Hashicorp Vault Secrets Refresh Task";
    }
}
