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
package org.apache.camel.component.hashicorp.vault;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * A {@link PropertiesFunction} that lookup the property value from Hashicorp Vault instance.
 * <p/>
 * The location of Hashicorp Vault, and how to log in to it, is defined using environment variables:
 * <ul>
 * <li><tt>CAMEL_HASHICORP_VAULT_HOST</tt></li>
 * <li><tt>CAMEL_HASHICORP_VAULT_PORT</tt></li>
 * <li><tt>CAMEL_HASHICORP_VAULT_SCHEME</tt></li>
 * <li><tt>CAMEL_HASHICORP_VAULT_AUTHENTICATION</tt>: token (the default), approle, kubernetes, jwt, cert, aws-iam,
 * azure-msi or gcp-gce</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_TOKEN</tt>, for the token login</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_ROLE_ID</tt> and <tt>CAMEL_HASHICORP_VAULT_SECRET_ID</tt>, for the approle login</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_ROLE</tt>, the Vault role to log in as, for the kubernetes, jwt, cert and cloud
 * logins</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_JWT</tt> and <tt>CAMEL_HASHICORP_VAULT_JWT_FILE</tt>, for the kubernetes and jwt
 * logins</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_AUTH_MOUNT_PATH</tt>, when the login method is not at its default path</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_TRUST_STORE</tt> and <tt>CAMEL_HASHICORP_VAULT_TRUST_STORE_PASSWORD</tt>, the
 * certificates to trust for TLS</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_KEY_STORE</tt> and <tt>CAMEL_HASHICORP_VAULT_KEY_STORE_PASSWORD</tt>, the TLS client
 * certificate, for the cert login</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_AWS_IAM_SERVER_ID</tt>, <tt>CAMEL_HASHICORP_VAULT_AWS_REGION</tt> and
 * <tt>CAMEL_HASHICORP_VAULT_AWS_STS_ENDPOINT</tt>, for the aws-iam login</li>
 * <li><tt>CAMEL_HASHICORP_VAULT_AZURE_RESOURCE</tt> and <tt>CAMEL_HASHICORP_VAULT_AZURE_CLIENT_ID</tt>, for the
 * azure-msi login</li>
 * </ul>
 * <p/>
 *
 * Otherwise it is possible to specify them as properties:
 *
 * <ul>
 * <li><tt>camel.vault.hashicorp.host</tt></li>
 * <li><tt>camel.vault.hashicorp.port</tt></li>
 * <li><tt>camel.vault.hashicorp.scheme</tt></li>
 * <li><tt>camel.vault.hashicorp.authentication</tt></li>
 * <li><tt>camel.vault.hashicorp.token</tt></li>
 * <li><tt>camel.vault.hashicorp.roleId</tt> and <tt>camel.vault.hashicorp.secretId</tt></li>
 * <li><tt>camel.vault.hashicorp.role</tt>, <tt>camel.vault.hashicorp.jwt</tt> and
 * <tt>camel.vault.hashicorp.jwtFile</tt></li>
 * <li><tt>camel.vault.hashicorp.authMountPath</tt></li>
 * <li><tt>camel.vault.hashicorp.trustStore</tt> and <tt>camel.vault.hashicorp.trustStorePassword</tt></li>
 * <li><tt>camel.vault.hashicorp.keyStore</tt> and <tt>camel.vault.hashicorp.keyStorePassword</tt></li>
 * <li><tt>camel.vault.hashicorp.awsIamServerId</tt>, <tt>camel.vault.hashicorp.awsRegion</tt> and
 * <tt>camel.vault.hashicorp.awsStsEndpoint</tt></li>
 * <li><tt>camel.vault.hashicorp.azureResource</tt> and <tt>camel.vault.hashicorp.azureClientId</tt></li>
 * </ul>
 * <p/>
 *
 * The function renews its token before it expires, and logs in again when the token can no longer be renewed. A
 * {@link VaultTemplate} given to the function is used as it is.
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>hashicorp:</tt>. For example asking for
 * <tt>hashicorp:engine:token</tt>, will return the secret value associated to the secret named token on Hashicorp Vault
 * instance on the engine 'engine'. When no engine is given, such as <tt>hashicorp:token</tt>, the 'secret' engine is
 * used.
 *
 * Another way of retrieving a secret value is using the following notation <tt>hashicorp:engine:database#username</tt>:
 * in this case the field username of the secret database will be returned. As a fallback, the user could provide a
 * default value, which will be returned in case the secret doesn't exist, the secret has been marked for deletion or,
 * for example, if a particular field of the secret doesn't exist. For using this feature, the user could use the
 * following notation <tt>hashicorp:engine:database#username:admin</tt>. The admin value will be returned as default
 * value, if the conditions above were all met.
 */

@org.apache.camel.spi.annotations.PropertiesFunction("hashicorp")
public class HashicorpVaultPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(HashicorpVaultPropertiesFunction.class);

    private static final String DEFAULT_ENGINE = "secret";
    private CamelContext camelContext;
    private volatile VaultTemplate client;
    // the client the function created, which it closes on stop (a client given to the function is not closed)
    private HashicorpVaultClient vaultClient;
    private final Set<String> secrets = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> secretVersions = new ConcurrentHashMap<>();

    private String namespace;
    private boolean cloud;

    public HashicorpVaultPropertiesFunction() {
        super();
    }

    public HashicorpVaultPropertiesFunction(VaultTemplate client) {
        super();
        this.client = client;
    }

    HashicorpVaultPropertiesFunction(VaultTemplate client, boolean cloud, String namespace) {
        this(client);
        this.cloud = cloud;
        this.namespace = namespace;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        HashicorpVaultConfiguration hashicorpVaultConfiguration
                = getCamelContext().getVaultConfiguration().getHashicorpVaultConfiguration();
        HashicorpVaultClientSettings settings
                = HashicorpVaultClientSettings.fromEnvironmentOrConfiguration(hashicorpVaultConfiguration);
        cloud = settings.isCloud();
        namespace = settings.getNamespace();
        if (client == null) {
            // log in as configured, unless the function was given the client to use
            try {
                vaultClient = HashicorpVaultClient.create(getCamelContext(), this, settings);
            } catch (IllegalArgumentException e) {
                throw new RuntimeCamelException("Cannot use the Hashicorp Properties Function: " + e.getMessage(), e);
            }
            client = vaultClient.getVaultTemplate();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (vaultClient != null) {
            // stop renewing the token, and revoke the token of the login
            vaultClient.close();
            vaultClient = null;
            client = null;
        }
        super.doStop();
    }

    @Override
    public String getName() {
        return "hashicorp";
    }

    @Override
    public boolean isSensitive() {
        // the values are secrets
        return true;
    }

    @Override
    public String apply(String remainder) {
        // the function is shared by all lookups (also concurrent ones), so the parsed parts must be local
        String engine = DEFAULT_ENGINE;
        String key = remainder;
        String subkey = null;
        String returnValue = null;
        String defaultValue = null;
        String version = null;
        if (remainder.contains("#")) {
            String keyRemainder = StringHelper.before(remainder, "#");
            if (keyRemainder.contains(":")) {
                engine = StringHelper.before(keyRemainder, ":");
                key = StringHelper.after(keyRemainder, ":");
            } else {
                key = keyRemainder;
            }
            subkey = StringHelper.after(remainder, "#");
            defaultValue = StringHelper.after(subkey, ":");
            if (ObjectHelper.isNotEmpty(defaultValue)) {
                if (defaultValue.contains("@")) {
                    version = StringHelper.after(defaultValue, "@");
                    defaultValue = StringHelper.before(defaultValue, "@");
                }
            }
            if (subkey.contains(":")) {
                subkey = StringHelper.before(subkey, ":");
            }
            if (subkey.contains("@")) {
                version = StringHelper.after(subkey, "@");
                subkey = StringHelper.before(subkey, "@");
            }
        } else if (remainder.contains(":")) {
            engine = StringHelper.before(remainder, ":");
            key = StringHelper.after(remainder, ":");
            if (key.contains(":")) {
                defaultValue = StringHelper.after(key, ":");
                if (ObjectHelper.isNotEmpty(defaultValue)) {
                    if (defaultValue.contains("@")) {
                        version = StringHelper.after(defaultValue, "@");
                        defaultValue = StringHelper.before(defaultValue, "@");
                    }
                }
                if (key.contains(":")) {
                    key = StringHelper.before(key, ":");
                }
                if (key.contains("@")) {
                    version = StringHelper.after(key, "@");
                    key = StringHelper.before(key, "@");
                }
            } else {
                if (key.contains("@")) {
                    version = StringHelper.after(key, "@");
                    key = StringHelper.before(key, "@");
                }
            }
        } else if (remainder.contains("@")) {
            version = StringHelper.after(remainder, "@");
            key = StringHelper.before(remainder, "@");
        }

        if (key != null) {
            try {
                returnValue = getSecretFromSource(engine, key, subkey, defaultValue, version);
            } catch (Exception e) {
                throw new RuntimeCamelException(
                        "Error getting secret from vault using key: " + key + " due to: " + e.getMessage(), e);
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(String engine, String key, String subkey, String defaultValue, String version) {
        // capture name of secret in the form the refresh task reads the metadata of
        String secretName = trackedSecretName(engine, key);
        secrets.add(secretName);

        String returnValue = null;
        try {
            String completePath = engine + "/" + "data" + "/" + key;
            if (cloud && ObjectHelper.isNotEmpty(namespace)) {
                // without a namespace the engine itself, as the secret refresh task does
                completePath = namespace + "/" + completePath;
            }
            if (ObjectHelper.isNotEmpty(version)) {
                completePath = completePath + "?version=" + version;
            }
            VaultResponse rawSecret = client.read(completePath);
            // the secret may not exist, or the response may have no data: then there is no value (nor field)
            Map<String, Object> responseData = rawSecret != null ? rawSecret.getData() : null;
            Object secretData = responseData != null ? responseData.get("data") : null;
            if (secretData != null) {
                returnValue = secretData.toString();
                if (ObjectHelper.isEmpty(version)) {
                    captureVersion(secretName, responseData);
                }
            }
            if (ObjectHelper.isNotEmpty(subkey)) {
                returnValue = null;
                if (secretData instanceof Map<?, ?> data) {
                    Object field = data.get(subkey);
                    if (ObjectHelper.isNotEmpty(field)) {
                        returnValue = field.toString();
                    }
                }
            }
            if (ObjectHelper.isEmpty(returnValue)) {
                returnValue = defaultValue;
            }
        } catch (Exception ex) {
            if (ObjectHelper.isNotEmpty(defaultValue)) {
                returnValue = defaultValue;
            } else {
                throw ex;
            }
        }
        return returnValue;
    }

    /**
     * The name of a secret as the refresh task tracks it: engine:secret, or only the secret for the default secret
     * engine. A lookup without engine is tracked as a secret of the default secret engine, which is also how the
     * refresh task reads a name without engine.
     */
    private static String trackedSecretName(String secretEngine, String key) {
        return secretEngine == null || "secret".equals(secretEngine) ? key : secretEngine + ":" + key;
    }

    private void captureVersion(String secretName, Map<String, Object> responseData) {
        if (responseData.get("metadata") instanceof Map<?, ?> metadata && metadata.get("version") != null) {
            try {
                // keep the oldest version resolved: a value resolved earlier may still be in use, so the refresh
                // task must compare with that one (at worst one extra reload)
                secretVersions.merge(secretName, Integer.valueOf(metadata.get("version").toString()), Math::min);
            } catch (NumberFormatException e) {
                LOG.debug("Could not parse secret version for {}: {}", secretName, metadata.get("version"), e);
                // the refresh task then uses the version it reads first
            }
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Ids of the secrets in use, as engine:secret, or only the secret for the default secret engine
     */
    public Set<String> getSecrets() {
        return Collections.unmodifiableSet(secrets);
    }

    /**
     * The oldest version of the secret (named as in {@link #getSecrets()}) that was resolved, or null if not known
     * (such as when a specific version was requested).
     *
     * @param  secretName the name of the secret, as engine:secret, or only the secret for the default secret engine
     * @return            the oldest version of the secret that was resolved, or null if not known
     */
    public Integer getSecretVersion(String secretName) {
        return secretVersions.get(secretName);
    }
}
