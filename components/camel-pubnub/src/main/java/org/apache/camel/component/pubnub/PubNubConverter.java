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
package org.apache.camel.component.pubnub;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import org.apache.camel.Converter;

/**
 * Converts the PubNub objects the consumer sets as the message body.
 */
@Converter(generateLoader = true)
public final class PubNubConverter {

    private PubNubConverter() {
    }

    /**
     * The payload of a received PubNub message as text: a string message as its text, any other message (a JSON object,
     * array, number or boolean) as JSON.
     */
    @Converter
    public static String toString(PNMessageResult message) {
        JsonElement payload = message.getMessage();
        if (payload instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        return payload.toString();
    }
}
