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

import java.util.function.Supplier;

import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AWSServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AWSServiceFactory.class);

    private AWSServiceFactory() {

    }

    private static String getInstanceTypeName(String awsInstanceType) {
        return awsInstanceType == null ? "default" : awsInstanceType;
    }

    private static <
            T extends AWSLocalContainerService> AWSService createService(String property, String name, Supplier<T> supplier) {
        String awsInstanceType = System.getProperty(property);
        LOG.info("Creating a {} {} instance", name, getInstanceTypeName(awsInstanceType));

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            return supplier.get();
        }

        if (awsInstanceType.equals("remote")) {
            return new AWSRemoteService();
        }

        LOG.error("Invalid AWS instance type: {}. Must be either 'remote' or 'local-aws-container'",
                awsInstanceType);
        throw new UnsupportedOperationException("Invalid AWS instance type");
    }

    private static <T extends AWSLocalContainerService> AWSService createService(String name, Supplier<T> supplier) {
        return createService("aws-service.instance.type", name, supplier);
    }

    public static AWSService createKinesisService() {
        return createService("aws-service.kinesis.instance.type", "AWS Kinesis",
                AWSKinesisLocalContainerService::new);
    }

    public static AWSService createSQSService() {
        return createService("AWS SQS", AWSSQSLocalContainerService::new);
    }

    public static AWSService createS3Service() {
        return createService("AWS S3", AWSS3LocalContainerService::new);
    }

    public static AWSService createSNSService() {
        return createService("AWS SNS", AWSSNSLocalContainerService::new);
    }

    public static AWSService createCloudWatchService() {
        return createService("AWS Cloud Watch", AWSCloudWatchLocalContainerService::new);
    }

    public static AWSService createEC2Service() {
        return createService("AWS EC2", AWSEC2LocalContainerService::new);
    }

    public static AWSService createEventBridgeService() {
        return createService("AWS EventBridge", AWSEventBridgeLocalContainerService::new);
    }

    public static AWSService createIAMService() {
        return createService("AWS IAM", AWSIAMLocalContainerService::new);
    }

    public static AWSService createKMSService() {
        return createService("AWS KMS", AWSKMSLocalContainerService::new);
    }

    public static AWSService createLambdaService() {
        return createService("AWS Lambda", AWSLambdaLocalContainerService::new);
    }

    public static AWSService createSTSService() {
        return createService("AWS STS", AWSSTSLocalContainerService::new);
    }
}
