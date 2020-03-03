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

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public final class LoginConfigHelper {

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

        System.getenv().keySet().stream()//
            .filter(k -> k.startsWith("SALESFORCE_") && isNotEmpty(System.getenv(k))).forEach(k -> configuration.put(fromEnvName(k), System.getenv(k)));
        System.getProperties().keySet().stream().map(String.class::cast).filter(k -> k.startsWith("salesforce.") && isNotEmpty(System.getProperty(k)))
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
            System.out.println("To run integration tests Salesforce Authentication information is");
            System.out.println("needed.");
            System.out.println("You need to specify the configuration for running tests by either");
            System.out.println("specifying environment variables, Maven properties or create a Java");
            System.out.println("properties file at:");
            System.out.println();
            System.out.println("camel/components/camel-salesforce/test-salesforce-login.properties");
            System.out.println();
            System.out.println("With authentication information to access a Salesforce instance.");
            System.out.println("You can use:");
            System.out.println();
            System.out.println("camel/components/camel-salesforce/test-salesforce-login.properties.sample");
            System.out.println();
            System.out.println("as reference. A free Salesforce developer account can be obtained at:");
            System.out.println();
            System.out.println("https://developer.salesforce.com");
            System.out.println();
            System.out.println("Properties that you need to set:");
            System.out.println();
            System.out.println("| Maven or properties file     | Environment variable         | Use    |");
            System.out.println("|------------------------------+------------------------------+--------|");
            System.out.println("| salesforce.client.id         | SALESFORCE_CLIENT_ID         | ALL    |");
            System.out.println("| salesforce.client.secret     | SALESFORCE_CLIENT_SECRET     | UP, RT |");
            System.out.println("| salesforce.username          | SALESFORCE_USERNAME          | UP, JWT|");
            System.out.println("| salesforce.password          | SALESFORCE_PASSWORD          | UP     |");
            System.out.println("| salesforce.refreshToken      | SALESFORCE_REFRESH_TOKEN     | RT     |");
            System.out.println("| salesforce.keystore.path     | SALESFORCE_KEYSTORE_PATH     | JWT    |");
            System.out.println("| salesforce.keystore.type     | SALESFORCE_KEYSTORE_TYPE     | JWT    |");
            System.out.println("| salesforce.keystore.password | SALESFORCE_KEYSTORE_PASSWORD | JWT    |");
            System.out.println("| salesforce.login.url         | SALESFORCE_LOGIN_URL         | ALL    |");
            System.out.println();
            System.out.println("* ALL - required always");
            System.out.println("* UP  - when using username and password authentication");
            System.out.println("* RT  - when using refresh token flow");
            System.out.println("* JWT - when using JWT flow");
            System.out.println();
            System.out.println("You can force authentication type to be one of USERNAME_PASSWORD,");
            System.out.println("REFRESH_TOKEN or JWT by setting `salesforce.auth.type` (or ");
            System.out.println("`SALESFORCE_AUTH_TYPE` for environment variables).");
            System.out.println();
            System.out.println("Examples:");
            System.out.println();
            System.out.println("Using environment:");
            System.out.println();
            System.out.println("$ export SALESFORCE_CLIENT_ID=...");
            System.out.println("$ export SALESFORCE_CLIENT_SECRET=...");
            System.out.println("$ export ...others...");
            System.out.println();
            System.out.println("or using Maven properties:");
            System.out.println();
            System.out.println("$ mvn -Pintegration -Dsalesforce.client.id=... \\");
            System.out.println("  -Dsalesforce.client.secret=... ...");
            System.out.println();
        }
    }

    public static SalesforceLoginConfig getLoginConfig() {
        return INSTANCE.createLoginConfig();
    }

}
