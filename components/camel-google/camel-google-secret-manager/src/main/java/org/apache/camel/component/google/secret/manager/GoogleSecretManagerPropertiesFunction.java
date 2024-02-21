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
package org.apache.camel.component.google.secret.manager;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.GcpVaultConfiguration;

/**
 * A {@link PropertiesFunction} that lookup the property value from GCP Secrets Manager service.
 * <p/>
 * The credentials to access Secrets Manager is defined using three environment variables representing the static
 * credentials:
 * <ul>
 * <li><tt>CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_GCP_PROJECT_ID</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise it is possible to specify the credentials as properties:
 *
 * <ul>
 * <li><tt>camel.vault.aws.serviceAccountKey</tt></li>
 * <li><tt>camel.vault.aws.projectId</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>gcp:</tt>. For example asking for <tt>gcp:token</tt>, will return the
 * secret value associated to the secret named token on GCP Secrets Manager.
 *
 * Another way of retrieving a secret value is using the following notation <tt>gcp:database/username</tt>: in this case
 * the field username of the secret database will be returned. As a fallback, the user could provide a default value,
 * which will be returned in case the secret doesn't exist, the secret has been marked for deletion or, for example, if
 * a particular field of the secret doesn't exist. For using this feature, the user could use the following notation
 * <tt>gcp:database/username:admin</tt>. The admin value will be returned as default value, if the conditions above were
 * all met.
 */

@org.apache.camel.spi.annotations.PropertiesFunction("gcp")
public class GoogleSecretManagerPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY = "CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY";
    private static final String CAMEL_VAULT_GCP_PROJECT_ID = "CAMEL_VAULT_GCP_PROJECT_ID";
    private static final String CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE = "CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE";

    boolean useDefaultInstance;

    private CamelContext camelContext;
    private SecretManagerServiceClient client;
    private String projectId;
    private final Set<String> secrets = new HashSet<>();

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String serviceAccountKey = System.getenv(CAMEL_VAULT_GCP_SERVICE_ACCOUNT_KEY);
        useDefaultInstance = Boolean.parseBoolean(System.getenv(CAMEL_VAULT_GCP_USE_DEFAULT_INSTANCE));
        projectId = System.getenv(CAMEL_VAULT_GCP_PROJECT_ID);
        if (ObjectHelper.isEmpty(serviceAccountKey) && ObjectHelper.isEmpty(projectId)) {
            GcpVaultConfiguration gcpVaultConfiguration = getCamelContext().getVaultConfiguration().gcp();
            if (ObjectHelper.isNotEmpty(gcpVaultConfiguration)) {
                serviceAccountKey = gcpVaultConfiguration.getServiceAccountKey();
                projectId = gcpVaultConfiguration.getProjectId();
                useDefaultInstance = gcpVaultConfiguration.isUseDefaultInstance();
            }
        }
        if (ObjectHelper.isNotEmpty(serviceAccountKey) && ObjectHelper.isNotEmpty(projectId)) {
            InputStream resolveMandatoryResourceAsInputStream
                    = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), serviceAccountKey);
            Credentials myCredentials = ServiceAccountCredentials
                    .fromStream(resolveMandatoryResourceAsInputStream);
            SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(myCredentials)).build();
            client = SecretManagerServiceClient.create(settings);
        } else if (useDefaultInstance && ObjectHelper.isNotEmpty(projectId)) {
            SecretManagerServiceSettings settings = SecretManagerServiceSettings.newBuilder().build();
            client = SecretManagerServiceClient.create(settings);
        } else {
            throw new RuntimeCamelException(
                    "Using the GCP Secret Manager Properties Function requires setting GCP service account key and project Id as application properties or environment variables");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
        }
        secrets.clear();
        super.doStop();
    }

    @Override
    public String getName() {
        return "gcp";
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
            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Something went wrong while recovering " + key + " from vault");
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(
            String key, String subkey, String defaultValue, String version)
            throws JsonProcessingException {

        // capture name of secret
        secrets.add(key);

        String returnValue = null;
        try {
            SecretVersionName secretVersionName
                    = SecretVersionName.of(projectId, key, ObjectHelper.isNotEmpty(version) ? version : "latest");
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
            if (ObjectHelper.isNotEmpty(response)) {
                returnValue = response.getPayload().getData().toStringUtf8();
            }
            if (ObjectHelper.isNotEmpty(subkey) && ObjectHelper.isNotEmpty(returnValue)) {
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
        } catch (ApiException ex) {
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
     * Whether login is using default instance or service account key file
     */
    public boolean isUseDefaultInstance() {
        return useDefaultInstance;
    }
}
