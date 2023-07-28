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
package org.apache.camel.component.aws2.sns;

import java.util.Map;

import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

public class Sns2ProducerHealthCheck extends AbstractHealthCheck {

    private final Sns2Endpoint sns2Endpoint;

    public Sns2ProducerHealthCheck(Sns2Endpoint sns2Endpoint, String clientId) {
        super("camel", "producer:aws2-sns-" + clientId);
        this.sns2Endpoint = sns2Endpoint;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Sns2Configuration configuration = sns2Endpoint.getConfiguration();
        try {
            if (!SnsClient.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                builder.message("The service is not supported in this region");
                builder.down();
                return;
            }
            SnsClient client = sns2Endpoint.getSNSClient();
            client.listSubscriptions();
        } catch (AwsServiceException e) {
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
