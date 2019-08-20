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
package org.apache.camel.component.salesforce;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.component.extension.ComponentVerifierExtension;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.Assert;
import org.junit.Test;

public class SalesforceComponentVerifierExtensionIntegrationTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    // *********************************
    // Helpers
    // *********************************

    protected Map<String, Object> getParameters() {
        SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", loginConfig.getClientId());
        parameters.put("clientSecret", loginConfig.getClientSecret());
        parameters.put("userName", loginConfig.getUserName());
        parameters.put("password", loginConfig.getPassword());

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

    protected ComponentVerifierExtension getExtension() {
        Component component = context().getComponent("salesforce");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        return verifier;
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Test
    public void testConnectivity() {
        Map<String, Object> parameters = getParameters();
        ComponentVerifierExtension.Result result = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testConnectivityWithWrongUserName() {
        Map<String, Object> parameters = getParameters();
        parameters.put("userName", "not-a-salesforce-user");

        ComponentVerifierExtension.Result result = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(2, result.getErrors().size());

        // Exception
        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getDetails()
            .get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof SalesforceException);
        Assert.assertEquals(400, result.getErrors().get(0).getDetails().get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE));

        // Salesforce Error
        Assert.assertEquals("invalid_grant", result.getErrors().get(1).getDetail("salesforce_code"));
    }

    @Test
    public void testConnectivityWithWrongSecrets() {
        Map<String, Object> parameters = getParameters();
        parameters.put("clientId", "wrong-client-id");
        parameters.put("clientSecret", "wrong-client-secret");

        ComponentVerifierExtension.Result result = getExtension().verify(ComponentVerifierExtension.Scope.CONNECTIVITY, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(2, result.getErrors().size());

        // Exception
        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.EXCEPTION, result.getErrors().get(0).getCode());
        Assert.assertNotNull(result.getErrors().get(0).getDetails().get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE));
        Assert.assertTrue(result.getErrors().get(0).getDetails()
            .get(ComponentVerifierExtension.VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE) instanceof SalesforceException);
        Assert.assertEquals(400, result.getErrors().get(0).getDetails().get(ComponentVerifierExtension.VerificationError.HttpAttribute.HTTP_CODE));

        // Salesforce Error
        Assert.assertEquals("invalid_client_id", result.getErrors().get(1).getDetail("salesforce_code"));
    }
}
