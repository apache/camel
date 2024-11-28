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

import org.apache.camel.test.infra.common.services.SimpleTestServiceBuilder;
import org.apache.camel.test.infra.common.services.SingletonService;

public final class AWSServiceFactory {
    private AWSServiceFactory() {

    }

    private static class SingletonAWSService extends SingletonService<AWSTestService> implements AWSTestService {
        public SingletonAWSService(AWSTestService service, String name) {
            super(service, name);
        }

        @Override
        public Properties getConnectionProperties() {
            return getService().getConnectionProperties();
        }
    }

    public static <T extends AWSTestService> SimpleTestServiceBuilder<T> builder() {
        return new SimpleTestServiceBuilder<>("aws");
    }

    private static AWSTestService createService(Supplier<AWSTestService> supplier) {
        return builder()
                .addRemoteMapping(AWSTestServices.AWSRemoteTestService::new)
                .addLocalMapping(supplier)
                .withPropertyNameFormat("%s-service.instance.type")
                .build();
    }

    public static AWSTestService createKinesisService() {
        return builder()
                .addRemoteMapping(AWSTestServices.AWSRemoteTestService::new)
                .addLocalMapping(AWSTestServices.AWSKinesisLocalContainerTestService::new)
                .withPropertyNameFormat("%s-service.kinesis.instance.type")
                .build();
    }

    public static AWSTestService createSQSService() {
        return createService(AWSTestServices.AWSSQSLocalContainerTestService::new);
    }

    public static AWSTestService createS3Service() {
        return createService(AWSTestServices.AWSS3LocalContainerTestService::new);
    }

    public static AWSTestService createSNSService() {
        return createService(AWSTestServices.AWSSNSLocalContainerTestService::new);
    }

    public static AWSTestService createConfigService() {
        return createService(AWSTestServices.AWSConfigLocalContainerTestService::new);
    }

    public static AWSTestService createCloudWatchService() {
        return createService(AWSTestServices.AWSCloudWatchLocalContainerTestService::new);
    }

    public static AWSTestService createEC2Service() {
        return createService(AWSTestServices.AWSEC2LocalContainerTestService::new);
    }

    public static AWSTestService createEventBridgeService() {
        return createService(AWSTestServices.AWSEventBridgeLocalContainerTestService::new);
    }

    public static AWSTestService createIAMService() {
        return createService(AWSTestServices.AWSIAMLocalContainerTestService::new);
    }

    public static AWSTestService createKMSService() {
        return createService(AWSTestServices.AWSKMSLocalContainerTestService::new);
    }

    public static AWSTestService createLambdaService() {
        return createService(AWSTestServices.AWSLambdaLocalContainerTestService::new);
    }

    public static AWSTestService createSTSService() {
        return createService(AWSTestServices.AWSSTSLocalContainerTestService::new);
    }

    public static AWSTestService createDynamodbService() {
        return createService(AWSTestServices.AWSDynamodbLocalContainerTestService::new);
    }

    public static AWSTestService createSecretsManagerService() {
        return createService(AWSTestServices.AWSSecretsManagerLocalContainerTestService::new);
    }

    public static AWSTestService createSingletonDynamoDBService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSDynamodbLocalContainerTestService(), "dynamoDB");
    }

    public static AWSTestService createSingletonS3Service() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSS3LocalContainerTestService(), "s3");
    }

    public static AWSTestService createSingletonSQSService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSSQSLocalContainerTestService(), "sqs");
    }

    public static AWSTestService createSingletonEventBridgeService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSEventBridgeLocalContainerTestService(), "eventBridge");
    }

    public static AWSTestService createSingletonKinesisService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSKinesisLocalContainerTestService(), "kinesis");
    }

    private static class SingletonServiceHolder {
        private static final Map<String, AWSTestService> INSTANCES_HOLDER = new ConcurrentHashMap<>();

        public synchronized static AWSTestService getInstance(AWSTestService service, String name) {
            if (INSTANCES_HOLDER.get(name) == null) {
                SimpleTestServiceBuilder<AWSTestService> instance = builder();
                instance.addLocalMapping(() -> new SingletonAWSService(service, name))
                        .addRemoteMapping(AWSTestServices.AWSRemoteTestService::new)
                        .build();

                INSTANCES_HOLDER.put(name, instance.build());
            }

            return INSTANCES_HOLDER.get(name);
        }
    }
}
