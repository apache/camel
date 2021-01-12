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
package org.apache.camel.component.aws2.sts;

import java.util.Map;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;

public class STS2ComponentVerifierExtension extends DefaultComponentVerifierExtension {

    public STS2ComponentVerifierExtension() {
        this("aws2-sts");
    }

    public STS2ComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {

        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .error(ResultErrorHelper.requiresOption("accessKey", parameters))
                .error(ResultErrorHelper.requiresOption("secretKey", parameters))
                .error(ResultErrorHelper.requiresOption("region", parameters));

        // Validate using the catalog

        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        try {
            STS2Configuration configuration = setProperties(new STS2Configuration(), parameters);
            if (!StsClient.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                ResultErrorBuilder errorBuilder
                        = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.ILLEGAL_PARAMETER,
                                "The service is not supported in this region");
                return builder.error(errorBuilder.build()).build();
            }
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
            StsClientBuilder clientBuilder = StsClient.builder();
            StsClient client = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred))
                    .region(Region.of(configuration.getRegion())).build();
            client.serviceName();
        } catch (SdkClientException e) {
            ResultErrorBuilder errorBuilder
                    = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, e.getMessage())
                            .detail("aws_sts_exception_message", e.getMessage())
                            .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, e.getClass().getName())
                            .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, e);

            builder.error(errorBuilder.build());
        } catch (Exception e) {
            builder.error(ResultErrorBuilder.withException(e).build());
        }
        return builder.build();
    }
}
