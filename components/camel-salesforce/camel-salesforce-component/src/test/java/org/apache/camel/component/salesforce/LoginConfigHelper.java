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

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoginConfigHelper {

    private static final Logger LOG = LoggerFactory.getLogger(LoginConfigHelper.class);
    private static final LoginConfigHelper INSTANCE = new LoginConfigHelper();

    private final Map<String, String> configuration;

    private LoginConfigHelper() {
        configuration = new HashMap<>();
        try {
            final ResourceBundle properties = ResourceBundle.getBundle("test-salesforce-login");
            properties.keySet().forEach(k -> configuration.put(k, properties.getString(k)));
        } catch (final MissingResourceException ignored) {
            // ignoring if missing
        }

        System.getenv().keySet().stream() //
                .filter(k -> k.startsWith("SALESFORCE_") && isNotEmpty(System.getenv(k)))
                .forEach(k -> configuration.put(fromEnvName(k), System.getenv(k)));
        System.getProperties().keySet().stream()
                .map(String.class::cast)
                .filter(k -> k.startsWith("salesforce.") && isNotEmpty(System.getProperty(k)))
                .forEach(k -> configuration.put(k, System.getProperty(k)));
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
            LOG.info("You need to specify the configuration for running tests by either");
            LOG.info("specifying environment variables, Maven properties or create a Java");
            LOG.info("properties file at:");
            LOG.info("");
            LOG.info("camel/components/camel-salesforce/test-salesforce-login.properties");
            LOG.info("");
            LOG.info("With authentication information to access a Salesforce instance.");
            LOG.info("You can use:");
            LOG.info("");
            LOG.info("camel/components/camel-salesforce/test-salesforce-login.sample.properties");
            LOG.info("");
            LOG.info("as reference. A free Salesforce developer account can be obtained at:");
            LOG.info("");
            LOG.info("https://developer.salesforce.com");
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
            LOG.info("You can force authentication type to be one of USERNAME_PASSWORD,");
            LOG.info("REFRESH_TOKEN or JWT by setting `salesforce.auth.type` (or ");
            LOG.info("`SALESFORCE_AUTH_TYPE` for environment variables).");
            LOG.info("");
            LOG.info("Examples:");
            LOG.info("");
            LOG.info("Using environment:");
            LOG.info("");
            LOG.info("$ export SALESFORCE_CLIENT_ID=...");
            LOG.info("$ export SALESFORCE_CLIENT_SECRET=...");
            LOG.info("$ export ...others...");
            LOG.info("");
            LOG.info("or using Maven properties:");
            LOG.info("");
            LOG.info("$ mvn -Pintegration -Dsalesforce.client.id=... \\");
            LOG.info("  -Dsalesforce.client.secret=... ...");
            LOG.info("");
        }
    }

    public static SalesforceLoginConfig getLoginConfig() {
        return INSTANCE.createLoginConfig();
    }
}
