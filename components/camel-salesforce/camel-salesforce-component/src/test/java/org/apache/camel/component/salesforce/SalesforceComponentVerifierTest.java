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
    private static final String clientId = getSystemPropertyOrEnvVar("salesforce.clientid");
    private static final String clientSecret = getSystemPropertyOrEnvVar("salesforce.clientsecret");
    private static final String userName = getSystemPropertyOrEnvVar("salesforce.userName");
    private static final String password = getSystemPropertyOrEnvVar("salesforce.password");

    @Override
    protected void doPreSetup() throws Exception {
        Assume.assumeNotNull(clientId);
        Assume.assumeNotNull(clientSecret);
        Assume.assumeNotNull(userName);
        Assume.assumeNotNull(password);
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
        parameters.put("clientId", clientId);
        parameters.put("clientSecret", clientSecret);
        parameters.put("userName", userName);
        parameters.put("password", password);

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

    protected SalesforceComponentVerifier getVerifier() {
        SalesforceComponent component = context().getComponent("salesforce", SalesforceComponent.class);
        SalesforceComponentVerifier verifier = (SalesforceComponentVerifier)component.getVerifier();

        return verifier;
    }

    // *********************************
    // Parameters validation
    // *********************************


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

        Assert.assertEquals(ComponentVerifier.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(2, result.getErrors().size());

        // Exception
        Assert.assertEquals(ComponentVerifier.CODE_EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getAttributes().get(ComponentVerifier.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getAttributes().get(ComponentVerifier.EXCEPTION_INSTANCE) instanceof SalesforceException);
        Assert.assertEquals(400, result.getErrors().get(0).getAttributes().get(ComponentVerifier.HTTP_CODE));

        // Salesforce Error
        Assert.assertEquals("salesforce", result.getErrors().get(1).getAttributes().get(ComponentVerifier.ERROR_TYPE_ATTRIBUTE));
        Assert.assertEquals("authentication failure", result.getErrors().get(1).getDescription());
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
        Assert.assertEquals(ComponentVerifier.CODE_EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getAttributes().get(ComponentVerifier.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getAttributes().get(ComponentVerifier.EXCEPTION_INSTANCE) instanceof SalesforceException);
        Assert.assertEquals(400, result.getErrors().get(0).getAttributes().get(ComponentVerifier.HTTP_CODE));

        // Salesforce Error
        Assert.assertEquals("salesforce", result.getErrors().get(1).getAttributes().get(ComponentVerifier.ERROR_TYPE_ATTRIBUTE));
        Assert.assertEquals("client identifier invalid", result.getErrors().get(1).getDescription());
    }
}
