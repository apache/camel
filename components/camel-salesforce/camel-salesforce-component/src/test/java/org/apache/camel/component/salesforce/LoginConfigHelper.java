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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public final class LoginConfigHelper {

    private static final Logger LOG = LoggerFactory.getLogger(LoginConfigHelper.class);
    private static final LoginConfigHelper INSTANCE = new LoginConfigHelper();

    private static final String SF_CREDENTIALS_PATH = "it/resources/sfdx-project/.local/test-credentials.json";

    private final Map<String, String> configuration;

    private LoginConfigHelper() {
        configuration = new HashMap<>();
        loadConfiguration();
    }

    public static void reloadCredentials() {
        INSTANCE.configuration.clear();
        INSTANCE.loadConfiguration();
    }

    private void loadConfiguration() {
        // 1. First, try to load from SF CLI credentials file (lowest priority, can be overridden)
        loadFromSfCliCredentials();

        // 2. Load from properties file (overrides SF CLI)
        try {
            final ResourceBundle properties = ResourceBundle.getBundle("test-salesforce-login");
            properties.keySet().forEach(k -> configuration.put(k, properties.getString(k)));
        } catch (final MissingResourceException ignored) {
            // ignoring if missing
        }

        // 3. Environment variables (overrides properties file)
        System.getenv().keySet().stream()//
                .filter(k -> k.startsWith("SALESFORCE_") && isNotEmpty(System.getenv(k)))
                .forEach(k -> configuration.put(fromEnvName(k), System.getenv(k)));

        // 4. System properties (highest priority)
        System.getProperties().keySet().stream().map(String.class::cast)
                .filter(k -> k.startsWith("salesforce.") && isNotEmpty(System.getProperty(k)))
                .forEach(k -> configuration.put(k, System.getProperty(k)));
    }

    private void loadFromSfCliCredentials() {
        Path credentialsPath = findCredentialsFile();

        if (credentialsPath == null || !Files.exists(credentialsPath)) {
            LOG.debug("SF CLI credentials file not found");
            return;
        }

        try {
            LOG.info("Loading credentials from SF CLI: {}", credentialsPath);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(credentialsPath.toFile());

            setIfPresent(root, "clientId", "salesforce.client.id");
            setIfPresent(root, "clientSecret", "salesforce.client.secret");
            setIfPresent(root, "refreshToken", "salesforce.refresh.token");
            setIfPresent(root, "username", "salesforce.username");
            setIfPresent(root, "password", "salesforce.password");
            setIfPresent(root, "instanceUrl", "salesforce.login.url");

        } catch (IOException e) {
            LOG.debug("Failed to read SF CLI credentials file: {}", e.getMessage());
        }
    }

    private void setIfPresent(JsonNode root, String jsonKey, String configKey) {
        JsonNode node = root.get(jsonKey);
        if (node != null && !node.isNull() && isNotEmpty(node.asText())) {
            configuration.put(configKey, node.asText());
        }
    }

    private Path findCredentialsFile() {
        // First, try the direct path from current directory
        Path direct = Paths.get(SF_CREDENTIALS_PATH);
        if (Files.exists(direct)) {
            return direct;
        }

        // Try to find from camel-salesforce directory
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(SF_CREDENTIALS_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            // Also check if we're in a subdirectory of camel-salesforce
            if (current.endsWith("camel-salesforce") || current.endsWith("camel-salesforce-component")) {
                candidate = current.getParent() != null
                        ? current.getParent().resolve(SF_CREDENTIALS_PATH)
                        : current.resolve(SF_CREDENTIALS_PATH);
                if (Files.exists(candidate)) {
                    return candidate;
                }
                // If we're in camel-salesforce directly
                candidate = current.resolve(SF_CREDENTIALS_PATH);
                if (Files.exists(candidate)) {
                    return candidate;
                }
            }
            current = current.getParent();
        }

        return null;
    }

    private String fromEnvName(final String envVariable) {
        return envVariable.replace('_', '.').toLowerCase();
    }

    SalesforceLoginConfig createLoginConfig() {
        final SalesforceLoginConfig loginConfig = new SalesforceLoginConfig();

        final String explicitType = configuration.get("salesforce.auth.type");
        if (ObjectHelper.isNotEmpty(explicitType)) {
            loginConfig.setType(AuthenticationType.valueOf(explicitType));
        }
        final String loginUrl = configuration.get("salesforce.login.url");
        if (ObjectHelper.isNotEmpty(loginUrl)) {
            loginConfig.setLoginUrl(loginUrl);
        }
        loginConfig.setClientId(configuration.get("salesforce.client.id"));
        loginConfig.setClientSecret(configuration.get("salesforce.client.secret"));
        loginConfig.setRefreshToken(configuration.get("salesforce.refresh.token"));
        loginConfig.setUserName(configuration.get("salesforce.username"));
        loginConfig.setPassword(configuration.get("salesforce.password"));

        final KeyStoreParameters keystore = new KeyStoreParameters();
        keystore.setResource(configuration.get("salesforce.keystore.resource"));
        keystore.setPassword(configuration.get("salesforce.keystore.password"));
        keystore.setType(configuration.get("salesforce.keystore.type"));
        keystore.setProvider(configuration.get("salesforce.keystore.provider"));
        loginConfig.setKeystore(keystore);

        validate(loginConfig);

        return loginConfig;
    }

    void validate(final SalesforceLoginConfig loginConfig) {
        try {
            loginConfig.validate();
        } catch (final IllegalArgumentException e) {
            LOG.info("To run integration tests Salesforce Authentication information is");
            LOG.info("needed.");
            LOG.info("");
            LOG.info("RECOMMENDED: Use the SF CLI setup script to automatically configure");
            LOG.info("a scratch org with all required credentials:");
            LOG.info("");
            LOG.info("  cd camel/components/camel-salesforce/it/resources");
            LOG.info("  ./setup-salesforce.sh");
            LOG.info("");
            LOG.info("This will create a scratch org, deploy the test metadata, and store");
            LOG.info("credentials that are automatically loaded by the tests.");
            LOG.info("");
            LOG.info("ALTERNATIVE: You can specify configuration manually by either");
            LOG.info("specifying environment variables, Maven properties or create a Java");
            LOG.info("properties file at:");
            LOG.info("");
            LOG.info("camel/components/camel-salesforce/test-salesforce-login.properties");
            LOG.info("");
            LOG.info("Properties that you need to set:");
            LOG.info("");
            LOG.info("| Maven or properties file     | Environment variable         | Use    |");
            LOG.info("|------------------------------+------------------------------+--------|");
            LOG.info("| salesforce.client.id         | SALESFORCE_CLIENT_ID         | ALL    |");
            LOG.info("| salesforce.client.secret     | SALESFORCE_CLIENT_SECRET     | UP, RT |");
            LOG.info("| salesforce.username          | SALESFORCE_USERNAME          | UP, JWT|");
            LOG.info("| salesforce.password          | SALESFORCE_PASSWORD          | UP     |");
            LOG.info("| salesforce.refreshToken      | SALESFORCE_REFRESH_TOKEN     | RT     |");
            LOG.info("| salesforce.keystore.path     | SALESFORCE_KEYSTORE_PATH     | JWT    |");
            LOG.info("| salesforce.keystore.type     | SALESFORCE_KEYSTORE_TYPE     | JWT    |");
            LOG.info("| salesforce.keystore.password | SALESFORCE_KEYSTORE_PASSWORD | JWT    |");
            LOG.info("| salesforce.login.url         | SALESFORCE_LOGIN_URL         | ALL    |");
            LOG.info("");
            LOG.info("* ALL - required always");
            LOG.info("* UP  - when using username and password authentication");
            LOG.info("* RT  - when using refresh token flow");
            LOG.info("* JWT - when using JWT flow");
            LOG.info("");
        }
    }

    public static SalesforceLoginConfig getLoginConfig() {
        return INSTANCE.createLoginConfig();
    }

}
