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

public class AWSTestServices {

    public static class AWSCloudWatchLocalContainerTestService extends AWSCloudWatchLocalContainerService
            implements AWSTestService {
    }

    public static class AWSConfigLocalContainerTestService extends AWSConfigLocalContainerService implements AWSTestService {
    }

    public static class AWSDynamodbLocalContainerTestService extends AWSDynamodbLocalContainerService
            implements AWSTestService {
    }

    public static class AWSEC2LocalContainerTestService extends AWSEC2LocalContainerService implements AWSTestService {
    }

    public static class AWSEventBridgeLocalContainerTestService extends AWSEventBridgeLocalContainerService
            implements AWSTestService {
    }

    public static class AWSIAMLocalContainerTestService extends AWSIAMLocalContainerService implements AWSTestService {
    }

    public static class AWSKinesisLocalContainerTestService extends AWSKinesisLocalContainerService implements AWSTestService {
    }

    public static class AWSKMSLocalContainerTestService extends AWSKMSLocalContainerService implements AWSTestService {
    }

    public static class AWSLambdaLocalContainerTestService extends AWSLambdaLocalContainerService implements AWSTestService {
    }

    public static class AWSS3LocalContainerTestService extends AWSS3LocalContainerService implements AWSTestService {
    }

    public static class AWSSecretsManagerLocalContainerTestService extends AWSSecretsManagerLocalContainerService
            implements AWSTestService {
    }

    public static class AWSSNSLocalContainerTestService extends AWSSNSLocalContainerService implements AWSTestService {
    }

    public static class AWSSQSLocalContainerTestService extends AWSSQSLocalContainerService implements AWSTestService {
    }

    public static class AWSSTSLocalContainerTestService extends AWSSTSLocalContainerService implements AWSTestService {
    }

    public static class AWSRemoteTestService extends AWSRemoteService implements AWSTestService {
    }
}
