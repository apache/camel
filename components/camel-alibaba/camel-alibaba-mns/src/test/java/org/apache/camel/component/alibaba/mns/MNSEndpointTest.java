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
package org.apache.camel.component.alibaba.mns;

import com.aliyun.mns.client.MNSClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MNSEndpointTest {

    @Test
    void doStopSkipsCloseForAutowiredClient() throws Exception {
        MNSEndpoint endpoint = new MNSEndpoint();
        MNSClient client = mock(MNSClient.class);
        endpoint.setMnsClient(client);

        endpoint.doStop();

        verify(client, never()).close();
    }

    @Test
    void doStopClosesOwnedClient() throws Exception {
        MNSEndpoint endpoint = new MNSEndpoint();
        MNSClient client = mock(MNSClient.class);

        var clientField = MNSEndpoint.class.getDeclaredField("mnsClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = MNSEndpoint.class.getDeclaredField("autowiredMnsClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        verify(client).close();
        assertThat(endpoint.getMnsClient()).isNull();
    }
}
