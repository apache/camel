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
package org.apache.camel.component.http;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HttpComponentVerifierTest extends BaseHttpTest {
    private static final String AUTH_USERNAME = "camel";
    private static final String AUTH_PASSWORD = "password";
    private static final int PORT = AvailablePortFinder.getNextAvailable();

    private Server localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = new Server(PORT);
        localServer.setHandler(handlers(
            contextHandler("/basic", new BasicValidationHandler("GET", null, null, getExpectedContent()))
        ));

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

    // *************************************************
    // Helpers
    // *************************************************

    protected String getLocalServerUri(String contextPath) throws Exception {
        return new StringBuilder()
            .append("http://")
            .append("localhost")
            .append(":")
            .append(PORT)
            .append(contextPath != null ? contextPath : "")
            .toString();
    }

    // *************************************************
    // Tests (parameters)
    // *************************************************

    @Test
    public void testParameters() throws Exception {
        HttpComponent component = context().getComponent("http", HttpComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/basic"));

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testMissingMandatoryParameters() throws Exception {
        HttpComponent component = context().getComponent("http", HttpComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());

        ComponentVerifier.VerificationError error = result.getErrors().get(0);

        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.MISSING_PARAMETER, error.getCode());
        Assert.assertTrue(error.getParameterKeys().contains("httpUri"));
    }

    // *************************************************
    // Tests (connectivity)
    // *************************************************

    @Test
    public void testConnectivity() throws Exception {
        HttpComponent component = context().getComponent("http", HttpComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", getLocalServerUri("/basic"));

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongUri() throws Exception {
        HttpComponent component = context().getComponent("http", HttpComponent.class);
        ComponentVerifier verifier = component.getVerifier();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("httpUri", "http://www.not-existing-uri.unknown");

        ComponentVerifier.Result result = verifier.verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(1, result.getErrors().size());

        ComponentVerifier.VerificationError error = result.getErrors().get(0);

        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.EXCEPTION, error.getCode());
        Assert.assertTrue(error.getParameterKeys().contains("httpUri"));
    }
}