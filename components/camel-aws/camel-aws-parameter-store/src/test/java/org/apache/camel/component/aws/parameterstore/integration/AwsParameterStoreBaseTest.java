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
package org.apache.camel.component.aws.parameterstore.integration;

import java.net.URI;

import org.apache.camel.CamelContext;
import org.apache.camel.component.aws.parameterstore.ParameterStoreComponent;
import org.apache.camel.test.infra.aws.common.services.AWSService;
import org.apache.camel.test.infra.aws2.clients.AWSSDKClientUtils;
import org.apache.camel.test.infra.aws2.services.AWSServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AwsParameterStoreBaseTest extends CamelTestSupport {
    @RegisterExtension
    public static AWSService service = AWSServiceFactory.createSSMService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        ParameterStoreComponent psComponent = context.getComponent("aws-parameter-store", ParameterStoreComponent.class);
        psComponent.getConfiguration().setSsmClient(AWSSDKClientUtils.newSsmClient());
        return context;
    }

    public static SsmClient getSsmClient() {
        String accessKey = service.getConnectionProperties().getProperty("aws.access.key");
        String region = service.getConnectionProperties().getProperty("aws.region");
        String secretKey = service.getConnectionProperties().getProperty("aws.secret.key");
        String host = service.getConnectionProperties().getProperty("aws.host");
        String protocol = service.getConnectionProperties().getProperty("aws.protocol");
        SsmClientBuilder clientBuilder = SsmClient.builder();
        AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
        clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
        clientBuilder = clientBuilder.region(Region.of(region));
        clientBuilder.endpointOverride(URI.create(protocol + "://" + host));
        return clientBuilder.build();
    }

    public String getSecretKey() {
        return service.getConnectionProperties().getProperty("aws.secret.key");
    }

    public String getAccessKey() {
        return service.getConnectionProperties().getProperty("aws.access.key");
    }

    public String getRegion() {
        return service.getConnectionProperties().getProperty("aws.region");
    }

    public String getProtocol() {
        return service.getConnectionProperties().getProperty("aws.protocol");
    }

    public String getHost() {
        return service.getConnectionProperties().getProperty("aws.host");
    }

    public String getUrlOverride() {
        return getProtocol() + "://" + getHost();
    }

}
