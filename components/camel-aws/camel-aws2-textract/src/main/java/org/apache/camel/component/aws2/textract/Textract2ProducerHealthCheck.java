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

package org.apache.camel.component.aws2.textract;

import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.Document;

public class Textract2ProducerHealthCheck extends AbstractHealthCheck {

    private final Textract2Endpoint textract2Endpoint;

    public Textract2ProducerHealthCheck(Textract2Endpoint textract2Endpoint, String clientId) {
        super("camel", "producer:aws2-textract-" + clientId);
        this.textract2Endpoint = textract2Endpoint;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Textract2Configuration configuration = textract2Endpoint.getConfiguration();
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            if (!TextractClient.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                builder.message("The service is not supported in this region");
                builder.down();
                return;
            }
        }
        try {
            TextractClient textractClient = textract2Endpoint.getTextractClient();
            // Create a minimal test request to verify the client is working
            byte[] testData = "test".getBytes();
            Document testDocument =
                    Document.builder().bytes(SdkBytes.fromByteArray(testData)).build();
            DetectDocumentTextRequest testRequest =
                    DetectDocumentTextRequest.builder().document(testDocument).build();

            // This will likely fail because "test" is not a valid document, but it will validate credentials and
            // connectivity
            textractClient.detectDocumentText(testRequest);
        } catch (AwsServiceException e) {
            // For health check, we consider certain errors as "healthy" since they indicate the service is reachable
            if ("InvalidParameterException".equals(e.awsErrorDetails().errorCode())
                    || "UnsupportedDocumentException".equals(e.awsErrorDetails().errorCode())) {
                // These errors mean the service is reachable but our test document is invalid - this is expected
                builder.up();
                return;
            }

            builder.message(e.getMessage());
            builder.error(e);
            if (ObjectHelper.isNotEmpty(e.statusCode())) {
                builder.detail(SERVICE_STATUS_CODE, e.statusCode());
            }
            if (ObjectHelper.isNotEmpty(e.awsErrorDetails().errorCode())) {
                builder.detail(SERVICE_ERROR_CODE, e.awsErrorDetails().errorCode());
            }
            builder.down();
            return;
        } catch (Exception e) {
            builder.error(e);
            builder.down();
            return;
        }
        builder.up();
    }
}
