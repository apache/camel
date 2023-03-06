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
package org.apache.camel.component.google.storage;

import java.util.Map;

import com.google.api.gax.rpc.ApiException;
import com.google.cloud.storage.Storage;
import org.apache.camel.health.HealthCheckResultBuilder;
import org.apache.camel.impl.health.AbstractHealthCheck;
import org.apache.camel.util.ObjectHelper;

public class GoogleCloudStorageConsumerHealthCheck extends AbstractHealthCheck {

    private final GoogleCloudStorageConsumer googleCloudStorageConsumer;

    public GoogleCloudStorageConsumerHealthCheck(GoogleCloudStorageConsumer googleCloudStorageConsumer, String routeId) {
        super("camel", "google-cloud-storage-consumer-" + routeId);
        this.googleCloudStorageConsumer = googleCloudStorageConsumer;
    }

    @Override
    protected void doCall(HealthCheckResultBuilder builder, Map<String, Object> options) {
        Storage client;
        try {
            GoogleCloudStorageConfiguration configuration = googleCloudStorageConsumer.getConfiguration();
            if (ObjectHelper.isNotEmpty(configuration.getStorageClient())) {
                client = configuration.getStorageClient();
            } else {
                client = googleCloudStorageConsumer.getStorageClient();
            }
            client.list();
        } catch (ApiException e) {
            builder.message(e.getMessage());
            builder.error(e);
            if (ObjectHelper.isNotEmpty(e.getStatusCode())) {
                builder.detail(SERVICE_STATUS_CODE, e.getStatusCode());
            }
            if (ObjectHelper.isNotEmpty(e.getStatusCode().getCode())) {
                builder.detail(SERVICE_ERROR_CODE, e.getStatusCode().getCode());
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
