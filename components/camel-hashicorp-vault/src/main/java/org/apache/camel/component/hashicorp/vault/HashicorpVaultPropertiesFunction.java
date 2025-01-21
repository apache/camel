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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.HashicorpVaultConfiguration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

/**
 * A {@link PropertiesFunction} that lookup the property value from Hashicorp Vault instance.
 * <p/>
 * The credentials to access Hashicorp Vault is defined using five environment variables representing the static
 * credentials and service location:
 * <ul>
 * <li><tt>CAMEL_HASHICORP_VAULT_TOKEN_ENV</tt></li>
 * <li><tt>CAMEL_HASHICORP_VAULT_HOST_ENV</tt></li>
 * <li><tt>CAMEL_HASHICORP_VAULT_PORT_ENV</tt></li>
 * <li><tt>CAMEL_HASHICORP_VAULT_SCHEME_ENV</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise it is possible to specify the credentials and service location as properties:
 *
 * <ul>
 * <li><tt>camel.vault.hashicorp.token</tt></li>
 * <li><tt>camel.vault.hashicorp.host</tt></li>
 * <li><tt>camel.vault.hashicorp.port</tt></li>
 * <li><tt>camel.vault.hashicorp.scheme</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>hashicorp:</tt>. For example asking for
 * <tt>hashicorp:engine:token</tt>, will return the secret value associated to the secret named token on Hashicorp Vault
 * instance on the engine 'engine'.
 *
 * Another way of retrieving a secret value is using the following notation <tt>hashicorp:engine:database/username</tt>:
 * in this case the field username of the secret database will be returned. As a fallback, the user could provide a
 * default value, which will be returned in case the secret doesn't exist, the secret has been marked for deletion or,
 * for example, if a particular field of the secret doesn't exist. For using this feature, the user could use the
 * following notation <tt>hashicorp:engine:database/username:admin</tt>. The admin value will be returned as default
 * value, if the conditions above were all met.
 */

@org.apache.camel.spi.annotations.PropertiesFunction("hashicorp")
public class HashicorpVaultPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_HASHICORP_VAULT_TOKEN_ENV = "CAMEL_HASHICORP_VAULT_TOKEN";
    private static final String CAMEL_HASHICORP_VAULT_HOST_ENV = "CAMEL_HASHICORP_VAULT_HOST";
    private static final String CAMEL_HASHICORP_VAULT_PORT_ENV
            = "CAMEL_HASHICORP_VAULT_PORT";
    private static final String CAMEL_HASHICORP_VAULT_SCHEME_ENV
            = "CAMEL_HASHICORP_VAULT_SCHEME";
    private static final String CAMEL_HASHICORP_VAULT_CLOUD_ENV
            = "CAMEL_HASHICORP_VAULT_CLOUD";
    private static final String CAMEL_HASHICORP_VAULT_NAMESPACE_ENV
            = "CAMEL_HASHICORP_VAULT_NAMESPACE";
    private CamelContext camelContext;
    private VaultTemplate client;

    private String engine;
    private String namespace;
    private boolean cloud;

    public HashicorpVaultPropertiesFunction() {
        super();
    }

    public HashicorpVaultPropertiesFunction(VaultTemplate client) {
        super();
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String token = System.getenv(CAMEL_HASHICORP_VAULT_TOKEN_ENV);
        String host = System.getenv(CAMEL_HASHICORP_VAULT_HOST_ENV);
        String port = System.getenv(CAMEL_HASHICORP_VAULT_PORT_ENV);
        String scheme = System.getenv(CAMEL_HASHICORP_VAULT_SCHEME_ENV);
        namespace = System.getenv(CAMEL_HASHICORP_VAULT_NAMESPACE_ENV);
        if (ObjectHelper.isEmpty(token) && ObjectHelper.isEmpty(host)
                && ObjectHelper.isEmpty(port) && ObjectHelper.isEmpty(scheme) && ObjectHelper.isEmpty(namespace)) {
            HashicorpVaultConfiguration hashicorpVaultConfiguration = getCamelContext().getVaultConfiguration().hashicorp();
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
        if (ObjectHelper.isNotEmpty(token) && ObjectHelper.isNotEmpty(host)
                && ObjectHelper.isNotEmpty(port) && ObjectHelper.isNotEmpty(scheme)) {
            VaultEndpoint vaultEndpoint = new VaultEndpoint();
            vaultEndpoint.setHost(host);
            vaultEndpoint.setPort(Integer.parseInt(port));
            vaultEndpoint.setScheme(scheme);

            client = new VaultTemplate(
                    vaultEndpoint,
                    new TokenAuthentication(token));
        } else {
            throw new RuntimeCamelException(
                    "Using the Hashicorp Properties Function requires setting Token, Host, port and scheme properties");
        }
    }

    @Override
    public String getName() {
        return "hashicorp";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String subkey = null;
        String returnValue = null;
        String defaultValue = null;
        String version = null;
        if (remainder.contains("#")) {
            String keyRemainder = StringHelper.before(remainder, "#");
            engine = StringHelper.before(keyRemainder, ":");
            key = StringHelper.after(keyRemainder, ":");
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
        }

        if (key != null) {
            try {
                returnValue = getSecretFromSource(key, subkey, defaultValue, version);
            } catch (Exception e) {
                throw new RuntimeCamelException(
                        "Error getting secret from vault using key: " + key + " due to: " + e.getMessage(), e);
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(String key, String subkey, String defaultValue, String version) {
        String returnValue = null;
        try {
            String completePath = "";
            if (!cloud) {
                completePath = engine + "/" + "data" + "/" + key;
            } else {
                if (ObjectHelper.isNotEmpty(namespace)) {
                    completePath = namespace + "/" + engine + "/" + "data" + "/" + key;
                }
            }
            if (ObjectHelper.isNotEmpty(version)) {
                completePath = completePath + "?version=" + version;
            }
            VaultResponse rawSecret = client.read(completePath);
            if (ObjectHelper.isNotEmpty(rawSecret)) {
                returnValue = rawSecret.getData().get("data").toString();
            }
            if (ObjectHelper.isNotEmpty(subkey)) {
                Object field = ((Map) rawSecret.getData().get("data")).get(subkey);
                if (ObjectHelper.isNotEmpty(field)) {
                    returnValue = field.toString();
                } else {
                    returnValue = null;
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

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
}
