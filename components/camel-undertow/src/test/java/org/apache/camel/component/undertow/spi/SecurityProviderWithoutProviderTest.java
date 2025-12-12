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
package org.apache.camel.component.undertow.spi;

import java.io.File;
import java.net.URL;

import io.undertow.util.StatusCodes;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test of basic securityProvider scenario, when provider does not accepts security configuration.
 */
public class SecurityProviderWithoutProviderTest extends AbstractSecurityProviderTest {

    @BeforeAll
    public static void createSecurtyProviderConfigurationFile() {
        URL location = MockSecurityProvider.class.getProtectionDomain().getCodeSource().getLocation();
        File file = new File(location.getPath() + "META-INF/services/" + UndertowSecurityProvider.class.getName());
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testSecuredNoProviderd() {
        securityConfiguration.setRoleToAssign("user");

        getMockEndpoint("mock:input").expectedHeaderReceived(Exchange.HTTP_METHOD, "GET");

        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("undertow:http://localhost:{{port}}/foo", null, String.class));
        HttpOperationFailedException he = assertIsInstanceOf(HttpOperationFailedException.class, e.getCause());
        assertEquals(StatusCodes.FORBIDDEN, he.getStatusCode());
    }
}
