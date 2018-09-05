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
package org.apache.camel.component.servicenow;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.component.servicenow.annotations.ServiceNowSysParm;
import org.apache.camel.component.servicenow.auth.AuthenticationRequestFilter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.cxf.configuration.jsse.TLSClientParameters;
import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServiceNowClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceNowClient.class);

    private final CamelContext camelContext;
    private final ServiceNowConfiguration configuration;
    private final WebClient client;

    public ServiceNowClient(CamelContext camelContext, ServiceNowConfiguration configuration) {
        this.camelContext = camelContext;
        this.configuration = configuration;
        this.client = WebClient.create(
            configuration.getApiUrl(),
            Arrays.asList(
                new AuthenticationRequestFilter(configuration),
                new JacksonJsonProvider(configuration.getOrCreateMapper())
            ),
            true
        );

        configureRequestContext(camelContext, configuration, client);
        configureTls(camelContext, configuration, client);
        configureHttpClientPolicy(camelContext, configuration, client);
        configureProxyAuthorizationPolicy(camelContext, configuration, client);
    }

    public ServiceNowClient types(MediaType type) {
        return types(type, type);
    }

    public ServiceNowClient types(MediaType accept, MediaType type) {
        client.accept(accept);
        client.type(type);
        return this;
    }

    public ServiceNowClient path(Object path) {
        if (ObjectHelper.isNotEmpty(path)) {
            client.path(path);
        }

        return this;
    }

    public ServiceNowClient type(MediaType ct) {
        client.type(ct);
        return this;
    }

    public ServiceNowClient type(String type) {
        client.type(type);
        return this;
    }

    public ServiceNowClient accept(MediaType... types) {
        client.accept(types);
        return this;
    }

    public ServiceNowClient accept(String... types) {
        client.accept(types);
        return this;
    }

    public ServiceNowClient query(String name, Object... values) {
        client.query(name, values);
        return this;
    }

    public ServiceNowClient queryF(String name, String format, Object... values) {
        client.query(name, String.format(format, values));
        return this;
    }

    public ServiceNowClient query(ServiceNowParam param, Message message) {
        Object value = param.getHeaderValue(message, configuration);
        if (value != null) {
            client.query(param.getId(), value);
        }

        return this;
    }

    public ServiceNowClient query(Class<?> model) {
        if (model != null) {
            String name;
            String value;

            for (ServiceNowSysParm parm : model.getAnnotationsByType(ServiceNowSysParm.class)) {
                name = parm.name();
                value = parm.value();

                // SysParms defined on model have precedence and replace query param
                // with same name set via Message headers.
                if (ObjectHelper.isNotEmpty(name) && ObjectHelper.isNotEmpty(value)) {
                    LOGGER.debug("Replace query param {} with value {}", name, value);
                    client.replaceQueryParam(name, value);
                }
            }
        }

        return this;
    }

    public Response invoke(String httpMethod) throws Exception {
        return invoke(client, httpMethod, null);
    }

    public Response invoke(String httpMethod, Object body) throws Exception {
        return invoke(client, httpMethod, body);
    }

    public <T> T trasform(String httpMethod, Function<Response, T> function) throws Exception {
        return function.apply(invoke(client, httpMethod, null));
    }

    public <T> T trasform(String httpMethod, Object body, Function<Response, T> function) throws Exception {
        return function.apply(invoke(client, httpMethod, body));
    }

    public ServiceNowClient reset() {
        client.back(true);
        client.reset();
        client.resetQuery();

        return this;
    }

    // *******************************
    // Helpers
    // *******************************

    private Response invoke(WebClient client, String httpMethod, Object body) throws Exception {
        Response response = client.invoke(httpMethod, body);
        int code = response.getStatus();

        // Only ServiceNow known error status codes are mapped
        // See http://wiki.servicenow.com/index.php?title=REST_API#REST_Response_HTTP_Status_Codes
        switch(code) {
        case 200:
        case 201:
        case 204:
            // Success
            break;
        case 400:
        case 401:
        case 403:
        case 404:
        case 405:
        case 406:
        case 415:
            ServiceNowExceptionModel model = response.readEntity(ServiceNowExceptionModel.class);
            throw new ServiceNowException(
                code,
                model.getStatus(),
                model.getError().get("message"),
                model.getError().get("detail")
            );
        default:
            throw new ServiceNowException(
                code,
                response.readEntity(Map.class)
            );
        }

        return response;
    }

    private static void configureRequestContext(
            CamelContext context, ServiceNowConfiguration configuration, WebClient client) {

        WebClient.getConfig(client)
            .getRequestContext()
            .put("org.apache.cxf.http.header.split", true);
    }

    private static void configureTls(
        CamelContext camelContext, ServiceNowConfiguration configuration, WebClient client) {

        SSLContextParameters sslContextParams = configuration.getSslContextParameters();
        if (sslContextParams != null) {
            HTTPConduit conduit = WebClient.getConfig(client).getHttpConduit();
            TLSClientParameters tlsClientParams = conduit.getTlsClientParameters();
            if (tlsClientParams == null) {
                tlsClientParams = new TLSClientParameters();
            }

            try {
                SSLContext sslContext = sslContextParams.createSSLContext(camelContext);
                tlsClientParams.setSSLSocketFactory(sslContext.getSocketFactory());

                conduit.setTlsClientParameters(tlsClientParams);
            } catch (IOException | GeneralSecurityException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    private static void configureHttpClientPolicy(
            CamelContext context, ServiceNowConfiguration configuration, WebClient client) {

        HTTPClientPolicy httpPolicy = configuration.getHttpClientPolicy();
        if (httpPolicy == null) {
            String host = configuration.getProxyHost();
            Integer port = configuration.getProxyPort();

            if (host != null && port != null) {
                httpPolicy = new HTTPClientPolicy();
                httpPolicy.setProxyServer(host);
                httpPolicy.setProxyServerPort(port);
            }
        }

        if (httpPolicy != null) {
            WebClient.getConfig(client).getHttpConduit().setClient(httpPolicy);
        }
    }

    private static void configureProxyAuthorizationPolicy(
            CamelContext context, ServiceNowConfiguration configuration, WebClient client) {

        ProxyAuthorizationPolicy proxyPolicy = configuration.getProxyAuthorizationPolicy();
        if (proxyPolicy == null) {
            String username = configuration.getProxyUserName();
            String password = configuration.getProxyPassword();

            if (username != null && password != null) {
                proxyPolicy = new ProxyAuthorizationPolicy();
                proxyPolicy.setAuthorizationType("Basic");
                proxyPolicy.setUserName(username);
                proxyPolicy.setPassword(password);
            }
        }

        if (proxyPolicy != null) {
            WebClient.getConfig(client).getHttpConduit().setProxyAuthorization(proxyPolicy);
        }
    }
}
