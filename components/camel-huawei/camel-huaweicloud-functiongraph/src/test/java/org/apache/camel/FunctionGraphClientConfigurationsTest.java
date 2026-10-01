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
package org.apache.camel;

import org.apache.camel.constants.FunctionGraphConstants;
import org.apache.camel.models.ClientConfigurations;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the invoke URN keeps the region even when the client is initialized from the {@code endpoint}
 * parameter. Uses {@link CamelTestSupport} because it resolves a real {@link FunctionGraphEndpoint} from the context.
 */
public class FunctionGraphClientConfigurationsTest extends CamelTestSupport {

    @Test
    public void urnKeepsRegionWhenEndpointIsAlsoConfigured() {
        FunctionGraphEndpoint endpoint = context.getEndpoint(
                "hwcloud-functiongraph:invokeFunction?region=eu-west-101&endpoint=https://function.example.com"
                                                             + "&projectId=proj-1&functionName=fn&functionPackage=pkg"
                                                             + "&accessKey=ak&secretKey=sk&ignoreSslVerification=true",
                FunctionGraphEndpoint.class);

        ClientConfigurations clientConfigurations = new ClientConfigurations(endpoint);

        assertEquals("eu-west-101", clientConfigurations.getRegion(),
                "region must be populated even when the client is initialized from the endpoint");
        // functionName/functionPackage are filled in by the producer at invoke time; here we only assert the
        // region segment is present (previously it was 'urn:fss:null:...' whenever endpoint was configured)
        String urn = FunctionGraphUtils.composeUrn(FunctionGraphConstants.URN_FORMAT, clientConfigurations);
        assertTrue(urn.startsWith("urn:fss:eu-west-101:proj-1:function:"),
                "the invoke URN must carry the region, was: " + urn);
    }
}
