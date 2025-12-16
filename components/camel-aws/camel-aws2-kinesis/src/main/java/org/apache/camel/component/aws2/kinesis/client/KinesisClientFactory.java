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
package org.apache.camel.component.aws2.kinesis.client;

import org.apache.camel.component.aws.common.AwsClientBuilderUtil;
import org.apache.camel.component.aws2.kinesis.Kinesis2Configuration;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;

/**
 * Factory class to create AWS Kinesis clients using common configuration.
 */
public final class KinesisClientFactory {

    private KinesisClientFactory() {
    }

    /**
     * Create a Kinesis sync client based on configuration.
     *
     * @param  configuration The Kinesis configuration
     * @return               Configured KinesisClient
     */
    public static KinesisClient getKinesisClient(Kinesis2Configuration configuration) {
        return AwsClientBuilderUtil.buildClient(
                configuration,
                KinesisClient::builder);
    }

    /**
     * Create a Kinesis async client based on configuration.
     *
     * @param  configuration The Kinesis configuration
     * @return               Configured KinesisAsyncClient
     */
    public static KinesisAsyncClient getKinesisAsyncClient(Kinesis2Configuration configuration) {
        return AwsClientBuilderUtil.buildAsyncClient(
                configuration,
                KinesisAsyncClient::builder);
    }
}
