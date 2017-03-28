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
package org.apache.camel.component.weather;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.weather.http.AuthenticationHttpClientConfigurer;
import org.apache.camel.component.weather.http.AuthenticationMethod;
import org.apache.camel.component.weather.http.CompositeHttpConfigurer;
import org.apache.camel.component.weather.http.HttpClientConfigurer;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;

/**
 * A <a href="http://camel.apache.org/weather.html">Weather Component</a>.
 * <p/>
 * Camel uses <a href="http://openweathermap.org/api#weather">Open Weather</a> to get the information.
 */
public class WeatherComponent extends UriEndpointComponent {

    private HttpClient httpClient;

    public WeatherComponent() {
        super(WeatherEndpoint.class);
    }

    public WeatherComponent(CamelContext context) {
        super(context, WeatherEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        WeatherConfiguration configuration = new WeatherConfiguration(this);

        // and then override from parameters
        setProperties(configuration, parameters);

        httpClient = createHttpClient(configuration);
        WeatherEndpoint endpoint = new WeatherEndpoint(uri, this, configuration);
        return endpoint;
    }

    private HttpClient createHttpClient(WeatherConfiguration configuration) {
        HttpConnectionManager connectionManager = configuration.getHttpConnectionManager();
        if (connectionManager == null) {
            connectionManager = new MultiThreadedHttpConnectionManager();
        }
        HttpClient httpClient = new HttpClient(connectionManager);

        if (configuration.getProxyHost() != null && configuration.getProxyPort() != null) {
            httpClient.getHostConfiguration().setProxy(configuration.getProxyHost(),
                    configuration.getProxyPort());
        }

        if (configuration.getProxyAuthUsername() != null && configuration.getProxyAuthMethod() == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }

        CompositeHttpConfigurer configurer = new CompositeHttpConfigurer();
        if (configuration.getProxyAuthMethod() != null) {
            configureProxyAuth(configurer,
                    configuration.getProxyAuthMethod(),
                    configuration.getProxyAuthUsername(),
                    configuration.getProxyAuthPassword(),
                    configuration.getProxyAuthDomain(),
                    configuration.getProxyAuthHost());
        }

        configurer.configureHttpClient(httpClient);

        return httpClient;
    }

    private HttpClientConfigurer configureProxyAuth(CompositeHttpConfigurer configurer,
                                    String authMethod,
                                    String username,
                                    String password,
                                    String domain,
                                    String host) {
        // no proxy auth is in use
        if (username == null && authMethod == null) {
            return configurer;
        }

        // validate mandatory options given
        if (username != null && authMethod == null) {
            throw new IllegalArgumentException("Option proxyAuthMethod must be provided to use proxy authentication");
        }

        ObjectHelper.notNull(authMethod, "proxyAuthMethod");
        ObjectHelper.notNull(username, "proxyAuthUsername");
        ObjectHelper.notNull(password, "proxyAuthPassword");

        AuthenticationMethod auth = getCamelContext().getTypeConverter().convertTo(AuthenticationMethod.class, authMethod);

        if (auth == AuthenticationMethod.Basic || auth == AuthenticationMethod.Digest) {
            configurer.addConfigurer(AuthenticationHttpClientConfigurer.basicAutenticationConfigurer(true, username, password));
            return configurer;
        } else if (auth == AuthenticationMethod.NTLM) {
            // domain is mandatory for NTML
            ObjectHelper.notNull(domain, "proxyAuthDomain");
            configurer.addConfigurer(AuthenticationHttpClientConfigurer.ntlmAutenticationConfigurer(true, username, password, domain, host));
            return configurer;
        }

        throw new IllegalArgumentException("Unknown proxyAuthMethod " + authMethod);

    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}