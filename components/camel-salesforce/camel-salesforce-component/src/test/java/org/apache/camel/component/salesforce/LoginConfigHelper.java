/**
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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.junit.Assert;

public class LoginConfigHelper extends Assert {

    protected static final String TEST_LOGIN_PROPERTIES = "../test-salesforce-login.properties";

    private static final LoginConfigHelper INSTANCE;

    static {
        try {
            INSTANCE = new LoginConfigHelper();
        } catch (final IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private SalesforceLoginConfig config;

    private final Properties properties;

    public LoginConfigHelper() throws IOException {
        // load test-salesforce-login properties
        try (InputStream stream = new FileInputStream(TEST_LOGIN_PROPERTIES);) {
            properties = new Properties();
            properties.load(stream);
        } catch (final FileNotFoundException ignored) {
            throw new FileNotFoundException("Create a properties file named " + TEST_LOGIN_PROPERTIES
                + " with clientId, clientSecret, userName, and password or with clientId, clientSecret and refreshToken"
                + " for a Salesforce account with Merchandise and Invoice objects from Salesforce Guides.");
        }

        final boolean hasPassword = ObjectHelper.isNotEmpty(properties.getProperty("password"));
        final boolean hasRefreshToken = ObjectHelper.isNotEmpty(properties.getProperty("refreshToken"));
        final boolean hasKeystore = ObjectHelper.isNotEmpty(properties.getProperty("keystore.resource"));

        final boolean lazyLogin = Boolean.parseBoolean(properties.getProperty("lazyLogin", "false"));
        final String loginUrl = properties.getProperty("loginUrl", SalesforceLoginConfig.DEFAULT_LOGIN_URL);
        final String clientId = properties.getProperty("clientId");
        final String clientSecret = properties.getProperty("clientSecret");
        final String username = properties.getProperty("userName");

        if (hasPassword) {
            config = new SalesforceLoginConfig(loginUrl, clientId, clientSecret, username,
                properties.getProperty("password"), lazyLogin);
        } else if (hasRefreshToken) {
            config = new SalesforceLoginConfig(loginUrl, clientId, //
                clientSecret, //
                properties.getProperty("refreshToken"), //
                lazyLogin);
        } else if (hasKeystore) {
            final KeyStoreParameters keystore = new KeyStoreParameters();
            keystore.setResource(properties.getProperty("keystore.resource"));
            keystore.setType(properties.getProperty("keystore.type"));
            keystore.setPassword(properties.getProperty("keystore.password"));
            config = new SalesforceLoginConfig(loginUrl, clientId, username, keystore, lazyLogin);
        } else {
            throw new IllegalArgumentException("Must specifiy parameters in " + TEST_LOGIN_PROPERTIES);
        }

    }

    public static SalesforceLoginConfig getLoginConfig() {
        return INSTANCE.config;
    }

    public static Properties testLoginProperties() {
        return INSTANCE.properties;
    }

}