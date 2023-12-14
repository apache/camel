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

package org.apache.camel.spi;

import java.util.Objects;

/**
 * Mime types known to Camel data types and its transformers.
 */
public enum MimeType {
    JSON("application/json"),
    PROTOBUF("application/protobuf"),
    PROTOBUF_BINARY("protobuf/binary"),
    PROTOBUF_STRUCT("protobuf/x-struct"),
    PROTOBUF_JAVA_OBJECT("protobuf/x-java-object"),
    AVRO("application/avro"),
    AVRO_BINARY("avro/binary"),
    AVRO_STRUCT("avro/x-struct"),
    AVRO_JAVA_OBJECT("avro/x-java-object"),
    BINARY("application/octet-stream"),
    TEXT("text/plain"),
    JAVA_OBJECT("application/x-java-object"),
    STRUCT("application/x-struct");

    private static final MimeType[] VALUES = values();
    private final String type;

    MimeType(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }

    public static MimeType of(String type) {
        for (MimeType mt : VALUES) {
            if (Objects.equals(type, mt.type)) {
                return mt;
            }
        }

        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
