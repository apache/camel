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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.pubnub.api.models.consumer.pubsub.BasePubSubResult;
import com.pubnub.api.models.consumer.pubsub.PNMessageResult;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PubNubConverterTest extends CamelTestSupport {

    @Test
    public void testStringMessageToString() {
        assertEquals("Hello World", convert(new JsonPrimitive("Hello World")));
    }

    @Test
    public void testObjectMessageToString() {
        JsonObject json = new JsonObject();
        json.addProperty("text", "Message");
        json.addProperty("count", 3);
        assertEquals("{\"text\":\"Message\",\"count\":3}", convert(json));
    }

    @Test
    public void testArrayMessageToString() {
        JsonArray json = new JsonArray();
        json.add("a");
        json.add(1);
        assertEquals("[\"a\",1]", convert(json));
    }

    @Test
    public void testNumberMessageToString() {
        assertEquals("42", convert(new JsonPrimitive(42)));
    }

    private String convert(JsonElement payload) {
        PNMessageResult message = new PNMessageResult(
                new BasePubSubResult("mychannel", null, 14607577960932487L, null, "publisher"), payload, null, null);
        return context.getTypeConverter().convertTo(String.class, message);
    }
}
