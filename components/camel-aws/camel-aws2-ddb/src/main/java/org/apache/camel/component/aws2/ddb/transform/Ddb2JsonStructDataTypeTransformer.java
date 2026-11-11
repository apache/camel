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

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Instant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Message;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.aws2.ddb.transform.serialization.gson.JavaTimeInstantTypeAdapter;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.DataTypeTransformer;
import org.apache.camel.spi.Transformer;

/**
 * Writes the body as a Json String: a String is left as it is, bytes, a stream or a file are read as text, and another
 * body, such as the Record of the aws2-ddbstream consumer, is written as Json with Gson.
 */
@DataTypeTransformer(name = "aws2-ddb:application-x-struct",
                     description = "Transforms DynamoDB record into a Json String")
@BodyTypes(transformerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.VALUE, Carrier.MAP, Carrier.LIST,
        Carrier.POJO },
                                     description = "A String is left as it is, and bytes, a stream or a file are read as"
                                                   + " text; another body, such as the Record of the aws2-ddbstream"
                                                   + " consumer, is written as JSON with Gson"),
           transformerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String"))
public class Ddb2JsonStructDataTypeTransformer extends Transformer {

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new JavaTimeInstantTypeAdapter())
            .create();

    @Override
    public void transform(Message message, DataType fromType, DataType toType) {
        Object body = message.getBody();
        if (body instanceof String) {
            return;
        }

        if (body instanceof byte[] || isStreamOrFile(body)) {
            // their content is the Json, as for a String: Gson would write bytes as an array of numbers, and cannot
            // write a stream or a file, whose fields are of JDK classes it may not reflect into
            try {
                message.setBody(message.getMandatoryBody(String.class));
            } catch (InvalidPayloadException e) {
                throw new CamelExecutionException("Failed to read the stream or file body as text", message.getExchange(), e);
            }
            return;
        }

        message.setBody(gson.toJson(body));
    }

    private static boolean isStreamOrFile(Object body) {
        return body instanceof InputStream || body instanceof Reader || body instanceof StreamCache
                || body instanceof File || body instanceof Path || body instanceof WrappedFile;
    }
}
