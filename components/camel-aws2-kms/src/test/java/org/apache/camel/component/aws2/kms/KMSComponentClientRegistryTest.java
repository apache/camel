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
package org.apache.camel.component.aws2.kms;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KMSComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalKMSClientConfiguration() throws Exception {

        AmazonKMSClientMock clientMock = new AmazonKMSClientMock();
        context.getRegistry().bind("amazonKmsClient", clientMock);
        KMS2Component component = context.getComponent("aws2-kms", KMS2Component.class);
        KMS2Endpoint endpoint = (KMS2Endpoint)component.createEndpoint("aws2-kms://TestDomain");

        assertNotNull(endpoint.getConfiguration().getKmsClient());
    }

    @Test
    public void createEndpointWithMinimalKMSClientMisconfiguration() throws Exception {

        KMS2Component component = context.getComponent("aws2-kms", KMS2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-kms://TestDomain");
        });
    }
}
