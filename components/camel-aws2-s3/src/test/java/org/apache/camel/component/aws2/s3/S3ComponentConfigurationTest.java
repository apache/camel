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
package org.apache.camel.component.aws2.s3;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3ComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalConfiguration() throws Exception {
        AWS2S3Component component = context.getComponent("aws2-s3", AWS2S3Component.class);
        AWS2S3Endpoint endpoint = (AWS2S3Endpoint)component
            .createEndpoint("aws2-s3://TestDomain?accessKey=xxx&secretKey=yyy&region=us-west-1&overrideEndpoint=true&uriEndpointOverride=http://localhost:4572");
        assertTrue(endpoint.getConfiguration().isOverrideEndpoint());
        assertEquals(endpoint.getConfiguration().getUriEndpointOverride(), "http://localhost:4572");
    }
}
