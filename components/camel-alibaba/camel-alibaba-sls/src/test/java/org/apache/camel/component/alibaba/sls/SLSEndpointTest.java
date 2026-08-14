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
package org.apache.camel.component.alibaba.sls;

import com.aliyun.sls20201230.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SLSEndpointTest {

    @Test
    void doStopKeepsAutowiredClient() throws Exception {
        SLSEndpoint endpoint = new SLSEndpoint();
        Client client = mock(Client.class);
        endpoint.setSlsClient(client);

        endpoint.doStop();

        assertThat(endpoint.getSlsClient()).isSameAs(client);
    }

    @Test
    void doStopClearsOwnedClient() throws Exception {
        SLSEndpoint endpoint = new SLSEndpoint();
        Client client = mock(Client.class);

        var clientField = SLSEndpoint.class.getDeclaredField("slsClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = SLSEndpoint.class.getDeclaredField("autowiredSlsClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        assertThat(endpoint.getSlsClient()).isNull();
    }
}
