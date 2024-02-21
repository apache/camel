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
package org.apache.camel.component.salesforce.api.dto.pubsub;

import com.salesforce.eventbus.protobuf.Error;
import org.apache.camel.component.salesforce.internal.client.PubSubApiClient;

public class PublishResult {

    private final com.salesforce.eventbus.protobuf.PublishResult source;

    public PublishResult(com.salesforce.eventbus.protobuf.PublishResult source) {
        this.source = source;
    }

    // Replay ID is opaque.
    public String getReplayId() {
        return PubSubApiClient.base64EncodeByteString(source.getReplayId());
    }

    public boolean hasError() {
        return source.hasError();
    }

    public Error getError() {
        return source.getError();
    }

    public String getCorrelationKey() {
        return source.getCorrelationKey();
    }

    public com.salesforce.eventbus.protobuf.PublishResult getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "PublishResult{" +
               "hasError=" + hasError() +
               ",error=" + getError() +
               ",correlationKey=" + getCorrelationKey() +
               "}";
    }
}
