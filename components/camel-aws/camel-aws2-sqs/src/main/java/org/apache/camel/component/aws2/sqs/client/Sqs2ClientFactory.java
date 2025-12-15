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
package org.apache.camel.component.aws2.sqs.client;

import java.net.URI;

import org.apache.camel.component.aws.common.AwsClientBuilderUtil;
import org.apache.camel.component.aws2.sqs.Sqs2Configuration;
import org.apache.camel.util.FileUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Factory class to create AWS SQS clients using common configuration.
 */
public final class Sqs2ClientFactory {

    private static final String DEFAULT_AWS_HOST = "amazonaws.com";

    private Sqs2ClientFactory() {
    }

    /**
     * Create an SQS client based on configuration.
     *
     * @param  configuration The SQS configuration
     * @return               Configured SqsClient
     */
    public static SqsClient getSqsClient(Sqs2Configuration configuration) {
        return AwsClientBuilderUtil.buildClient(
                configuration,
                SqsClient::builder,
                builder -> {
                    // SQS-specific: Handle custom AWS host (non-amazonaws.com endpoints)
                    if (!isDefaultAwsHost(configuration) && !configuration.isOverrideEndpoint()) {
                        String endpointOverrideUri = getAwsEndpointUri(configuration);
                        builder.endpointOverride(URI.create(endpointOverrideUri));
                    }
                });
    }

    private static boolean isDefaultAwsHost(Sqs2Configuration configuration) {
        return DEFAULT_AWS_HOST.equals(configuration.getAmazonAWSHost());
    }

    private static String getAwsEndpointUri(Sqs2Configuration configuration) {
        return configuration.getProtocol() + "://" + getFullyQualifiedAWSHost(configuration);
    }

    private static String getFullyQualifiedAWSHost(Sqs2Configuration configuration) {
        String host = configuration.getAmazonAWSHost();
        host = FileUtil.stripTrailingSeparator(host);

        if (isDefaultAwsHost(configuration)) {
            return "sqs." + Region.of(configuration.getRegion()).id() + "." + host;
        }

        return host;
    }
}
