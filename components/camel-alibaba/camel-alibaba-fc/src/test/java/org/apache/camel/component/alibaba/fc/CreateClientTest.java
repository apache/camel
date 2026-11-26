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
package org.apache.camel.component.alibaba.fc;

import com.aliyun.fc_open20210406.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateClientTest {

    @Test
    @SuppressWarnings("deprecation")
    void aRegionWithoutAnEndpointIsRejected() {
        FCEndpoint endpoint = new FCEndpoint();
        endpoint.setRegion("cn-hangzhou");
        endpoint.setAccessKey("accessKey");
        endpoint.setSecretKey("secretKey");

        assertThatThrownBy(() -> FCUtils.createClient(endpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Endpoint is required");
    }

    @Test
    void anEndpointWithoutARegionCreatesAClient() throws Exception {
        FCEndpoint endpoint = new FCEndpoint();
        endpoint.setEndpoint("12345.cn-hangzhou.fc.aliyuncs.com");
        endpoint.setAccessKey("accessKey");
        endpoint.setSecretKey("secretKey");

        Client client = FCUtils.createClient(endpoint);

        assertThat(client._endpoint).isEqualTo("12345.cn-hangzhou.fc.aliyuncs.com");
        assertThat(client._regionId).isNull();
    }
}
