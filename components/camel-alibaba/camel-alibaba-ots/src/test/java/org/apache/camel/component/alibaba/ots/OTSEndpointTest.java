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
package org.apache.camel.component.alibaba.ots;

import com.alicloud.openservices.tablestore.SyncClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OTSEndpointTest {

    @Test
    void initClientReturnsCachedInstance() throws Exception {
        OTSEndpoint endpoint = new OTSEndpoint();
        endpoint.setAccessKey("ak");
        endpoint.setSecretKey("sk");
        endpoint.setEndpoint("https://test-instance.cn-hangzhou.ots.aliyuncs.com");
        endpoint.setInstanceName("test-instance");

        SyncClient first = endpoint.initClient();
        SyncClient second = endpoint.initClient();

        assertThat(first).isSameAs(second);
        first.shutdown();
    }

    @Test
    void doStopSkipsShutdownForAutowiredClient() throws Exception {
        OTSEndpoint endpoint = new OTSEndpoint();
        SyncClient client = mock(SyncClient.class);
        endpoint.setOtsClient(client);

        endpoint.doStop();

        verify(client, never()).shutdown();
    }

    @Test
    void doStopShutsDownOwnedClient() throws Exception {
        OTSEndpoint endpoint = new OTSEndpoint();
        SyncClient client = mock(SyncClient.class);

        var clientField = OTSEndpoint.class.getDeclaredField("otsClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = OTSEndpoint.class.getDeclaredField("autowiredOtsClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        verify(client).shutdown();
        assertThat(endpoint.getOtsClient()).isNull();
    }
}
