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
package org.apache.camel.component.aws2.rekognition;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Rekognition2ComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalRekognitionClientConfiguration() throws Exception {

        AmazonRekognitionClientMock clientMock = new AmazonRekognitionClientMock();
        context.getRegistry().bind("amazonRekognitionClient", clientMock);
        Rekognition2Component component = context.getComponent("aws2-rekognition", Rekognition2Component.class);
        Rekognition2Endpoint endpoint = (Rekognition2Endpoint) component.createEndpoint("aws2-rekognition://TestDomain");

        assertNotNull(endpoint.getConfiguration().getAwsRekognitionClient());
    }

    @Test
    public void createEndpointWithMinimalRekognitionClientMisconfiguration() {

        Rekognition2Component component = context.getComponent("aws2-rekognition", Rekognition2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-rekognition://TestDomain");
        });
    }

    @Test
    public void createEndpointWithAutowired() throws Exception {

        AmazonRekognitionClientMock clientMock = new AmazonRekognitionClientMock();
        context.getRegistry().bind("awsRekognitionClient", clientMock);
        Rekognition2Component component = context.getComponent("aws2-rekognition", Rekognition2Component.class);
        Rekognition2Endpoint endpoint = (Rekognition2Endpoint) component
                .createEndpoint("aws2-rekognition://TestDomain?accessKey=xxx&secretKey=yyy");

        assertSame(clientMock, endpoint.getConfiguration().getAwsRekognitionClient());
    }
}
