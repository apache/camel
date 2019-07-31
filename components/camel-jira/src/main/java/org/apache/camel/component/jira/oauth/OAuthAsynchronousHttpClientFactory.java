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
package org.apache.camel.component.jira.oauth;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.Properties;

import javax.annotation.Nonnull;

import com.atlassian.event.api.EventPublisher;
import com.atlassian.httpclient.apache.httpcomponents.DefaultHttpClientFactory;
import com.atlassian.httpclient.api.HttpClient;
import com.atlassian.httpclient.api.factory.HttpClientOptions;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.DisposableHttpClient;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.sal.api.UrlMode;
import com.atlassian.sal.api.executor.ThreadLocalContextManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Similar to AsynchronousHttpClientFactory, except we use a custom OAuthHttpClientDecorator
 */
public class OAuthAsynchronousHttpClientFactory {

    private static final String JIRA_REST_CLIENT_VERSION = MavenUtils.getVersion("com.atlassian.jira", "jira-rest-java-client-api");

    @SuppressWarnings("unchecked")
    public DisposableHttpClient createClient(final URI serverUri, final AuthenticationHandler authenticationHandler) {
        final HttpClientOptions options = new HttpClientOptions();

        final DefaultHttpClientFactory defaultHttpClientFactory = new DefaultHttpClientFactory(new NoOpEventPublisher(),
                new RestClientApplicationProperties(serverUri),
                new ThreadLocalContextManager() {
                    @Override
                    public Object getThreadLocalContext() {
                        return null;
                    }

                    @Override
                    public void setThreadLocalContext(Object context) {
                    }

                    @Override
                    public void clearThreadLocalContext() {
                    }
                });

        final HttpClient httpClient = defaultHttpClientFactory.create(options);

        return new OAuthHttpClientDecorator(httpClient, authenticationHandler) {
            @Override
            public void destroy() throws Exception {
                defaultHttpClientFactory.dispose(httpClient);
            }
        };
    }

    private static class NoOpEventPublisher implements EventPublisher {
        @Override
        public void publish(Object o) {
        }

        @Override
        public void register(Object o) {
        }

        @Override
        public void unregister(Object o) {
        }

        @Override
        public void unregisterAll() {
        }
    }

    /**
     * These properties are used to present JRJC as a User-Agent during http requests.
     */
    private static final class RestClientApplicationProperties implements ApplicationProperties {

        private final String baseUrl;

        private RestClientApplicationProperties(URI jiraURI) {
            this.baseUrl = jiraURI.getPath();
        }

        @Override
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * We'll always have an absolute URL as a client.
         */
        @Nonnull
        @Override
        public String getBaseUrl(UrlMode urlMode) {
            return baseUrl;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Atlassian JIRA Rest Java Client";
        }

        @Nonnull
        @Override
        public String getPlatformId() {
            return ApplicationProperties.PLATFORM_JIRA;
        }

        @Nonnull
        @Override
        public String getVersion() {
            return JIRA_REST_CLIENT_VERSION;
        }

        @Nonnull
        @Override
        public Date getBuildDate() {
            // TODO implement using MavenUtils, JRJC-123
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public String getBuildNumber() {
            // TODO implement using MavenUtils, JRJC-123
            return String.valueOf(0);
        }

        @Override
        public File getHomeDirectory() {
            return new File(".");
        }

        @Override
        public String getPropertyValue(final String s) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

    private static final class MavenUtils {
        private static final Logger LOG = LoggerFactory.getLogger(MavenUtils.class);
        private static final String UNKNOWN_VERSION = "unknown";

        static String getVersion(String groupId, String artifactId) {
            final Properties props = new Properties();
            String pomProps = String.format("/META-INF/maven/%s/%s/pom.properties", groupId, artifactId);
            try (InputStream resourceAsStream  = AuthenticationHandler.class.getResourceAsStream(pomProps)) {
                props.load(resourceAsStream);
                return props.getProperty("version", UNKNOWN_VERSION);
            } catch (Exception e) {
                LOG.debug("Could not find version for Jira Rest Java Client maven artifact {}:{}. Error: {}", groupId, artifactId, e.getMessage());
                return UNKNOWN_VERSION;
            }
        }
    }

}
