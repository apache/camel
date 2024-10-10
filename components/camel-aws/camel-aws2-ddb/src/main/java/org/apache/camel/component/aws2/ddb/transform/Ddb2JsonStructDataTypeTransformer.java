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

package org.apache.camel.component.aws2.ddb.transform;

import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.camel.Message;
import org.apache.camel.component.aws2.ddb.transform.serialization.gson.JavaTimeInstantTypeAdapter;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

@DataTypeTransformer(name = "aws2-ddb:application-x-struct",
                     description = "Transforms DynamoDB record into a Json node")
public class Ddb2JsonStructDataTypeTransformer extends Transformer {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new JavaTimeInstantTypeAdapter())
            .create();

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        if (message.getBody() instanceof String) {
            return;
        }

        message.setBody(gson.toJson(message.getBody()));
    }
}
