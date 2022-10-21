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
package org.apache.camel.component.aws2.kinesis;

import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.KinesisClientBuilder;

public class Kinesis2ConsumerHealthCheck extends AbstractHealthCheck {

    private final Kinesis2Consumer kinesis2Consumer;
    private final String routeId;

    public Kinesis2ConsumerHealthCheck(Kinesis2Consumer kinesis2Consumer, String routeId) {
        super("camel", "aws2-kinesis-consumer-" + routeId);
        this.kinesis2Consumer = kinesis2Consumer;
        this.routeId = routeId;
    }

    @Override
    public boolean isLiveness() {
        // this health check is only readiness
        return false;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {

        try {
            Kinesis2Configuration configuration = kinesis2Consumer.getConfiguration();
            if (!KinesisClient.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                builder.message("The service is not supported in this region");
                builder.down();
                return;
            }
            KinesisClient client;
            if (!configuration.isUseDefaultCredentialsProvider()) {
                AwsBasicCredentials cred
                        = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
                KinesisClientBuilder clientBuilder = KinesisClient.builder();
                client = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred))
                        .region(Region.of(configuration.getRegion())).build();
            } else if (ObjectHelper.isNotEmpty(configuration.getAmazonKinesisClient())) {
                client = configuration.getAmazonKinesisClient();
            } else {
                KinesisClientBuilder clientBuilder = KinesisClient.builder();
                client = clientBuilder.region(Region.of(configuration.getRegion())).build();
            }
            client.listStreams();
        } catch (AwsServiceException e) {
            builder.message(e.getMessage());
            builder.error(e);
            if (ObjectHelper.isNotEmpty(e.statusCode())) {
                builder.detail(SERVICE_STATUS_CODE, e.statusCode());
            }
            if (ObjectHelper.isNotEmpty(e.awsErrorDetails().errorCode())) {
                builder.detail(SERVICE_ERROR_CODE, e.awsErrorDetails().errorCode());
            }
            builder.down();
            return;

        } catch (Exception e) {
            builder.error(e);
            builder.down();
            return;
        }

        builder.up();

    }

}
