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
package org.apache.camel.component.platform.http.main;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;
import org.apache.camel.component.platform.http.main.authentication.MainAuthenticationConfigurer;
import org.apache.camel.component.platform.http.vertx.auth.AuthenticationConfig;
import org.apache.camel.main.ConfigurationPropertiesWithMandatoryFields;
import org.apache.camel.main.HttpServerAuthenticationConfigurationProperties;
import org.apache.camel.main.HttpServerConfigurationProperties;
import org.apache.camel.main.MainConstants;
import org.apache.camel.main.MainHttpServerFactory;
import org.apache.camel.spi.annotations.JdkService;

@JdkService(MainConstants.PLATFORM_HTTP_SERVER)
public class DefaultMainHttpServerFactory implements CamelContextAware, MainHttpServerFactory {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Service newHttpServer(HttpServerConfigurationProperties configuration) {
        MainHttpServer server = new MainHttpServer();

        server.setCamelContext(camelContext);
        server.setHost(configuration.getHost());
        server.setPort(configuration.getPort());
        server.setPath(configuration.getPath());
        if (configuration.getMaxBodySize() != null) {
            server.setMaxBodySize(configuration.getMaxBodySize());
        }
        server.setUseGlobalSslContextParameters(configuration.isUseGlobalSslContextParameters());
        server.setInfoEnabled(configuration.isInfoEnabled());
        server.setDevConsoleEnabled(configuration.isDevConsoleEnabled());
        server.setHealthCheckEnabled(configuration.isHealthCheckEnabled());
        server.setJolokiaEnabled(configuration.isJolokiaEnabled());
        server.setMetricsEnabled(configuration.isMetricsEnabled());
        server.setUploadEnabled(configuration.isUploadEnabled());
        server.setUploadSourceDir(configuration.getUploadSourceDir());

        if (configuration.authentication().isEnabled()) {
            configureAuthentication(server, configuration);
        }

        return server;
    }

    private void configureAuthentication(MainHttpServer server, HttpServerConfigurationProperties configuration) {
        AuthenticationConfig authenticationConfig = server.getConfiguration().getAuthenticationConfig();

        HttpServerAuthenticationConfigurationProperties authenticationProperties = configuration.getAuthentication();
        Optional<MainAuthenticationConfigurer> authenticationConfigurer
                = findAuthenticationConfigurerByConfigurationProperties(authenticationProperties);

        authenticationConfigurer.ifPresentOrElse(
                (configurer -> configurer.configureAuthentication(authenticationConfig, authenticationProperties)),
                (() -> {
                    throw new RuntimeException(
                            "Authentication for camel-platform-http-main is enabled but no complete authentication configuration is found.");
                }));
    }

    private Optional<MainAuthenticationConfigurer> findAuthenticationConfigurerByConfigurationProperties(
            HttpServerAuthenticationConfigurationProperties authenticationConfigurationProperties) {

        MainAuthenticationConfigurer result = null;
        for (String authenticationTypeName : HttpServerAuthenticationConfigurationProperties.SUPPORTED_AUTHENTICATION_TYPES) {
            if (authenticationTypeIsEnabled(authenticationConfigurationProperties, authenticationTypeName)) {
                try {
                    if (result != null) {
                        throw new RuntimeException(
                                "Cannot configure authentication for MainHttpServer as more than one authentication configuration is present");
                    }
                    String configurerQualifiedName = MainAuthenticationConfigurer.class.getPackageName() + "."
                                                     + authenticationTypeName + "AuthenticationConfigurer";
                    result = (MainAuthenticationConfigurer) Class
                            .forName(configurerQualifiedName)
                            .getDeclaredConstructor()
                            .newInstance();
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Could not create MainAuthenticationConfigurer for authentication type " + authenticationTypeName,
                            e);
                }
            }
        }
        return Optional.ofNullable(result);
    }

    private boolean authenticationTypeIsEnabled(
            HttpServerAuthenticationConfigurationProperties authenticationProperties, String authenticationTypeName) {
        try {
            ConfigurationPropertiesWithMandatoryFields propertiesForAuthenticationType
                    = (ConfigurationPropertiesWithMandatoryFields) HttpServerAuthenticationConfigurationProperties.class
                            .getMethod("get" + authenticationTypeName)
                            .invoke(authenticationProperties);
            return propertiesForAuthenticationType.areMandatoryFieldsFilled();
        } catch (NoSuchMethodException noSuchMethodException) {
            throw new RuntimeException(
                    "Not found authentication configuration of type " + authenticationTypeName, noSuchMethodException);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
