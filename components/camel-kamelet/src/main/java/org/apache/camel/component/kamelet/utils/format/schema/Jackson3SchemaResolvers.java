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
package org.apache.camel.component.kamelet.utils.format.schema;

import org.apache.camel.Processor;
import org.apache.camel.component.jackson3.avro.transform.AvroSchemaResolver;
import org.apache.camel.component.jackson3.protobuf.transform.ProtobufSchemaResolver;
import org.apache.camel.component.jackson3.transform.JsonSchemaResolver;

/**
 * The schema resolvers of camel-jackson3, camel-jackson3-avro and camel-jackson3-protobuf.
 */
class Jackson3SchemaResolvers extends SchemaResolverFactory {

    @Override
    Processor doCreate(Format format, String schema, String contentClass) {
        switch (format) {
            case AVRO:
                AvroSchemaResolver avro = new AvroSchemaResolver();
                avro.setSchema(schema);
                avro.setContentClass(contentClass);
                return avro;
            case PROTOBUF:
                ProtobufSchemaResolver protobuf = new ProtobufSchemaResolver();
                protobuf.setSchema(schema);
                protobuf.setContentClass(contentClass);
                return protobuf;
            default:
                JsonSchemaResolver json = new JsonSchemaResolver();
                json.setSchema(schema);
                json.setContentClass(contentClass);
                return json;
        }
    }

    @Override
    String className(Format format) {
        switch (format) {
            case AVRO:
                return "org.apache.camel.component.jackson3.avro.transform.AvroSchemaResolver";
            case PROTOBUF:
                return "org.apache.camel.component.jackson3.protobuf.transform.ProtobufSchemaResolver";
            default:
                return "org.apache.camel.component.jackson3.transform.JsonSchemaResolver";
        }
    }

    @Override
    String artifactId(Format format) {
        switch (format) {
            case AVRO:
                return "camel-jackson3-avro";
            case PROTOBUF:
                return "camel-jackson3-protobuf";
            default:
                return "camel-jackson3";
        }
    }
}
