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
package org.apache.camel.component.http4;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.http4.handler.AuthenticationValidationHandler;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.ResponseContent;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CamelComponentVerifierExtensionTest extends BaseHttpTest {
    private static final String AUTH_USERNAME = "camel";
    private static final String AUTH_PASSWORD = "password";

    private HttpServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap()
            .setHttpProcessor(getHttpProcessor())
            .registerHandler("/basic", new BasicValidationHandler("GET", null, null, getExpectedContent()))
            .registerHandler("/auth", new AuthenticationValidationHandler("GET", null, null, getExpectedContent(), AUTH_USERNAME, AUTH_PASSWORD))
            .registerHandler("/redirect", redirectTo(HttpStatus.SC_MOVED_PERMANENTLY, "/redirected"))
            .registerHandler("/redirected", new BasicValidationHandler("GET", null, null, getExpectedContent()))
            .create();

        localServer.start();

        super.setUp();
    }

    @After
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
        return new ImmutableHttpProcessor(
            Arrays.asList(
                new RequestBasicAuth()
            ),
            Arrays.asList(
                new ResponseContent(),
                new ResponseBasicUnauthorized())
        );
    }

    // *************************************************
    // Helpers
    // *************************************************

    protected String getLocalServerUri(String contextPath) {
        return new StringBuilder()
            .append("http://")
            .append(localServer.getInetAddress().getHostName())
            .append(":")
            .append(localServer.getLocalPort())
            .append(contextPath != null
                ? contextPath.startsWith("/") ? contextPath : "/" + contextPath
                : "")
            .toString();
    }

    private HttpRequestHandler redirectTo(int code, String path) {
        return new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setHeader("location", getLocalServerUri(path));
                response.setStatusCode(code);
            }
        };
    }

    // *************************************************
    // Tests (parameters)
    // *************************************************

    @Test
    public void testParameters() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/basic"));

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingMandatoryParameters() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();

        ComponentVerifier.Result result = verifier.verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.MISSING_PARAMETER, error.getCode());
        Assert.assertTrue(error.getParameterKeys().contains("httpUri"));
    }

    // *************************************************
    // Tests (connectivity)
    // *************************************************

    @Test
    public void testConnectivity() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/basic"));

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongUri() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", "http://www.not-existing-uri.unknown");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, error.getCode());
        Assert.assertTrue(error.getParameterKeys().contains("httpUri"));
    }

    @Test
    public void testConnectivityWithAuthentication() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/auth"));
        parameters.put("authUsername", AUTH_USERNAME);
        parameters.put("authPassword", AUTH_PASSWORD);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongAuthenticationData() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/auth"));
        parameters.put("authUsername", "unknown");
        parameters.put("authPassword", AUTH_PASSWORD);

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.AUTHENTICATION, error.getCode());
        Assert.assertEquals(401, error.getDetails().get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE));
        Assert.assertTrue(error.getParameterKeys().contains("authUsername"));
        Assert.assertTrue(error.getParameterKeys().contains("authPassword"));
    }

    @Test
    public void testConnectivityWithRedirect() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/redirect"));

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithRedirectDisabled() throws Exception {
        Component component = context().getComponent("http4");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/redirect"));
        parameters.put("httpClient.redirectsEnabled", "false");

        ComponentVerifierExtension.Result result = verifier.verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());

        ComponentVerifierExtension.VerificationError error = result.getErrors().get(0);

        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.GENERIC, error.getCode());
        Assert.assertEquals(getLocalServerUri("/redirected"), error.getDetails().get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_REDIRECT));
        Assert.assertTrue(error.getParameterKeys().contains("httpUri"));
    }
}