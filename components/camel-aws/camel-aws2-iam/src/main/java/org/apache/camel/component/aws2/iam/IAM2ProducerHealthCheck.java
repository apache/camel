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
package org.apache.camel.component.aws2.iam;

import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;

public class IAM2ProducerHealthCheck extends AbstractHealthCheck {
    private final IAM2Endpoint endpoint;

    public IAM2ProducerHealthCheck(IAM2Endpoint endpoint, String clientId) {
        super("camel", "producer:aws2-iam-" + clientId);
        this.endpoint = endpoint;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        try {
            IAM2Configuration configuration = endpoint.getConfiguration();
            if (!IamClient.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                builder.message("The service is not supported in this region");
                builder.down();
                return;
            }
            IamClient client = endpoint.getIamClient();
            client.listAccessKeys(ListAccessKeysRequest.builder().maxItems(1).build());
        } catch (SdkClientException e) {
            builder.message(e.getMessage());
            builder.error(e);
            builder.down();
        } catch (Exception e) {
            builder.error(e);
            builder.down();
            return;
        }
        builder.up();
    }

}
