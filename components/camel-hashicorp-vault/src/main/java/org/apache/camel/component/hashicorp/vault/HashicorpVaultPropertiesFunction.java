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
 * <li><tt>CAMEL_HASHICORP_VAULT_ENGINE_ENV</tt></li>
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
 * <li><tt>camel.vault.hashicorp.engine</tt></li>
 * <li><tt>camel.vault.hashicorp.host</tt></li>
 * <li><tt>camel.vault.hashicorp.port</tt></li>
 * <li><tt>camel.vault.hashicorp.scheme</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>hashicorp:</tt>. For example asking for <tt>hashicorp:token</tt>, will
 * return the secret value associated to the secret named token on Hashicorp Vault instance.
 *
 * Another way of retrieving a secret value is using the following notation <tt>hashicorp:database/username</tt>: in
 * this case the field username of the secret database will be returned. As a fallback, the user could provide a default
 * value, which will be returned in case the secret doesn't exist, the secret has been marked for deletion or, for
 * example, if a particular field of the secret doesn't exist. For using this feature, the user could use the following
 * notation <tt>aws:database/username:admin</tt>. The admin value will be returned as default value, if the conditions
 * above were all met.
 */

@org.apache.camel.spi.annotations.PropertiesFunction("hashicorp")
public class HashicorpVaultPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_HASHICORP_VAULT_TOKEN_ENV = "CAMEL_HASHICORP_VAULT_TOKEN";
    private static final String CAMEL_HASHICORP_VAULT_ENGINE_ENV = "CAMEL_HASHICORP_VAULT_ENGINE";
    private static final String CAMEL_HASHICORP_VAULT_HOST_ENV = "CAMEL_HASHICORP_VAULT_HOST";
    private static final String CAMEL_HASHICORP_VAULT_PORT_ENV
            = "CAMEL_HASHICORP_VAULT_PORT";
    private static final String CAMEL_HASHICORP_VAULT_SCHEME_ENV
            = "CAMEL_HASHICORP_VAULT_SCHEME";
    private CamelContext camelContext;
    private VaultTemplate client;

    private String engine;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String token = System.getenv(CAMEL_HASHICORP_VAULT_TOKEN_ENV);
        engine = System.getenv(CAMEL_HASHICORP_VAULT_ENGINE_ENV);
        String host = System.getenv(CAMEL_HASHICORP_VAULT_HOST_ENV);
        String port = System.getenv(CAMEL_HASHICORP_VAULT_PORT_ENV);
        String scheme = System.getenv(CAMEL_HASHICORP_VAULT_SCHEME_ENV);
        if (ObjectHelper.isEmpty(token) && ObjectHelper.isEmpty(engine) && ObjectHelper.isEmpty(host)
                && ObjectHelper.isEmpty(port) && ObjectHelper.isEmpty(scheme)) {
            HashicorpVaultConfiguration hashicorpVaultConfiguration = getCamelContext().getVaultConfiguration().hashicorp();
            if (ObjectHelper.isNotEmpty(hashicorpVaultConfiguration)) {
                token = hashicorpVaultConfiguration.getToken();
                engine = hashicorpVaultConfiguration.getEngine();
                host = hashicorpVaultConfiguration.getHost();
                port = hashicorpVaultConfiguration.getPort();
                scheme = hashicorpVaultConfiguration.getScheme();
            }
        }
        if (ObjectHelper.isNotEmpty(token) && ObjectHelper.isNotEmpty(engine) && ObjectHelper.isNotEmpty(host)
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
                    "Using the Hashicorp Properties Function requires setting Engine, Token, Host, port and scheme properties");
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
        if (remainder.contains("/")) {
            key = StringHelper.before(remainder, "/");
            subkey = StringHelper.after(remainder, "/");
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
            key = StringHelper.before(remainder, ":");
            defaultValue = StringHelper.after(remainder, ":");
            if (remainder.contains("@")) {
                version = StringHelper.after(remainder, "@");
                defaultValue = StringHelper.before(defaultValue, "@");
            }
        } else {
            if (remainder.contains("@")) {
                key = StringHelper.before(remainder, "@");
                version = StringHelper.after(remainder, "@");
            }
        }

        if (key != null) {
            try {
                returnValue = getSecretFromSource(key, subkey, defaultValue, version);
            } catch (Exception e) {
                throw new RuntimeCamelException("Something went wrong while recovering " + key + " from vault");
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(String key, String subkey, String defaultValue, String version) {
        String returnValue = null;
        try {
            String completePath = engine + "/" + "data" + "/" + key;
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
