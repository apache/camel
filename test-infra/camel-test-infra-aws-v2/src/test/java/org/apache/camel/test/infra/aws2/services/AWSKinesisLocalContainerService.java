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

import org.apache.camel.test.infra.aws2.common.TestAWSCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

public class AWSKinesisLocalContainerService extends AWSLocalContainerService<KinesisClient> {
    private static final Logger LOG = LoggerFactory.getLogger(AWSKinesisLocalContainerService.class);

    public AWSKinesisLocalContainerService() {
        super(Service.KINESIS);

        LOG.info("Initializing the local AWS services");
        getContainer().start();
    }

    @Override
    public KinesisClient getClient() {
        Region region = Region.US_EAST_1;
        KinesisClient client = KinesisClient.builder()
                .region(region)
                .credentialsProvider(TestAWSCredentialsProvider.CONTAINER_LOCAL_DEFAULT_PROVIDER)
                .endpointOverride(getServiceEndpoint())
                .build();

        return client;
    }
}
