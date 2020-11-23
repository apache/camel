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

package org.apache.camel.test.infra.aws2.clients;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Supplier;

import org.apache.camel.test.infra.aws.common.AWSConfigs;
import org.apache.camel.test.infra.aws2.common.SystemPropertiesAWSCredentialsProvider;
import org.apache.camel.test.infra.aws2.common.TestAWSCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sts.StsClient;

public final class AWSSDKClientUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AWSSDKClientUtils.class);

    private AWSSDKClientUtils() {

    }

    private static URI getEndpoint() {
        String amazonHost = System.getProperty(AWSConfigs.AMAZON_AWS_HOST);

        if (amazonHost == null || amazonHost.isEmpty()) {
            return null;
        }

        try {
            return new URI(String.format("http://%s", amazonHost));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid endpoint");
        }
    }

    private static boolean isLocalContainer(String awsInstanceType) {
        return awsInstanceType == null || awsInstanceType.equals("local-aws-container");
    }

    /**
     * Generic AWS v2 client builder
     * 
     * @param  property              the system property used to figure out if it's local service or not
     * @param  name                  the service name
     * @param  clientBuilderSupplier A supplier type for creating the builder class
     * @param  yClass                The client type to be generated
     * @param  <T>
     * @param  <Y>
     * @return                       A new client of the given type
     */
    private static <T extends AwsClientBuilder, Y extends SdkClient> Y newClient(
            String property, String name,
            Supplier<T> clientBuilderSupplier, Class<Y> yClass) {
        T clientBuilder = clientBuilderSupplier.get();

        LOG.debug("Creating a new AWS v2 {} client", name);

        String awsInstanceType = System.getProperty(property);

        clientBuilder.region(Region.US_EAST_1);

        URI endpoint = getEndpoint();

        if (isLocalContainer(awsInstanceType) || endpoint != null) {
            clientBuilder.endpointOverride(endpoint);
        }

        if (isLocalContainer(awsInstanceType)) {
            clientBuilder.credentialsProvider(TestAWSCredentialsProvider.CONTAINER_LOCAL_DEFAULT_PROVIDER);

        } else {
            clientBuilder.credentialsProvider(new SystemPropertiesAWSCredentialsProvider());
        }

        Object o = clientBuilder.build();
        if (yClass.isInstance(o)) {
            return (Y) o;
        }

        throw new UnsupportedOperationException("Invalid class type for AWS client");
    }

    /**
     * Generic AWS v2 client builder
     * 
     * @param  name                  the service name
     * @param  clientBuilderSupplier A supplier type for creating the builder class
     * @param  yClass                The client type to be generated
     * @param  <T>
     * @param  <Y>
     * @return                       A new client of the given type
     */
    private static <T extends AwsClientBuilder, Y extends SdkClient> Y newClient(
            String name,
            Supplier<T> clientBuilderSupplier, Class<Y> yClass) {
        return newClient("aws-service.instance.type", name, clientBuilderSupplier, yClass);
    }

    public static KinesisClient newKinesisClient() {
        return newClient("aws-service.kinesis.instance.type", "Kinesis", KinesisClient::builder,
                KinesisClient.class);
    }

    public static SqsClient newSQSClient() {
        return newClient("SQS", SqsClient::builder, SqsClient.class);
    }

    public static S3Client newS3Client() {
        return newClient("S3", S3Client::builder, S3Client.class);
    }

    public static SnsClient newSNSClient() {
        return newClient("SNS", SnsClient::builder, SnsClient.class);
    }

    public static CloudWatchClient newCloudWatchClient() {
        return newClient("Cloud Watch", CloudWatchClient::builder, CloudWatchClient.class);
    }

    public static Ec2Client newEC2Client() {
        return newClient("EC2", Ec2Client::builder, Ec2Client.class);
    }

    public static EventBridgeClient newEventBridgeClient() {
        return newClient("EventBridge", EventBridgeClient::builder, EventBridgeClient.class);
    }

    public static IamClient newIAMClient() {
        return newClient("EventBridge", IamClient::builder, IamClient.class);
    }

    public static KmsClient newKMSClient() {
        return newClient("KMS", KmsClient::builder, KmsClient.class);
    }

    public static LambdaClient newLambdaClient() {
        return newClient("Lambda", LambdaClient::builder, LambdaClient.class);
    }

    public static StsClient newSTSClient() {
        return newClient("STS", StsClient::builder, StsClient.class);
    }
}
