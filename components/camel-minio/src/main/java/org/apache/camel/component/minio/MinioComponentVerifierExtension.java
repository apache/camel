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
package org.apache.camel.component.minio;

import java.util.Map;

import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;

import static org.apache.camel.util.ObjectHelper.isEmpty;
import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public class MinioComponentVerifierExtension extends DefaultComponentVerifierExtension {

    public MinioComponentVerifierExtension() {
        this("minio");
    }

    public MinioComponentVerifierExtension(String scheme) {
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
            MinioConfiguration configuration = setProperties(new MinioConfiguration(), parameters);
            MinioClient.Builder clientBuilderRequest = MinioClient.builder();

            if (isEmpty(configuration.getEndpoint())) {
                ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(
                        VerificationError.StandardCode.ILLEGAL_PARAMETER, "The service endpoint has not defined");
                return builder.error(errorBuilder.build()).build();
            }

            if (isNotEmpty(configuration.getProxyPort())) {
                clientBuilderRequest.endpoint(
                        configuration.getEndpoint(), configuration.getProxyPort(), configuration.isSecure());
            } else {
                clientBuilderRequest.endpoint(configuration.getEndpoint());
            }

            if (isNotEmpty(configuration.getRegion())) {
                clientBuilderRequest.region(configuration.getRegion());
            }

            if (isNotEmpty(configuration.getAccessKey()) && isNotEmpty(configuration.getSecretKey())) {
                clientBuilderRequest.credentials(configuration.getAccessKey(), configuration.getSecretKey());
            }

            MinioClient client = clientBuilderRequest.build();
            client.listBuckets();
        } catch (MinioException e) {
            ResultErrorBuilder errorBuilder
                    = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, e.getMessage())
                            .detail("minio_exception_message", e.getMessage())
                            .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, e.getClass().getName())
                            .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, e);

            builder.error(errorBuilder.build());
        } catch (Exception e) {
            builder.error(ResultErrorBuilder.withException(e).build());
        }
        return builder.build();
    }
}
