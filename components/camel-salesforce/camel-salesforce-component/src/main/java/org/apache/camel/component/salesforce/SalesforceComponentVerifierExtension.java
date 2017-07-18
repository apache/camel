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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.NoSuchOptionException;
import org.apache.camel.component.extension.verifier.OptionsGroup;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.DefaultRestClient;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class SalesforceComponentVerifierExtension extends DefaultComponentVerifierExtension {

    SalesforceComponentVerifierExtension() {
        super("salesforce");
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        // Validate mandatory component options, needed to be done here as these
        // options are not properly marked as mandatory in the catalog.
        //
        // Validation rules are borrowed from SalesforceLoginConfig's validate
        // method, which support 3 workflow:
        //
        // - OAuth Username/Password Flow
        // - OAuth Refresh Token Flow:
        // - OAuth JWT Flow
        //
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
            .errors(ResultErrorHelper.requiresAny(parameters,
                OptionsGroup.withName(AuthenticationType.USERNAME_PASSWORD)
                    .options("clientId", "clientSecret", "userName", "password", "!refreshToken", "!keystore"),
                OptionsGroup.withName(AuthenticationType.REFRESH_TOKEN)
                    .options("clientId", "clientSecret", "refreshToken", "!password", "!keystore"),
                OptionsGroup.withName(AuthenticationType.JWT)
                    .options("clientId", "userName", "keystore", "!password", "!refreshToken")));

        // Validate using the catalog
        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        // Default is success
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        try {
            SalesforceEndpointConfig configuration = new SalesforceEndpointConfig();
            setProperties(configuration, parameters);

            SalesforceLoginConfig loginConfig = new SalesforceLoginConfig();
            setProperties(loginConfig, parameters);

            // Create a dummy SslContextFactory which is needed by SalesforceHttpClient
            // or the underlying jetty client fails with a NPE
            SSLContextParameters contextParameters = new SSLContextParameters();
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setSslContext(contextParameters.createSSLContext(getCamelContext()));

            SalesforceHttpClient httpClient = new SalesforceHttpClient(sslContextFactory);
            httpClient.setConnectTimeout(SalesforceComponent.CONNECTION_TIMEOUT);
            configureHttpProxy(httpClient, parameters);

            SalesforceSession session = new SalesforceSession(getCamelContext(), httpClient, httpClient.getTimeout(), loginConfig);
            DefaultRestClient client = new DefaultRestClient(httpClient, configuration.getApiVersion(), configuration.getFormat(), session);

            httpClient.setSession(session);
            httpClient.start();

            // For authentication check is is enough to use
            session.start();

            client.start();
            client.getVersions((response, exception) -> processSalesforceException(builder, Optional.ofNullable(exception)));
            client.stop();

            session.stop();

            httpClient.stop();
            httpClient.destroy();
        } catch (NoSuchOptionException e) {
            builder.error(
                ResultErrorBuilder.withMissingOption(e.getOptionName()).build()
            );
        } catch (SalesforceException e) {
            processSalesforceException(builder, Optional.of(e));
        } catch (Exception e) {
            builder.error(
                ResultErrorBuilder.withException(e).build()
            );
        }

        return builder.build();
    }

    // *********************************
    // Helpers
    // *********************************

    private void processSalesforceException(ResultBuilder builder, Optional<SalesforceException> exception) {
        exception.ifPresent(e -> {
            builder.error(
                ResultErrorBuilder.withException(e)
                    .detail(VerificationError.HttpAttribute.HTTP_CODE, e.getStatusCode())
                    .build()
            );

            for (RestError error : e.getErrors()) {
                builder.error(
                    ResultErrorBuilder.withCode(VerificationError.StandardCode.GENERIC)
                        .description(error.getMessage())
                        .parameterKeys(error.getFields())
                        .detail("salesforce_code", error.getErrorCode())
                        .build()
                );
            }
        });
    }

    private void configureHttpProxy(SalesforceHttpClient httpClient, Map<String, Object> parameters) throws NoSuchOptionException, URISyntaxException {
        Optional<String> httpProxyHost = getOption(parameters, "httpProxyHost", String.class);
        Optional<Integer> httpProxyPort = getOption(parameters, "httpProxyPort", Integer.class);
        Optional<String> httpProxyUsername = getOption(parameters, "httpProxyUsername", String.class);
        Optional<String> httpProxyPassword = getOption(parameters, "httpProxyPassword", String.class);

        if (httpProxyHost.isPresent() && httpProxyPort.isPresent()) {
            Origin.Address address = new Origin.Address(httpProxyHost.get(), httpProxyPort.get());
            Boolean isHttpProxySocks4 = getOption(parameters, "isHttpProxySocks4", Boolean.class, () -> false);
            Boolean isHttpProxySecure = getOption(parameters, "isHttpProxySecure", Boolean.class, () -> true);

            if (isHttpProxySocks4) {
                httpClient.getProxyConfiguration().getProxies().add(
                    new Socks4Proxy(address, isHttpProxySecure)
                );
            } else {
                httpClient.getProxyConfiguration().getProxies().add(
                    new HttpProxy(address, isHttpProxySecure)
                );
            }
        }

        if (httpProxyUsername.isPresent() && httpProxyPassword.isPresent()) {
            Boolean httpProxyUseDigestAuth = getOption(parameters, "httpProxyUseDigestAuth", Boolean.class, () -> false);
            String httpProxyAuthUri = getMandatoryOption(parameters, "httpProxyAuthUri", String.class);
            String httpProxyRealm = getMandatoryOption(parameters, "httpProxyRealm", String.class);

            if (httpProxyUseDigestAuth) {
                httpClient.getAuthenticationStore().addAuthentication(new DigestAuthentication(
                    new URI(httpProxyAuthUri),
                    httpProxyRealm,
                    httpProxyUsername.get(),
                    httpProxyPassword.get())
                );
            } else {
                httpClient.getAuthenticationStore().addAuthentication(new BasicAuthentication(
                    new URI(httpProxyAuthUri),
                    httpProxyRealm,
                    httpProxyUsername.get(),
                    httpProxyPassword.get())
                );
            }
        }
    }
}
