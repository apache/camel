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
package org.apache.camel.component.alibaba.oss;

import com.aliyun.sdk.service.oss2.OSSClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OSSEndpointTest {

    @Test
    void initClientReturnsCachedInstance() {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setAccessKey("ak");
        endpoint.setSecretKey("sk");
        endpoint.setRegion("cn-hangzhou");

        OSSClient first = endpoint.initClient();
        OSSClient second = endpoint.initClient();

        assertThat(first).isSameAs(second);
    }

    @Test
    void doStopSkipsCloseForAutowiredClient() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        OSSClient client = mock(OSSClient.class);
        endpoint.setOssClient(client);

        endpoint.doStop();

        verify(client, never()).close();
    }

    @Test
    void doStopClosesOwnedClient() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        OSSClient client = mock(OSSClient.class);

        var clientField = OSSEndpoint.class.getDeclaredField("ossClient");
        clientField.setAccessible(true);
        clientField.set(endpoint, client);

        var autowiredField = OSSEndpoint.class.getDeclaredField("autowiredOssClient");
        autowiredField.setAccessible(true);
        autowiredField.setBoolean(endpoint, false);

        endpoint.doStop();

        verify(client).close();
        assertThat(endpoint.getOssClient()).isNull();
    }
}
