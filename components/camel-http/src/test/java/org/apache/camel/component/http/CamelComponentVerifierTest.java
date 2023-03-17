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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.http.handler.AuthenticationValidationHandler;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.component.http.interceptor.RequestBasicAuth;
import org.apache.camel.component.http.interceptor.ResponseBasicUnauthorized;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.apache.hc.core5.http.io.HttpRequestHandler;
import org.apache.hc.core5.http.protocol.DefaultHttpProcessor;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.protocol.ResponseContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelComponentVerifierTest extends BaseHttpTest {
    private static final String AUTH_USERNAME = "camel";
    private static final String AUTH_PASSWORD = "password";

    private HttpServer localServer;
    private ComponentVerifierExtension verifier;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        super.setUp();

        localServer = ServerBootstrap.bootstrap()
                .setHttpProcessor(getHttpProcessor())
                .register("/basic", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .register("/auth",
                        new AuthenticationValidationHandler(
                                GET.name(), null, null, getExpectedContent(), AUTH_USERNAME, AUTH_PASSWORD))
                .register("/redirect", redirectTo(HttpStatus.SC_MOVED_PERMANENTLY, "/redirected"))
                .register("/redirected", new BasicValidationHandler(GET.name(), null, null, getExpectedContent()))
                .create();

        localServer.start();

        HttpComponent component = context().getComponent("http", HttpComponent.class);
        verifier = component.getVerifier();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private HttpProcessor getHttpProcessor() {
        return new DefaultHttpProcessor(
                Collections.singletonList(
                        new RequestBasicAuth()),
                Arrays.asList(
                        new ResponseContent(),
                        new ResponseBasicUnauthorized()));
    }

    // *************************************************
    // Helpers
    // *************************************************

    protected String getLocalServerUri(String contextPath) {
        return "http://localhost:"
               + localServer.getLocalPort()
               + (contextPath != null
                       ? contextPath.startsWith("/") ? contextPath : "/" + contextPath
                       : "");
    }

    private HttpRequestHandler redirectTo(int code, String path) {
        return (request, response, context) -> {
            response.setHeader("location", getLocalServerUri(path));
            response.setCode(code);
        };
    }

    // *************************************************
    // Tests (parameters)
    // *************************************************

    @Test
    public void testParameters() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/basic"));

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingMandatoryParameters() throws Exception {
        Map<String, Object> parameters = new HashMap<>();

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER, error.getCode());
        assertTrue(error.getParameterKeys().contains("httpUri"));
    }

    // *************************************************
    // Tests (connectivity)
    // *************************************************

    @Test
    public void testConnectivity() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/basic"));

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongUri() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", "http://www.not-existing-uri.unknown");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, error.getCode());
        assertTrue(error.getParameterKeys().contains("httpUri"));
    }

    @Test
    public void testConnectivityWithAuthentication() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/auth"));
        parameters.put("authUsername", AUTH_USERNAME);
        parameters.put("authPassword", AUTH_PASSWORD);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongAuthenticationData() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/auth"));
        parameters.put("authUsername", "unknown");
        parameters.put("authPassword", AUTH_PASSWORD);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.AUTHENTICATION, error.getCode());
        assertEquals(401, error.getDetails().get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE));
        assertTrue(error.getParameterKeys().contains("authUsername"));
        assertTrue(error.getParameterKeys().contains("authPassword"));
    }

    @Test
    public void testConnectivityWithRedirect() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/redirect"));

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithRedirectDisabled() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/redirect"));
        parameters.put("httpClient.redirectsEnabled", "false");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.GENERIC, error.getCode());
        assertEquals(getLocalServerUri("/redirected"),
                error.getDetails().get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_REDIRECT));
        assertTrue(error.getParameterKeys().contains("httpUri"));
    }

    @Test
    public void testConnectivityWithFollowRedirectEnabled() throws Exception {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/redirect"));
        parameters.put("httpMethod", "POST");
        parameters.put("followRedirects", "true");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
        assertEquals(0, result.getErrors().size());

    }

}
