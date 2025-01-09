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
                .addRemoteMapping(AWSTestServices.AWSRemoteTestService::new)
                .addLocalMapping(supplier)
                .withPropertyNameFormat("%s-service.instance.type")
                .build();
    }

    public static AWSService createKinesisService() {
        return builder()
                .addRemoteMapping(AWSTestServices.AWSRemoteTestService::new)
                .addLocalMapping(AWSTestServices.AWSKinesisLocalContainerTestService::new)
                .withPropertyNameFormat("%s-service.kinesis.instance.type")
                .build();
    }

    public static AWSService createSQSService() {
        return createService(AWSTestServices.AWSSQSLocalContainerTestService::new);
    }

    public static AWSService createS3Service() {
        return createService(AWSTestServices.AWSS3LocalContainerTestService::new);
    }

    public static AWSService createSNSService() {
        return createService(AWSTestServices.AWSSNSLocalContainerTestService::new);
    }

    public static AWSService createConfigService() {
        return createService(AWSTestServices.AWSConfigLocalContainerTestService::new);
    }

    public static AWSService createCloudWatchService() {
        return createService(AWSTestServices.AWSCloudWatchLocalContainerTestService::new);
    }

    public static AWSService createEC2Service() {
        return createService(AWSTestServices.AWSEC2LocalContainerTestService::new);
    }

    public static AWSService createEventBridgeService() {
        return createService(AWSTestServices.AWSEventBridgeLocalContainerTestService::new);
    }

    public static AWSService createIAMService() {
        return createService(AWSTestServices.AWSIAMLocalContainerTestService::new);
    }

    public static AWSService createKMSService() {
        return createService(AWSTestServices.AWSKMSLocalContainerTestService::new);
    }

    public static AWSService createLambdaService() {
        return createService(AWSTestServices.AWSLambdaLocalContainerTestService::new);
    }

    public static AWSService createSTSService() {
        return createService(AWSTestServices.AWSSTSLocalContainerTestService::new);
    }

    public static AWSService createDynamodbService() {
        return createService(AWSTestServices.AWSDynamodbLocalContainerTestService::new);
    }

    public static AWSService createSecretsManagerService() {
        return createService(AWSTestServices.AWSSecretsManagerLocalContainerTestService::new);
    }

    public static AWSService createSingletonDynamoDBService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSDynamodbLocalContainerTestService(), "dynamoDB");
    }

    public static AWSService createSingletonS3Service() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSS3LocalContainerTestService(), "s3");
    }

    public static AWSService createSingletonSQSService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSSQSLocalContainerTestService(), "sqs");
    }

    public static AWSService createSingletonEventBridgeService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSEventBridgeLocalContainerTestService(), "eventBridge");
    }

    public static AWSService createSingletonKinesisService() {
        return SingletonServiceHolder.getInstance(new AWSTestServices.AWSKinesisLocalContainerTestService(), "kinesis");
    }

    private static class SingletonServiceHolder {
        private static final Map<String, AWSService> INSTANCES_HOLDER = new ConcurrentHashMap<>();

        public synchronized static AWSService getInstance(AWSService service, String name) {
            if (INSTANCES_HOLDER.get(name) == null) {
                SimpleTestServiceBuilder<AWSService> instance = builder();
                instance.addLocalMapping(() -> new SingletonAWSService(service, name))
                        .addRemoteMapping(AWSTestServices.AWSRemoteTestService::new)
                        .build();

                INSTANCES_HOLDER.put(name, instance.build());
            }

            return INSTANCES_HOLDER.get(name);
        }
    }
}
