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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ComponentVerifier;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SalesforceComponentVerifierTest extends CamelTestSupport {
    private static final String CLIENT_ID = getSystemPropertyOrEnvVar("salesforce.clientid");
    private static final String CLIENT_SECRET = getSystemPropertyOrEnvVar("salesforce.clientsecret");
    private static final String USERNAME = getSystemPropertyOrEnvVar("salesforce.userName");
    private static final String PASSWORD = getSystemPropertyOrEnvVar("salesforce.password");

    @Override
    protected void doPreSetup() throws Exception {
        Assume.assumeNotNull(CLIENT_ID);
        Assume.assumeNotNull(CLIENT_SECRET);
        Assume.assumeNotNull(USERNAME);
        Assume.assumeNotNull(PASSWORD);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    // *********************************
    // Helpers
    // *********************************

    protected Map<String, Object> getParameters() {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", CLIENT_ID);
        parameters.put("clientSecret", CLIENT_SECRET);
        parameters.put("userName", USERNAME);
        parameters.put("password", PASSWORD);


        return parameters;
    }

    public static String getSystemPropertyOrEnvVar(String systemProperty) {
        String answer = System.getProperty(systemProperty);
        if (ObjectHelper.isEmpty(answer)) {
            String envProperty = systemProperty.toUpperCase().replaceAll("[.-]", "_");
            answer = System.getenv(envProperty);
        }

        return answer;
    }

    protected ComponentVerifier getVerifier() {
        SalesforceComponent component = context().getComponent("salesforce", SalesforceComponent.class);
        return component.getVerifier();
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Test
    public void testUsernamePasswordParameters() {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", "clientId");
        parameters.put("clientSecret", "clientSecret");
        parameters.put("userName", "userName");
        parameters.put("password", "password");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testRefreshTokenParameters() {
        Map<String, Object> parameters = getParameters();
        parameters.put("clientId", "clientId");
        parameters.put("clientSecret", "clientSecret");
        parameters.put("refreshToken", "refreshToken");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testWrongParameters() {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", "clientId");
        parameters.put("clientSecret", "clientSecret");
        parameters.put("password", "password");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(3, result.getErrors().size());

        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.INCOMPLETE_PARAMETER_GROUP, result.getErrors().get(0).getCode());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.INCOMPLETE_PARAMETER_GROUP, result.getErrors().get(1).getCode());
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.INCOMPLETE_PARAMETER_GROUP, result.getErrors().get(2).getCode());
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Test
    public void testConnectivity() {
        Map<String, Object> parameters = getParameters();
        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongUserName() {
        Map<String, Object> parameters = getParameters();
        parameters.put("userName", "not-a-salesforce-user");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(2, result.getErrors().size());

        // Exception
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof SalesforceException);
        Assert.assertEquals(400, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.HttpAttribute.HTTP_CODE));

        // Salesforce Error
        Assert.assertEquals("invalid_grant", result.getErrors().get(1).getDetail("salesforce_code"));
    }

    @Test
    public void testConnectivityWithWrongSecrets() {
        Map<String, Object> parameters = getParameters();
        parameters.put("clientId", "wrong-client-id");
        parameters.put("clientSecret", "wrong-client-secret");

        ComponentVerifier.Result result = getVerifier().verify(ComponentVerifier.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(2, result.getErrors().size());

        // Exception
        Assert.assertEquals(ComponentVerifier.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof SalesforceException);
        Assert.assertEquals(400, result.getErrors().get(0).getDetails().get(ComponentVerifier.VerificationError.HttpAttribute.HTTP_CODE));

        // Salesforce Error
        Assert.assertEquals("invalid_client_id", result.getErrors().get(1).getDetail("salesforce_code"));
    }
}
