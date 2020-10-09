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

import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

public final class AWSServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AWSServiceFactory.class);

    private AWSServiceFactory() {

    }

    private static String getInstanceTypeName(String awsInstanceType) {
        return awsInstanceType == null ? "default" : awsInstanceType;
    }

    public static AWSService<KinesisClient> createKinesisService() {
        String awsInstanceType = System.getProperty("aws-service.kinesis.instance.type");
        LOG.info("Creating a {} AWS kinesis instance", getInstanceTypeName(awsInstanceType));

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {

            System.setProperty(SdkSystemSetting.CBOR_ENABLED.property(), "false");

            return new AWSKinesisLocalContainerService();
        }

        if (awsInstanceType.equals("remote")) {
            return new AWSRemoteService<>(AWSSDKClientUtils::newKinesisClient);
        }

        LOG.error("Invalid AWS instance type: {}. Must be either 'remote' or 'local-aws-container'",
                awsInstanceType);
        throw new UnsupportedOperationException("Invalid AWS instance type");
    }

    public static AWSService<SqsClient> createSQSService() {
        String awsInstanceType = System.getProperty("aws-service.instance.type");
        LOG.info("Creating a {} AWS SQS instance", getInstanceTypeName(awsInstanceType));

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            return new AWSSQSLocalContainerService();
        }

        if (awsInstanceType.equals("remote")) {
            return new AWSRemoteService<>(AWSSDKClientUtils::newSQSClient);
        }

        LOG.error("Invalid AWS instance type: {}. Must be either 'remote' or 'local-aws-container'",
                awsInstanceType);
        throw new UnsupportedOperationException("Invalid AWS instance type");
    }

    public static AWSService<S3Client> createS3Service() {
        String awsInstanceType = System.getProperty("aws-service.instance.type");
        LOG.info("Creating a {} AWS S3 instance", awsInstanceType);

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            return new AWSS3LocalContainerService();
        }

        if (awsInstanceType.equals("remote")) {
            return new AWSRemoteService<>(AWSSDKClientUtils::newS3Client);
        }

        LOG.error("Invalid AWS instance type: {}. Must be either 'remote' or 'local-aws-container'",
                awsInstanceType);
        throw new UnsupportedOperationException("Invalid AWS instance type");
    }
}
