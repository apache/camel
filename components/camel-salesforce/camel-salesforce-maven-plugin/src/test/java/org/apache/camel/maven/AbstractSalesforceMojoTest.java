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
package org.apache.camel.maven;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.component.salesforce.AuthenticationType;
import org.apache.camel.component.salesforce.SalesforceEndpointConfig;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public abstract class AbstractSalesforceMojoTest {

    static final String TEST_LOGIN_PROPERTIES = "../test-salesforce-login.properties";

    static void setupUsernamePassword(final AbstractSalesforceMojo mojo) throws IOException {
        // load test-salesforce-login properties
        try (final InputStream stream = new FileInputStream(TEST_LOGIN_PROPERTIES)) {
            final Properties properties = new Properties();
            properties.load(stream);
            mojo.clientId = properties.getProperty("salesforce.client.id");
            mojo.clientSecret = properties.getProperty("salesforce.client.secret");
            mojo.userName = properties.getProperty("salesforce.username");
            mojo.password = properties.getProperty("salesforce.password");
            assumeTrue(mojo.password != null && !mojo.password.isEmpty(),
                    "Property 'salesforce.password' must be set in " + TEST_LOGIN_PROPERTIES
                                                                          + " for USERNAME_PASSWORD authentication test");
            mojo.loginUrl = properties.getProperty("salesforce.login.url");
            mojo.version = SalesforceEndpointConfig.DEFAULT_VERSION;
        } catch (final FileNotFoundException e) {
            final FileNotFoundException exception
                    = new FileNotFoundException(
                            "Create a properties file named " + TEST_LOGIN_PROPERTIES
                                                + " with clientId, clientSecret, userName, password"
                                                + " for a Salesforce account with Merchandise and Invoice objects from Salesforce Guides.");
            exception.initCause(e);

            throw exception;
        }
    }

    static void setupJwt(final AbstractSalesforceMojo mojo) throws IOException {
        // load test-salesforce-login properties
        try (final InputStream stream = new FileInputStream(TEST_LOGIN_PROPERTIES)) {
            final Properties properties = new Properties();
            properties.load(stream);
            mojo.clientId = properties.getProperty("salesforce.client.id");
            mojo.userName = properties.getProperty("salesforce.username");
            mojo.loginUrl = properties.getProperty("salesforce.login.url");
            mojo.keystoreResource = properties.getProperty("salesforce.keystore.resource");
            mojo.keystorePassword = properties.getProperty("salesforce.keystore.password");
            mojo.keystoreType = properties.getProperty("salesforce.keystore.type");
            mojo.version = SalesforceEndpointConfig.DEFAULT_VERSION;
        } catch (final FileNotFoundException e) {
            final FileNotFoundException exception
                    = new FileNotFoundException(
                            "Create a properties file named " + TEST_LOGIN_PROPERTIES
                                                + " with clientId, userName, keyStoreResource, keyStorePassword, keyStoreType"
                                                + " for a Salesforce account with Merchandise and Invoice objects from Salesforce Guides.");
            exception.initCause(e);

            throw exception;
        }
    }

    static void setupClientCredentials(final AbstractSalesforceMojo mojo) throws IOException {
        // load test-salesforce-login properties
        try (final InputStream stream = new FileInputStream(TEST_LOGIN_PROPERTIES)) {
            final Properties properties = new Properties();
            properties.load(stream);
            mojo.clientId = properties.getProperty("salesforce.client.id");
            mojo.clientSecret = properties.getProperty("salesforce.client.secret");
            mojo.authenticationType = AuthenticationType.CLIENT_CREDENTIALS;
            mojo.loginUrl = properties.getProperty("salesforce.login.url");
            mojo.version = SalesforceEndpointConfig.DEFAULT_VERSION;
        } catch (final FileNotFoundException e) {
            final FileNotFoundException exception
                    = new FileNotFoundException(
                            "Create a properties file named " + TEST_LOGIN_PROPERTIES
                                                + " with clientId, clientSecret"
                                                + " for a Salesforce connected app configured for Client Credentials flow.");
            exception.initCause(e);

            throw exception;
        }
    }
}
