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
package org.apache.camel.component.ibm.secrets.manager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ibm.cloud.sdk.core.http.Response;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.SecretsManager;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.GetSecretByNameTypeOptions;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.GetSecretVersionOptions;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.Secret;
import com.ibm.cloud.secrets_manager_sdk.secrets_manager.v2.model.SecretVersion;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.IBMSecretsManagerVaultConfiguration;

/**
 * A {@link PropertiesFunction} that lookup the property value from IBM Secrets Manager service.
 * <p/>
 * The credentials to access Secrets Manager is defined using three environment variables representing the static
 * credentials:
 * <ul>
 * <li><tt>CAMEL_VAULT_IBM_TOKEN</tt></li>
 * <li><tt>CAMEL_VAULT_IBM_SERVICE_URL</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise, it is possible to specify the credentials as properties:
 *
 * <ul>
 * <li><tt>camel.vault.ibm.token</tt></li>
 * <li><tt>camel.vault.ibm.serviceUrl</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>ibm:</tt>. For example asking for <tt>ibm:token</tt>, will return the
 * secret value associated to the secret named token on AWS Secrets Manager.
 *
 * Another way of retrieving a secret value is using the following notation <tt>ibm:database/username</tt>: in this case
 * the field username of the secret database will be returned. As a fallback, the user could provide a default value,
 * which will be returned in case the secret doesn't exist, the secret has been marked for deletion or, for example, if
 * a particular field of the secret doesn't exist. For using this feature, the user could use the following notation
 * <tt>ibm:database/username:admin</tt>. The admin value will be returned as default value, if the conditions above were
 * all met.
 */
@org.apache.camel.spi.annotations.PropertiesFunction("ibm")
public class IBMSecretsManagerPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_AWS_VAULT_IBM_TOKEN_ENV = "CAMEL_VAULT_IBM_TOKEN";
    private static final String CAMEL_AWS_VAULT_IBM_SERVICE_URL_ENV = "CAMEL_VAULT_IBM_SERVICE_URL";

    private CamelContext camelContext;
    private SecretsManager client;

    private final Set<String> secrets = new HashSet<>();

    public IBMSecretsManagerPropertiesFunction() {
        super();
    }

    public IBMSecretsManagerPropertiesFunction(SecretsManager client) {
        super();
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String token = System.getenv(CAMEL_AWS_VAULT_IBM_TOKEN_ENV);
        String serviceUrl = System.getenv(CAMEL_AWS_VAULT_IBM_SERVICE_URL_ENV);
        if (ObjectHelper.isEmpty(token) && ObjectHelper.isEmpty(serviceUrl)) {
            IBMSecretsManagerVaultConfiguration ibmVaultConfiguration
                    = getCamelContext().getVaultConfiguration().ibmSecretsManager();
            if (ObjectHelper.isNotEmpty(ibmVaultConfiguration)) {
                token = ibmVaultConfiguration.getToken();
                serviceUrl = ibmVaultConfiguration.getServiceUrl();
            }
            IamAuthenticator iamAuthenticator = new IamAuthenticator.Builder()
                    .apikey(token)
                    .build();
            client = new SecretsManager("Camel Secrets Manager Service for Properties", iamAuthenticator);
            client.setServiceUrl(serviceUrl);
        } else {
            throw new RuntimeCamelException(
                    "Using the IBM Secrets Manager Properties Function requires setting IBM Credentials and service url as application properties or environment variables");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client = null;
        }
        secrets.clear();
        super.doStop();
    }

    @Override
    public String getName() {
        return "aws";
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
                throw new RuntimeCamelException("Something went wrong while recovering " + key + " from vault");
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(
            String key, String subkey, String defaultValue, String version) {

        // capture name of secret
        secrets.add(key);

        String returnValue = "";
        Map<String, Object> data = Map.of();
        GetSecretByNameTypeOptions.Builder secretRequestBuilder = new GetSecretByNameTypeOptions.Builder();
        secretRequestBuilder.secretType(Secret.SecretType.KV).name(key);
        secretRequestBuilder.secretGroupName("default");
        Response<Secret> response = client.getSecretByNameType(secretRequestBuilder.build()).execute();
        data = response.getResult().getData();
        if (ObjectHelper.isNotEmpty(version)) {
            GetSecretVersionOptions getSecretVersionOptions = new GetSecretVersionOptions.Builder()
                    .secretId(response.getResult().getId())
                    .id(version)
                    .build();

            Response<SecretVersion> secVersion = client.getSecretVersion(getSecretVersionOptions).execute();
            data = secVersion.getResult().getData();
        }
        if (ObjectHelper.isNotEmpty(data)) {
            data = response.getResult().getData();
        }
        if (ObjectHelper.isNotEmpty(subkey)) {
            returnValue = String.valueOf(data.get(subkey));
        } else {
            returnValue = null;
        }
        if (ObjectHelper.isEmpty(returnValue)) {
            returnValue = defaultValue;
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
}
