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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class SalesforceComponentVerifierExtensionTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected ComponentVerifierExtension getExtension() {
        Component component = context().getComponent("salesforce");
        ComponentVerifierExtension verifier = component.getExtension(ComponentVerifierExtension.class).orElseThrow(IllegalStateException::new);

        return verifier;
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

        ComponentVerifierExtension.Result result = getExtension().verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testRefreshTokenParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", "clientId");
        parameters.put("clientSecret", "clientSecret");
        parameters.put("refreshToken", "refreshToken");

        ComponentVerifierExtension.Result result = getExtension().verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.OK, result.getStatus());
    }

    @Test
    public void testWrongParameters() {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", "clientId");
        parameters.put("clientSecret", "clientSecret");
        parameters.put("password", "password");

        ComponentVerifierExtension.Result result = getExtension().verify(ComponentVerifierExtension.Scope.PARAMETERS, parameters);

        Assert.assertEquals(ComponentVerifierExtension.Result.Status.ERROR, result.getStatus());
        Assert.assertEquals(3, result.getErrors().size());

        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION, result.getErrors().get(0).getCode());
        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION, result.getErrors().get(1).getCode());
        Assert.assertEquals(ComponentVerifierExtension.VerificationError.StandardCode.ILLEGAL_PARAMETER_GROUP_COMBINATION, result.getErrors().get(2).getCode());
    }
}
