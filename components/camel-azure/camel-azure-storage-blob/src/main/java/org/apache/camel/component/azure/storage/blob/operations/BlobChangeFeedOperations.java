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
package org.apache.camel.component.azure.storage.blob.operations;

import java.time.OffsetDateTime;
import java.util.List;

import com.azure.core.util.Context;
import com.azure.storage.blob.changefeed.BlobChangefeedClient;
import com.azure.storage.blob.changefeed.models.BlobChangefeedEvent;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.blob.BlobConfigurationOptionsProxy;
import org.apache.camel.util.ObjectHelper;

public class BlobChangeFeedOperations {

    private final BlobChangefeedClient client;
    private final BlobConfigurationOptionsProxy configurationOptionsProxy;

    public BlobChangeFeedOperations(BlobChangefeedClient client, BlobConfigurationOptionsProxy configurationOptionsProxy) {
        ObjectHelper.notNull(client, "client cannot be null");

        this.client = client;
        this.configurationOptionsProxy = configurationOptionsProxy;
    }

    public BlobOperationResponse getEvents(final Exchange exchange) {
        final OffsetDateTime startTime = configurationOptionsProxy.getChangeFeedStartTime(exchange);
        final OffsetDateTime endTime = configurationOptionsProxy.getChangeFeedEndTime(exchange);
        final Context context = configurationOptionsProxy.getChangeFeedContext(exchange);

        if (ObjectHelper.isEmpty(startTime) || ObjectHelper.isEmpty(endTime)) {
            return BlobOperationResponse.create(getEvents());
        } else {
            return BlobOperationResponse.create(getEvents(startTime, endTime, context));
        }
    }

    private List<BlobChangefeedEvent> getEvents() {
        return client.getEvents().stream().toList();
    }

    private List<BlobChangefeedEvent> getEvents(
            final OffsetDateTime startTime, final OffsetDateTime endTime, final Context context) {

        return client.getEvents(startTime, endTime, context).stream().toList();
    }

}
