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

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class AWSServiceFactory {
    private AWSServiceFactory() {

    }

    private static class SingletonAWSService extends SingletonService<AWSService> implements AWSService {
        public SingletonAWSService(AWSService service, String name) {
            super(service, name);
        }

        @Override
        public Properties getConnectionProperties() {
            return getService().getConnectionProperties();
        }
    }

    public static <T extends AWSService> SimpleTestServiceBuilder<T> builder() {
        return new SimpleTestServiceBuilder<>("aws");
    }

    private static AWSService createService(Supplier<AWSService> supplier) {
        return builder()
                .addRemoteMapping(AWSRemoteService::new)
                .addLocalMapping(supplier)
                .withPropertyNameFormat("%s-service.instance.type")
                .build();
    }

    public static AWSService createKinesisService() {
        return builder()
                .addRemoteMapping(AWSRemoteService::new)
                .addLocalMapping(AWSKinesisLocalContainerService::new)
                .withPropertyNameFormat("%s-service.kinesis.instance.type")
                .build();
    }

    public static AWSService createSQSService() {
        return createService(AWSSQSLocalContainerService::new);
    }

    public static AWSService createS3Service() {
        return createService(AWSS3LocalContainerService::new);
    }

    public static AWSService createSNSService() {
        return createService(AWSSNSLocalContainerService::new);
    }

    public static AWSService createConfigService() {
        return createService(AWSConfigLocalContainerService::new);
    }

    public static AWSService createCloudWatchService() {
        return createService(AWSCloudWatchLocalContainerService::new);
    }

    public static AWSService createEC2Service() {
        return createService(AWSEC2LocalContainerService::new);
    }

    public static AWSService createEventBridgeService() {
        return createService(AWSEventBridgeLocalContainerService::new);
    }

    public static AWSService createIAMService() {
        return createService(AWSIAMLocalContainerService::new);
    }

    public static AWSService createKMSService() {
        return createService(AWSKMSLocalContainerService::new);
    }

    public static AWSService createLambdaService() {
        return createService(AWSLambdaLocalContainerService::new);
    }

    public static AWSService createSTSService() {
        return createService(AWSSTSLocalContainerService::new);
    }

    public static AWSService createDynamodbService() {
        return createService(AWSDynamodbLocalContainerService::new);
    }

    public static AWSService createSecretsManagerService() {
        return createService(AWSSecretsManagerLocalContainerService::new);
    }

    public static AWSService createSingletonDynamoDBService() {
        return SingletonServiceHolder.getInstance(new AWSDynamodbLocalContainerService(), "dynamoDB");
    }

    public static AWSService createSingletonS3Service() {
        return SingletonServiceHolder.getInstance(new AWSS3LocalContainerService(), "s3");
    }

    public static AWSService createSingletonSQSService() {
        return SingletonServiceHolder.getInstance(new AWSSQSLocalContainerService(), "sqs");
    }

    public static AWSService createSingletonEventBridgeService() {
        return SingletonServiceHolder.getInstance(new AWSEventBridgeLocalContainerService(), "eventBridge");
    }

    public static AWSService createSingletonKinesisService() {
        return SingletonServiceHolder.getInstance(new AWSKinesisLocalContainerService(), "kinesis");
    }

    private static class SingletonServiceHolder {
        private static final Map<String, AWSService> INSTANCES_HOLDER = new ConcurrentHashMap<>();

        public synchronized static AWSService getInstance(AWSService service, String name) {
            if (INSTANCES_HOLDER.get(name) == null) {
                SimpleTestServiceBuilder<AWSService> instance = builder();
                instance.addLocalMapping(() -> new SingletonAWSService(service, name))
                        .addRemoteMapping(AWSRemoteService::new)
                        .build();

                INSTANCES_HOLDER.put(name, instance.build());
            }

            return INSTANCES_HOLDER.get(name);
        }
    }
}
