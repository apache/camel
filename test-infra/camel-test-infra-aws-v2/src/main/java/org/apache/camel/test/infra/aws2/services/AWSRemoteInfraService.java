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

package org.apache.camel.test.infra.aws2.services;

import java.util.Properties;

import org.apache.camel.test.infra.aws.common.AWSConfigs;
import org.apache.camel.test.infra.aws.common.services.AWSInfraService;
import org.apache.camel.test.infra.aws2.common.SystemPropertiesAWSCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;

public class AWSRemoteInfraService implements AWSInfraService {

    @Override
    public Properties getConnectionProperties() {
        Properties properties = new Properties();

        AwsCredentials credentials = new SystemPropertiesAWSCredentialsProvider().resolveCredentials();

        properties.put(AWSConfigs.ACCESS_KEY, credentials.accessKeyId());
        properties.put(AWSConfigs.SECRET_KEY, credentials.secretAccessKey());
        properties.put(AWSConfigs.REGION, Region.US_EAST_1.toString());

        return properties;
    }

    @Override
    public String amazonAWSHost() {
        throw new IllegalArgumentException("Not implemented for remote scenario");
    }

    @Override
    public String region() {
        throw new IllegalArgumentException("Not implemented for remote scenario");
    }

    @Override
    public String protocol() {
        throw new IllegalArgumentException("Not implemented for remote scenario");
    }

    @Override
    public String accessKey() {
        throw new IllegalArgumentException("Not implemented for remote scenario");
    }

    @Override
    public String secretKey() {
        throw new IllegalArgumentException("Not implemented for remote scenario");
    }

    @Override
    public void registerProperties() {

    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {

    }
}
