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
package org.apache.camel.component.aws2.s3;

import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public class AWS2S3ConsumerHealthCheck extends AbstractHealthCheck {

    private final AWS2S3Consumer aws2S3Consumer;
    private final String routeId;

    public AWS2S3ConsumerHealthCheck(AWS2S3Consumer aws2S3Consumer, String routeId) {
        super("camel", "aws2-s3-consumer-" + routeId);
        this.aws2S3Consumer = aws2S3Consumer;
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
            AWS2S3Configuration configuration = aws2S3Consumer.getConfiguration();
            if (!S3Client.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                builder.message("The service is not supported in this region");
                builder.down();
                return;
            }
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
            S3ClientBuilder clientBuilder = S3Client.builder();
            S3Client client = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred))
                    .region(Region.of(configuration.getRegion())).build();
            client.listBuckets();
        } catch (SdkClientException e) {
            builder.message(e.getMessage());
            builder.error(e);
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
