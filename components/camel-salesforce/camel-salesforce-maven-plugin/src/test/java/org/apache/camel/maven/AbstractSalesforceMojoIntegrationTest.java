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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.codegen.AbstractSalesforceExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractSalesforceMojoIntegrationTest {

    private static final Map<String, List<String>> NO_HEADERS = Collections.emptyMap();

    private static final String TEST_LOGIN_PROPERTIES = "../test-salesforce-login.properties";

    @Test
    public void shouldLoginAndProvideRestClient() throws IOException, MojoExecutionException, MojoFailureException {
        final AbstractSalesforceMojo mojo = new AbstractSalesforceMojo() {
            final Logger logger = LoggerFactory.getLogger(AbstractSalesforceExecution.class.getName());

            @Override
            protected AbstractSalesforceExecution getSalesforceExecution() {
                return new AbstractSalesforceExecution() {
                    @Override
                    protected void executeWithClient() {
                        assertThat(getRestClient()).isNotNull();

                        getRestClient().getGlobalObjects(NO_HEADERS, (response, headers, exception) -> {
                            assertThat(exception).isNull();
                        });
                    }

                    @Override
                    protected Logger getLog() {
                        return logger;
                    }
                };
            }
        };

        setup(mojo);

        mojo.execute();
    }

    static void setup(final AbstractSalesforceMojo mojo) throws IOException {
        // load test-salesforce-login properties
        try (final InputStream stream = new FileInputStream(TEST_LOGIN_PROPERTIES)) {
            final Properties properties = new Properties();
            properties.load(stream);
            mojo.clientId = properties.getProperty("salesforce.client.id");
            mojo.clientSecret = properties.getProperty("salesforce.client.secret");
            mojo.userName = properties.getProperty("salesforce.username");
            mojo.password = properties.getProperty("salesforce.password");
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
}
