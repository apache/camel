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
package org.apache.camel.component.aws2.cw;

import java.time.Instant;

import org.apache.camel.BindToRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CwComponentRegistryClientTest extends CamelTestSupport {

    @BindToRegistry("now")
    public static final Instant NOW = Instant.now();

    @Test
    public void createEndpointWithAllOptions() throws Exception {
        CloudWatchClient cloudWatchClient = new CloudWatchClientMock();
        context.getRegistry().bind("amazonCwClient", cloudWatchClient);
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        Cw2Endpoint endpoint = (Cw2Endpoint) component
                .createEndpoint("aws2-cw://camel.apache.org/test?name=testMetric&value=2&unit=Count&timestamp=#now");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertEquals("testMetric", endpoint.getConfiguration().getName());
        assertEquals(Double.valueOf(2), endpoint.getConfiguration().getValue());
        assertEquals("Count", endpoint.getConfiguration().getUnit());
        assertEquals(NOW, endpoint.getConfiguration().getTimestamp());
    }

    @Test
    public void createEndpointWithMinimalS3ClientMisconfiguration() {
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-cw://camel.apache.org/test");
        });
    }

    @Test
    public void createEndpointWithAutowire() throws Exception {
        CloudWatchClient cloudWatchClient = new CloudWatchClientMock();
        context.getRegistry().bind("amazonCwClient", cloudWatchClient);
        Cw2Component component = context.getComponent("aws2-cw", Cw2Component.class);
        Cw2Endpoint endpoint
                = (Cw2Endpoint) component.createEndpoint("aws2-cw://camel.apache.org/test?accessKey=xxx&secretKey=yyy");

        assertEquals("camel.apache.org/test", endpoint.getConfiguration().getNamespace());
        assertSame(cloudWatchClient, endpoint.getConfiguration().getAmazonCwClient());
    }
}
