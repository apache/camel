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
package org.apache.camel.component.linkedin.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.ws.rs.WebApplicationException;

import org.apache.camel.component.linkedin.api.model.Error;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for resource tests.
 */
public abstract class AbstractResourceIntegrationTest extends Assert {

    protected static final Logger LOG = LoggerFactory.getLogger(PeopleResourceIntegrationTest.class);
    protected static final String DEFAULT_FIELDS = "";

    protected static LinkedInOAuthRequestFilter requestFilter;
    private static Properties properties;
    private static OAuthToken token;
    private static List<Object> resourceList = new ArrayList<Object>();

    @BeforeClass
    public static void beforeClass() throws Exception {
        properties = new Properties();
        properties.load(AbstractResourceIntegrationTest.class.getResourceAsStream("/test-options.properties"));

        requestFilter = createOAuthHelper();
    }

    private static LinkedInOAuthRequestFilter createOAuthHelper() throws IOException {
        final String userName = properties.getProperty("userName");
        final String userPassword = properties.getProperty("userPassword");
        final String clientId = properties.getProperty("clientId");
        final String clientSecret = properties.getProperty("clientSecret");
        final String redirectUri = properties.getProperty("redirectUri");

        final OAuthScope[] scopes;
        final String scope = properties.getProperty("scope");
        if (scope != null) {
            scopes = OAuthScope.fromValues(scope.split(","));
        } else {
            scopes = null;
        }

        final OAuthSecureStorage secureStorage = new OAuthSecureStorage() {
            @Override
            public OAuthToken getOAuthToken() {
                return token;
            }

            @Override
            public void saveOAuthToken(OAuthToken newToken) {
                token = newToken;
            }
        };

        final OAuthParams oAuthParams = new OAuthParams(userName, userPassword, secureStorage,
            clientId, clientSecret, redirectUri, scopes);
        return new LinkedInOAuthRequestFilter(oAuthParams, null, false, null);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        // close all proxies
        for (Object resource : resourceList) {
            try {
                WebClient.client(resource).close();
            } catch (Exception ignore) {
            }
        }
        if (requestFilter != null) {
            requestFilter.close();
        }
        // TODO save and load token from test-options.properties
    }

    protected static <T> T getResource(Class<T> resourceClass) {
        if (requestFilter == null) {
            throw new IllegalStateException(AbstractResourceIntegrationTest.class.getName()
                                            + ".beforeClass must be invoked before getResource");
        }
        final T resource = JAXRSClientFactory.create(LinkedInOAuthRequestFilter.BASE_ADDRESS, resourceClass,
//            Arrays.asList(new Object[] { requestFilter, new LinkedInExceptionResponseFilter() } ));
            Arrays.asList(new Object[]{requestFilter, new EnumQueryParamConverterProvider()}));
        resourceList.add(resource);
        return resource;
    }

    protected void execute(Runnable runnable) {
        try {
            runnable.run();
        } catch (WebApplicationException e) {
            final org.apache.camel.component.linkedin.api.model.Error error = e.getResponse().readEntity(Error.class);
            assertNotNull(error);
            LOG.error("Error: {}", error.getMessage());
            throw new LinkedInException(error, e.getResponse());
        }
    }
}
