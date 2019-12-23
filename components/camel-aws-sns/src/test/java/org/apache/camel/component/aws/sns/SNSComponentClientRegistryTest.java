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
package org.apache.camel.component.aws.sns;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SNSComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalSNSClientConfiguration() throws Exception {

        AmazonSNSClientMock awsSNSClient = new AmazonSNSClientMock();
        context.getRegistry().bind("awsSNSClient", awsSNSClient);
        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic");

        assertNotNull(endpoint.getConfiguration().getAmazonSNSClient());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void createEndpointWithMinimalSNSClientMisconfiguration() throws Exception {

        SnsComponent component = context.getComponent("aws-sns", SnsComponent.class);
        SnsEndpoint endpoint = (SnsEndpoint) component.createEndpoint("aws-sns://MyTopic");
    }
}
