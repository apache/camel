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

package org.apache.camel.component.cyberark.vault;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.component.cyberark.vault.client.ConjurClientFactory;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.CyberArkVaultConfiguration;

/**
 * A {@link PropertiesFunction} that lookup the property value from CyberArk Conjur Vault service.
 * <p/>
 * The credentials to access CyberArk Conjur can be defined using environment variables:
 * <ul>
 * <li><tt>CAMEL_VAULT_CYBERARK_URL</tt></li>
 * <li><tt>CAMEL_VAULT_CYBERARK_ACCOUNT</tt></li>
 * <li><tt>CAMEL_VAULT_CYBERARK_USERNAME</tt></li>
 * <li><tt>CAMEL_VAULT_CYBERARK_PASSWORD</tt></li>
 * <li><tt>CAMEL_VAULT_CYBERARK_API_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_CYBERARK_AUTH_TOKEN</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise, it is possible to specify the credentials as properties:
 *
 * <ul>
 * <li><tt>camel.vault.cyberark.url</tt></li>
 * <li><tt>camel.vault.cyberark.account</tt></li>
 * <li><tt>camel.vault.cyberark.username</tt></li>
 * <li><tt>camel.vault.cyberark.password</tt></li>
 * <li><tt>camel.vault.cyberark.apiKey</tt></li>
 * <li><tt>camel.vault.cyberark.authToken</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>cyberark:</tt>. For example asking for <tt>cyberark:db/password</tt>,
 * will return the secret value associated with the secret named db/password on CyberArk Conjur.
 *
 * Another way of retrieving a secret value is using the following notation <tt>cyberark:database#username</tt>: in this
 * case the field username of the secret database will be returned. As a fallback, the user could provide a default
 * value, which will be returned in case the secret doesn't exist or if a particular field of the secret doesn't exist.
 * For using this feature, the user could use the following notation <tt>cyberark:database#username:admin</tt>. The
 * admin value will be returned as default value, if the conditions above were all met.
 */
@org.apache.camel.spi.annotations.PropertiesFunction("cyberark")
public class CyberArkVaultPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_CYBERARK_VAULT_URL = "CAMEL_VAULT_CYBERARK_URL";
    private static final String CAMEL_CYBERARK_VAULT_ACCOUNT = "CAMEL_VAULT_CYBERARK_ACCOUNT";
    private static final String CAMEL_CYBERARK_VAULT_USERNAME = "CAMEL_VAULT_CYBERARK_USERNAME";
    private static final String CAMEL_CYBERARK_VAULT_PASSWORD = "CAMEL_VAULT_CYBERARK_PASSWORD";
    private static final String CAMEL_CYBERARK_VAULT_API_KEY = "CAMEL_VAULT_CYBERARK_API_KEY";
    private static final String CAMEL_CYBERARK_VAULT_AUTH_TOKEN = "CAMEL_VAULT_CYBERARK_AUTH_TOKEN";

    private CamelContext camelContext;
    private ConjurClient client;

    private final Set<String> secrets = new HashSet<>();

    private String url;
    private String account;

    public CyberArkVaultPropertiesFunction() {
        super();
    }

    public CyberArkVaultPropertiesFunction(ConjurClient client) {
        super();
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String url = System.getenv(CAMEL_CYBERARK_VAULT_URL);
        String account = System.getenv(CAMEL_CYBERARK_VAULT_ACCOUNT);
        String username = System.getenv(CAMEL_CYBERARK_VAULT_USERNAME);
        String password = System.getenv(CAMEL_CYBERARK_VAULT_PASSWORD);
        String apiKey = System.getenv(CAMEL_CYBERARK_VAULT_API_KEY);
        String authToken = System.getenv(CAMEL_CYBERARK_VAULT_AUTH_TOKEN);

        if (ObjectHelper.isEmpty(url) && ObjectHelper.isEmpty(account)) {
            CyberArkVaultConfiguration cyberArkVaultConfiguration =
                    getCamelContext().getVaultConfiguration().cyberark();
            if (ObjectHelper.isNotEmpty(cyberArkVaultConfiguration)) {
                url = cyberArkVaultConfiguration.getUrl();
                account = cyberArkVaultConfiguration.getAccount();
                username = cyberArkVaultConfiguration.getUsername();
                password = cyberArkVaultConfiguration.getPassword();
                apiKey = cyberArkVaultConfiguration.getApiKey();
                authToken = cyberArkVaultConfiguration.getAuthToken();
            }
        }

        this.url = url;
        this.account = account;

        if (ObjectHelper.isNotEmpty(url) && ObjectHelper.isNotEmpty(account)) {
            // Create Conjur client based on authentication method
            if (ObjectHelper.isNotEmpty(authToken)) {
                // Use pre-authenticated token
                client = ConjurClientFactory.createWithToken(url, account, authToken);
            } else if (ObjectHelper.isNotEmpty(apiKey)) {
                // Use API key authentication
                client = ConjurClientFactory.createWithApiKey(url, account, username, apiKey);
            } else if (ObjectHelper.isNotEmpty(username) && ObjectHelper.isNotEmpty(password)) {
                // Use username/password authentication
                client = ConjurClientFactory.createWithCredentials(url, account, username, password);
            } else {
                throw new RuntimeCamelException(
                        "Using the CyberArk Conjur Vault Properties Function requires authentication credentials (authToken, apiKey, or username/password)");
            }
        } else {
            throw new RuntimeCamelException(
                    "Using the CyberArk Conjur Vault Properties Function requires setting URL and account as application properties or environment variables");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // ignore
            }
            client = null;
        }
        secrets.clear();
        super.doStop();
    }

    @Override
    public String getName() {
        return "cyberark";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String subkey = null;
        String returnValue = null;
        String defaultValue = null;
        String version = null;

        if (remainder.contains("#")) {
            key = StringHelper.before(remainder, "#");
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
                throw new RuntimeCamelException(
                        "Error getting secret from CyberArk Conjur vault using key: " + key + " due to: "
                                + e.getMessage(),
                        e);
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(String key, String subkey, String defaultValue, String version)
            throws JsonProcessingException {

        // capture name of secret
        secrets.add(key);

        String returnValue;
        try {
            // Retrieve secret from Conjur
            returnValue = client.retrieveSecret(key, version);

            if (ObjectHelper.isNotEmpty(subkey)) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode actualObj = mapper.readTree(returnValue);
                JsonNode field = actualObj.get(subkey);
                if (ObjectHelper.isNotEmpty(field)) {
                    returnValue = field.textValue();
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

    /**
     * Ids of the secrets in use
     */
    public Set<String> getSecrets() {
        return secrets;
    }

    /**
     * The Conjur URL in use
     */
    public String getUrl() {
        return url;
    }

    /**
     * The Conjur account in use
     */
    public String getAccount() {
        return account;
    }
}
