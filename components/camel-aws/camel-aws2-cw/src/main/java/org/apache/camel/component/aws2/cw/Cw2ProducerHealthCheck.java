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

package org.apache.camel.component.aws2.cw;

import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.ListDashboardsRequest;

public class Cw2ProducerHealthCheck extends AbstractHealthCheck {

    private final Cw2Endpoint cw2Endpoint;

    public Cw2ProducerHealthCheck(Cw2Endpoint cw2Endpoint, String clientId) {
        super("camel", "producer:aws2-cw-" + clientId);
        this.cw2Endpoint = cw2Endpoint;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Cw2Configuration configuration = cw2Endpoint.getConfiguration();
        if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
            if (!CloudWatchClient.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                builder.message("The service is not supported in this region");
                builder.down();
                return;
            }
        }
        try {
            CloudWatchClient cw2Client = cw2Endpoint.getCloudWatchClient();
            cw2Client.listDashboards(ListDashboardsRequest.builder().build());
        } catch (AwsServiceException e) {
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
