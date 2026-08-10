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
package org.apache.camel.component.alibaba.common;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import org.apache.camel.util.ObjectHelper;

public final class AlibabaClientBuilderUtil {

    private AlibabaClientBuilderUtil() {
    }

    /**
     * Create an OSS client using static credentials.
     *
     * @param  accessKey access key id
     * @param  secretKey secret access key
     * @param  region    OSS region
     * @param  endpoint  optional custom endpoint
     * @return           configured OSS client
     */
    public static OSSClient createOssClient(String accessKey, String secretKey, String region, String endpoint) {
        if (ObjectHelper.isEmpty(accessKey)) {
            throw new IllegalArgumentException("Authentication parameter 'access key (AK)' not found");
        }
        if (ObjectHelper.isEmpty(secretKey)) {
            throw new IllegalArgumentException("Authentication parameter 'secret key (SK)' not found");
        }
        if (ObjectHelper.isEmpty(region) && ObjectHelper.isEmpty(endpoint)) {
            throw new IllegalArgumentException("Region/endpoint not found");
        }

        OSSClientBuilder clientBuilder = OSSClient.newBuilder()
                .credentialsProvider(new StaticCredentialsProvider(accessKey, secretKey));

        if (ObjectHelper.isNotEmpty(region)) {
            clientBuilder.region(region);
        }
        if (ObjectHelper.isNotEmpty(endpoint)) {
            clientBuilder.endpoint(endpoint);
        }

        return clientBuilder.build();
    }
}
