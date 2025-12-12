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
package org.apache.camel.component.ibm.cos;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class IBMCOSComponentTest extends CamelTestSupport {

    @Test
    public void testComponentConfiguration() {
        IBMCOSComponent component = context.getComponent("ibm-cos", IBMCOSComponent.class);
        assertNotNull(component);
        assertNotNull(component.getConfiguration());
    }

    @Test
    public void testEndpointCreationRequiresCredentials() {
        // Test that endpoint creation without credentials fails
        ResolveEndpointFailedException exception = assertThrows(ResolveEndpointFailedException.class, () -> {
            context.getEndpoint("ibm-cos:mybucket");
        });
        // The exception message should indicate credentials are required
        String message = exception.getMessage();
        assertTrue(message.contains("cosClient") || message.contains("apiKey"),
                "Exception should mention credentials requirement, but was: " + message);
    }
}
