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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.component.aws2.timestream.query.AmazonTimestreamQueryClientMock;
import org.apache.camel.component.aws2.timestream.query.Timestream2QueryEndpoint;
import org.apache.camel.component.aws2.timestream.write.AmazonTimestreamWriteClientMock;
import org.apache.camel.component.aws2.timestream.write.Timestream2WriteEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Timestream2ComponentClientRegistryTest extends CamelTestSupport {

    @Test
    public void createEndpointWithMinimalTimestreamWriteConfiguration() throws Exception {

        AmazonTimestreamWriteClientMock clientMock = new AmazonTimestreamWriteClientMock();
        context.getRegistry().bind("awsTimestreamWriteClient", clientMock);
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        Timestream2WriteEndpoint endpoint
                = (Timestream2WriteEndpoint) component.createEndpoint("aws2-timestream:write:TestDomain");

        assertNotNull(endpoint.getConfiguration().getAwsTimestreamWriteClient());
    }

    @Test
    public void createEndpointWithMinimalTimestreamQueryConfiguration() throws Exception {

        AmazonTimestreamQueryClientMock clientMock = new AmazonTimestreamQueryClientMock();
        context.getRegistry().bind("awsTimestreamQueryClient", clientMock);
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        Timestream2QueryEndpoint endpoint
                = (Timestream2QueryEndpoint) component.createEndpoint("aws2-timestream://query:TestDomain");

        assertNotNull(endpoint.getConfiguration().getAwsTimestreamQueryClient());
    }

    @Test
    public void createEndpointWithMinimalTimestreamWriteClientMisconfiguration() {

        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-timestream://write:TestDomain");
        });
    }

    @Test
    public void createEndpointWithMinimalTimestreamQueryClientMisconfiguration() {

        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        assertThrows(IllegalArgumentException.class, () -> {
            component.createEndpoint("aws2-timestream://query:TestDomain");
        });
    }

    @Test
    public void createWriteQueryEndpointWithAutowired() throws Exception {

        AmazonTimestreamQueryClientMock clientMock = new AmazonTimestreamQueryClientMock();
        context.getRegistry().bind("awsTimestreamQueryClient", clientMock);
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        Timestream2QueryEndpoint endpoint = (Timestream2QueryEndpoint) component
                .createEndpoint("aws2-timestream://query:TestDomain?accessKey=xxx&secretKey=yyy");

        assertSame(clientMock, endpoint.getConfiguration().getAwsTimestreamQueryClient());
    }

}
