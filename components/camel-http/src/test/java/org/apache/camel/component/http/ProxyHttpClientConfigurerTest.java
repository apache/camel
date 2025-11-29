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
package org.apache.camel.component.http;

import java.lang.reflect.Field;
import java.util.Set;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProxyHttpClientConfigurerTest {

    @Test
    public void testBasicProxyConfiguration() throws Exception {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer("proxy.example.com", 8080, "http");

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured");
        assertEquals("proxy.example.com", proxy.getHostName());
        assertEquals(8080, proxy.getPort());
        assertEquals("http", proxy.getSchemeName());
    }

    @Test
    public void testProxyConfigurationWithNonProxyHosts() throws Exception {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer("proxy.example.com", 8080, "http", "localhost,127.0.0.1");

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpRoutePlanner routePlanner = getRoutePlannerFromBuilder(builder);
        assertNotNull(routePlanner, "Route planner should be configured");
        assertInstanceOf(CamelProxyRoutePlanner.class, routePlanner,
                "Should use CamelProxyRoutePlanner when nonProxyHosts is configured");
    }

    @Test
    public void testProxyConfigurationWithEmptyNonProxyHosts() throws Exception {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer("proxy.example.com", 8080, "http", "");

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured even with empty nonProxyHosts");
    }

    @Test
    public void testProxyConfigurationWithNullNonProxyHosts() throws Exception {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer("proxy.example.com", 8080, "http", (String) null);

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured with null nonProxyHosts");
    }

    @Test
    public void testNonProxyHostsParsing() {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer(
                        "proxy.example.com", 8080, "http",
                        "localhost,127.0.0.1,*.internal.com");

        Set<String> nonProxyHosts = getNonProxyHostsFromConfigurer(configurer);
        assertNotNull(nonProxyHosts);
        assertEquals(3, nonProxyHosts.size());
        assertTrue(nonProxyHosts.contains("localhost"));
        assertTrue(nonProxyHosts.contains("127.0.0.1"));
        assertTrue(nonProxyHosts.contains("*.internal.com"));
    }

    @Test
    public void testNonProxyHostsParsingWithSpaces() {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer(
                        "proxy.example.com", 8080, "http",
                        " localhost , 127.0.0.1 , *.internal.com ");

        Set<String> nonProxyHosts = getNonProxyHostsFromConfigurer(configurer);
        assertNotNull(nonProxyHosts);
        assertEquals(3, nonProxyHosts.size());
        assertTrue(nonProxyHosts.contains("localhost"));
        assertTrue(nonProxyHosts.contains("127.0.0.1"));
        assertTrue(nonProxyHosts.contains("*.internal.com"));
    }

    @Test
    public void testNonProxyHostsParsingWithEmptyElements() {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer(
                        "proxy.example.com", 8080, "http",
                        "localhost,,127.0.0.1,  ,*.internal.com");

        Set<String> nonProxyHosts = getNonProxyHostsFromConfigurer(configurer);
        assertNotNull(nonProxyHosts);
        assertEquals(3, nonProxyHosts.size(), "Empty elements should be filtered out");
        assertTrue(nonProxyHosts.contains("localhost"));
        assertTrue(nonProxyHosts.contains("127.0.0.1"));
        assertTrue(nonProxyHosts.contains("*.internal.com"));
    }

    @Test
    public void testProxyConfigurationWithCredentials() throws Exception {
        HttpCredentialsHelper credentialsHelper = new HttpCredentialsHelper();
        ProxyHttpClientConfigurer configurer = new ProxyHttpClientConfigurer(
                "proxy.example.com", 8080, "http", "testuser", "testpass", null, null,
                credentialsHelper, null);

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured");
        assertEquals("proxy.example.com", proxy.getHostName());

        assertNotNull(getCredentialsProviderFromBuilder(builder),
                "Credentials provider should be set when username and password are provided");
    }

    @Test
    public void testProxyConfigurationWithNTCredentials() throws Exception {
        HttpCredentialsHelper credentialsHelper = new HttpCredentialsHelper();
        ProxyHttpClientConfigurer configurer = new ProxyHttpClientConfigurer(
                "proxy.example.com", 8080, "http", "testuser", "testpass", "DOMAIN", "WORKSTATION",
                credentialsHelper, null);

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured");

        assertNotNull(getCredentialsProviderFromBuilder(builder),
                "Credentials provider should be set for NT credentials");
    }

    @Test
    public void testProxyConfigurationWithoutCredentials() throws Exception {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer("proxy.example.com", 8080, "http");

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured");
    }

    @Test
    public void testProxyConfigurationWithNullPassword() throws Exception {
        HttpCredentialsHelper credentialsHelper = new HttpCredentialsHelper();
        ProxyHttpClientConfigurer configurer = new ProxyHttpClientConfigurer(
                "proxy.example.com", 8080, "http", "testuser", null, null, null,
                credentialsHelper, null);

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured");
    }

    @Test
    public void testProxyConfigurationWithHttpsScheme() throws Exception {
        ProxyHttpClientConfigurer configurer
                = new ProxyHttpClientConfigurer("proxy.example.com", 8443, "https");

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpHost proxy = getProxyFromBuilder(builder);
        assertNotNull(proxy, "Proxy should be configured");
        assertEquals("https", proxy.getSchemeName());
        assertEquals(8443, proxy.getPort());
    }

    @Test
    public void testCompleteProxyConfiguration() throws Exception {
        HttpCredentialsHelper credentialsHelper = new HttpCredentialsHelper();
        ProxyHttpClientConfigurer configurer = new ProxyHttpClientConfigurer(
                "proxy.example.com", 8080, "http", "testuser", "testpass", null, null,
                credentialsHelper, "localhost,*.internal.com");

        HttpClientBuilder builder = HttpClientBuilder.create();
        configurer.configureHttpClient(builder);

        HttpRoutePlanner routePlanner = getRoutePlannerFromBuilder(builder);
        assertNotNull(routePlanner, "Route planner should be configured");
        assertInstanceOf(CamelProxyRoutePlanner.class, routePlanner);

        assertNotNull(getCredentialsProviderFromBuilder(builder),
                "Credentials provider should be set");

        Set<String> nonProxyHosts = getNonProxyHostsFromConfigurer(configurer);
        assertEquals(2, nonProxyHosts.size());
    }

    private HttpHost getProxyFromBuilder(HttpClientBuilder builder) throws Exception {
        Field proxyField = HttpClientBuilder.class.getDeclaredField("proxy");
        proxyField.setAccessible(true);
        return (HttpHost) proxyField.get(builder);
    }

    private HttpRoutePlanner getRoutePlannerFromBuilder(HttpClientBuilder builder) throws Exception {
        Field routePlannerField = HttpClientBuilder.class.getDeclaredField("routePlanner");
        routePlannerField.setAccessible(true);
        return (HttpRoutePlanner) routePlannerField.get(builder);
    }

    private Object getCredentialsProviderFromBuilder(HttpClientBuilder builder) throws Exception {
        Field credentialsProviderField = HttpClientBuilder.class.getDeclaredField("credentialsProvider");
        credentialsProviderField.setAccessible(true);
        return credentialsProviderField.get(builder);
    }

    private Set<String> getNonProxyHostsFromConfigurer(ProxyHttpClientConfigurer configurer) {
        try {
            Field nonProxyHostsField = ProxyHttpClientConfigurer.class.getDeclaredField("nonProxyHosts");
            nonProxyHostsField.setAccessible(true);
            return (Set<String>) nonProxyHostsField.get(configurer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get nonProxyHosts field", e);
        }
    }
}
