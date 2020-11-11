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

package org.apache.camel.test.infra.aws.clients;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.apache.camel.test.infra.aws.common.AWSConfigs;
import org.apache.camel.test.infra.aws.common.SystemPropertiesAWSCredentialsProvider;
import org.apache.camel.test.infra.aws.common.TestAWSCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AWSClientUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AWSClientUtils.class);

    private AWSClientUtils() {
    }

    private static String getRegion() {
        String regionStr = System.getProperty(AWSConfigs.REGION);
        String region;

        if (regionStr != null && !regionStr.isEmpty()) {
            region = Regions.valueOf(regionStr).getName();
        } else {
            region = Regions.US_EAST_1.getName();
        }

        return region;
    }

    public static AmazonSNS newSNSClient() {
        LOG.debug("Creating a custom SNS client for running a AWS SNS test");
        AmazonSNSClientBuilder clientBuilder = AmazonSNSClientBuilder
                .standard();

        String awsInstanceType = System.getProperty("aws-service.instance.type");
        String region = getRegion();

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            String amazonHost = System.getProperty(AWSConfigs.AMAZON_AWS_HOST);

            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProtocol(Protocol.HTTP);

            clientBuilder
                    .withClientConfiguration(clientConfiguration)
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(amazonHost, region))
                    .withCredentials(new TestAWSCredentialsProvider("accesskey", "secretkey"));
        } else {
            clientBuilder
                    .withRegion(region)
                    .withCredentials(new SystemPropertiesAWSCredentialsProvider());
        }

        return clientBuilder.build();
    }

    public static AmazonSQS newSQSClient() {
        LOG.debug("Creating a custom SQS client");
        AmazonSQSClientBuilder clientBuilder = AmazonSQSClientBuilder
                .standard();

        String awsInstanceType = System.getProperty("aws-service.instance.type");
        String region = getRegion();
        LOG.debug("Using amazon region: {}", region);

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            String amazonHost = System.getProperty(AWSConfigs.AMAZON_AWS_HOST);
            LOG.debug("Using amazon host: {}", amazonHost);

            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProtocol(Protocol.HTTP);

            clientBuilder
                    .withClientConfiguration(clientConfiguration)
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(amazonHost, region))
                    .withCredentials(new TestAWSCredentialsProvider("accesskey", "secretkey"));
        } else {
            clientBuilder
                    .withRegion(region)
                    .withCredentials(new SystemPropertiesAWSCredentialsProvider());
        }

        return clientBuilder.build();
    }

    public static AmazonS3 newS3Client() {
        LOG.debug("Creating a new S3 client");
        AmazonS3ClientBuilder clientBuilder = AmazonS3ClientBuilder.standard();

        String awsInstanceType = System.getProperty("aws-service.instance.type");
        String region = getRegion();

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            String amazonHost = System.getProperty(AWSConfigs.AMAZON_AWS_HOST);
            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProtocol(Protocol.HTTP);

            clientBuilder
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(amazonHost, region))
                    .withClientConfiguration(clientConfiguration)
                    .withCredentials(new TestAWSCredentialsProvider("accesskey", "secretkey"));
        } else {
            clientBuilder
                    .withRegion(region)
                    .withCredentials(new SystemPropertiesAWSCredentialsProvider());
        }

        clientBuilder
                .withPathStyleAccessEnabled(true);

        return clientBuilder.build();
    }

    public static AmazonKinesis newKinesisClient() {
        LOG.debug("Creating a new AWS Kinesis client");
        AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard();

        String awsInstanceType = System.getProperty("aws-service.kinesis.instance.type");
        String region = getRegion();

        if (awsInstanceType == null || awsInstanceType.equals("local-aws-container")) {
            String amazonHost = System.getProperty(AWSConfigs.AMAZON_AWS_HOST);

            LOG.debug("Creating a new AWS Kinesis client to access {}", amazonHost);

            ClientConfiguration clientConfiguration = new ClientConfiguration();
            clientConfiguration.setProtocol(Protocol.HTTP);

            clientBuilder
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(amazonHost, region))
                    .withClientConfiguration(clientConfiguration)
                    .withCredentials(new TestAWSCredentialsProvider("accesskey", "secretkey"));
        } else {
            clientBuilder
                    .withRegion(region)
                    .withCredentials(new SystemPropertiesAWSCredentialsProvider());
        }

        return clientBuilder.build();
    }
}
