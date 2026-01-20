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
package org.apache.camel.component.aws2.iam.client;

import org.apache.camel.component.aws.common.AwsClientBuilderUtil;
import org.apache.camel.component.aws2.iam.IAM2Configuration;
import software.amazon.awssdk.services.iam.IamClient;

/**
 * Factory class to create AWS IAM clients using common configuration.
 */
public final class IAM2ClientFactory {

    private IAM2ClientFactory() {
    }

    /**
     * Create an IAM client based on configuration.
     *
     * @param  configuration The IAM configuration
     * @return               Configured IamClient
     */
    public static IamClient getIamClient(IAM2Configuration configuration) {
        return AwsClientBuilderUtil.buildClient(
                configuration,
                IamClient::builder);
    }
}
