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
package org.apache.camel.dataformat.protobuf;

import com.google.protobuf.Descriptors.Descriptor;

/**
 * Turns the text of a .proto file into the descriptor of one of its message types, for the schema option of the
 * protobuf data format. protobuf-java cannot parse .proto text, so the implementation is in camel-protobuf-schema,
 * which carries the parser and its dependencies. It is found as a single bean of this type in the registry, or else on
 * the classpath through the factory {@link #FACTORY}.
 */
public interface ProtobufSchemaParser {

    /**
     * The key of the implementation under META-INF/services/org/apache/camel/
     */
    String FACTORY = "protobuf-schema-parser";

    /**
     * The descriptor of a message type of the schema.
     *
     * @param  schema                   the text of a .proto file
     * @param  messageName              the message type, simple or with its package, and Outer.Inner for a nested type;
     *                                  null or blank for the first message type of the file
     * @return                          the descriptor
     * @throws IllegalArgumentException if the schema cannot be read, or has no such message type
     */
    Descriptor parse(String schema, String messageName);
}
