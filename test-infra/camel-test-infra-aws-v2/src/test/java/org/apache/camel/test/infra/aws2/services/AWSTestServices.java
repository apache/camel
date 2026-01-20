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

public class AWSTestServices {

    public static class AWSCloudWatchLocalContainerTestService extends AWSCloudWatchLocalContainerInfraService
            implements AWSService {
    }

    public static class AWSConfigLocalContainerTestService extends AWSConfigLocalContainerInfraService implements AWSService {
    }

    public static class AWSDynamodbLocalContainerTestService extends AWSDynamodbLocalContainerInfraService
            implements AWSService {
    }

    public static class AWSEC2LocalContainerTestService extends AWSEC2LocalContainerInfraService implements AWSService {
    }

    public static class AWSEventBridgeLocalContainerTestService extends AWSEventBridgeLocalContainerInfraService
            implements AWSService {
    }

    public static class AWSIAMLocalContainerTestService extends AWSIAMLocalContainerInfraService implements AWSService {
    }

    public static class AWSKinesisLocalContainerTestService extends AWSKinesisLocalContainerInfraService implements AWSService {
    }

    public static class AWSKMSLocalContainerTestService extends AWSKMSLocalContainerInfraService implements AWSService {
    }

    public static class AWSLambdaLocalContainerTestService extends AWSLambdaLocalContainerInfraService implements AWSService {
    }

    public static class AWSS3LocalContainerTestService extends AWSS3LocalContainerInfraService implements AWSService {
    }

    public static class AWSSecretsManagerLocalContainerTestService extends AWSSecretsManagerLocalContainerInfraService
            implements AWSService {
    }

    public static class AWSSNSLocalContainerTestService extends AWSSNSLocalContainerInfraService implements AWSService {
    }

    public static class AWSSQSLocalContainerTestService extends AWSSQSLocalContainerInfraService implements AWSService {
    }

    public static class AWSSSMLocalContainerTestService extends AWSSSMLocalContainerInfraService implements AWSService {
    }

    public static class AWSSTSLocalContainerTestService extends AWSSTSLocalContainerInfraService implements AWSService {
    }

    public static class AWSTranscribeLocalContainerTestService extends AWSTranscribeLocalContainerInfraService
            implements AWSService {
    }

    public static class AWSRemoteTestService extends AWSRemoteInfraService implements AWSService {
    }
}
