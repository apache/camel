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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.component.salesforce.codegen.AbstractSalesforceExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.maven.AbstractSalesforceMojoTest.setupClientCredentials;
import static org.apache.camel.maven.AbstractSalesforceMojoTest.setupJwt;
import static org.apache.camel.maven.AbstractSalesforceMojoTest.setupUsernamePassword;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies Salesforce login with all supported authentication types. The {@code ManualIT} suffix
 * prevents automatic execution by Maven Surefire and Failsafe — run explicitly with:
 *
 * <pre>
 * mvn test -Dtest=CamelSalesforceLoginManualIT
 * </pre>
 *
 * Requires a properties file at {@code ../test-salesforce-login.properties} with:
 *
 * <pre>
 * # Required for USERNAME_PASSWORD test
 * salesforce.client.id=...
 * salesforce.client.secret=...
 * salesforce.username=...
 * salesforce.password=...
 * salesforce.login.url=https://your-domain.my.salesforce.com
 *
 * # Required for CLIENT_CREDENTIALS test (uses client.id, client.secret, login.url from above)
 *
 * # Required for JWT test
 * salesforce.keystore.resource=...
 * salesforce.keystore.password=...
 * salesforce.keystore.type=JKS
 * </pre>
 */
public class CamelSalesforceLoginManualIT {

    private static final Map<String, List<String>> NO_HEADERS = Collections.emptyMap();

    private static final Logger logger = LoggerFactory.getLogger(CamelSalesforceLoginManualIT.class.getName());

    @Test
    public void shouldLoginWithUsernamePasswordAndProvideRestClient()
            throws IOException, MojoExecutionException, MojoFailureException {
        logger.info("Testing shouldLoginWithUsernamePasswordAndProvideRestClient()");
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

        setupUsernamePassword(mojo);

        mojo.execute();
    }

    @Test
    public void shouldLoginWithJwtAndProvideRestClient() throws IOException, MojoExecutionException, MojoFailureException {
        logger.info("Testing shouldLoginWithJwtAndProvideRestClient()");
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

        setupJwt(mojo);

        mojo.execute();
    }

    @Test
    public void shouldLoginWithClientCredentialsAndProvideRestClient()
            throws IOException, MojoExecutionException, MojoFailureException {
        logger.info("Testing shouldLoginWithClientCredentialsAndProvideRestClient()");
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

        setupClientCredentials(mojo);

        mojo.execute();
    }
}
