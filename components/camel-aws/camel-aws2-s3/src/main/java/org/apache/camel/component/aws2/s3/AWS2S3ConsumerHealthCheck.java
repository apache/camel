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
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

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

        AWS2S3Configuration configuration = aws2S3Consumer.getConfiguration();
        try {

            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                if (!S3Client.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                    builder.message("The service is not supported in this region");
                    builder.down();
                    return;
                }
            }
            S3Client client = aws2S3Consumer.getAmazonS3Client();
            client.headBucket(HeadBucketRequest.builder().bucket(configuration.getBucketName()).build());
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
