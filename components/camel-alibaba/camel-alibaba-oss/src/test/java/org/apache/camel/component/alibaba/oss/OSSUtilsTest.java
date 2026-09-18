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
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OSSUtilsTest {

    @Test
    void createClientWithRegion() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setAccessKey("ak");
        endpoint.setSecretKey("sk");
        endpoint.setRegion("cn-hangzhou");

        try (OSSClient client = OSSUtils.createClient(endpoint)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void createClientWithEndpoint() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setAccessKey("ak");
        endpoint.setSecretKey("sk");
        endpoint.setEndpoint("https://oss-cn-hangzhou.aliyuncs.com");

        try (OSSClient client = OSSUtils.createClient(endpoint)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void createClientMissingAccessKey() {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setSecretKey("sk");
        endpoint.setRegion("cn-hangzhou");

        assertThatThrownBy(() -> OSSUtils.createClient(endpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access key");
    }

    @Test
    void createClientMissingRegionAndEndpoint() {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setAccessKey("ak");
        endpoint.setSecretKey("sk");

        assertThatThrownBy(() -> OSSUtils.createClient(endpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Region/endpoint");
    }

    @Test
    void createClientPrefersEndpointCredentialsOverEmptyServiceKeys() throws Exception {
        OSSEndpoint endpoint = new OSSEndpoint();
        endpoint.setAccessKey("uri-ak");
        endpoint.setSecretKey("uri-sk");
        endpoint.setRegion("cn-hangzhou");
        endpoint.setServiceKeys(new ServiceKeys("", ""));

        try (OSSClient client = OSSUtils.createClient(endpoint)) {
            assertThat(client).isNotNull();
        }
    }
}
