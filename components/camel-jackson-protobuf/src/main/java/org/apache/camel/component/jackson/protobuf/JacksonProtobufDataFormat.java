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
package org.apache.camel.component.jackson.protobuf;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.protobuf.ProtobufMapper;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * Marshal POJOs to Protobuf and back using Jackson.
 */
@Dataformat("protobufJackson")
@Metadata(firstVersion = "3.10.0", title = "Protobuf Jackson",
          excludeProperties = "library,permissions,dateFormatPattern,instanceClass,contentTypeFormat,defaultInstance")
@BodyTypes(marshalIn = @BodyType(value = { Carrier.MAP, Carrier.POJO },
                                 description = "A POJO, a Map or a JsonNode to write as the message of the protobuf schema of"
                                               + " the schemaResolver. Text, bytes or a stream are written as they are, not"
                                               + " encoded"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "binary",
                                  description = "The protobuf binary data in the stream cache, or a byte array when stream"
                                                + " caching is off"),
           unmarshalIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "binary",
                                   description = "Protobuf binary data of the schema of the schemaResolver. A String fails"),
           unmarshalOut = @BodyType(value = Carrier.MAP,
                                    dependsOn = {
                                            "unmarshalType", "collectionType", "useList", "allowUnmarshallType",
                                            "allowJmsType" },
                                    description = "A Map (LinkedHashMap) of the fields of the message; an object of the class"
                                                  + " unmarshalType names, such as JsonNode"))
public class JacksonProtobufDataFormat extends JacksonDataFormat {

    /**
     * Use the default Jackson {@link ProtobufMapper} and {@link Object}
     */
    public JacksonProtobufDataFormat() {
    }

    /**
     * Use the default Jackson {@link ProtobufMapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonProtobufDataFormat(Class<?> unmarshalType) {
        super(unmarshalType);
    }

    /**
     * Use the default Jackson {@link ProtobufMapper} and with a custom unmarshal type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     */
    public JacksonProtobufDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        super(unmarshalType, jsonView);
    }

    /**
     * Use a custom Jackson {@link ProtobufMapper} and and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonProtobufDataFormat(ProtobufMapper mapper, Class<?> unmarshalType) {
        super(mapper, unmarshalType);
    }

    /**
     * Use a custom Jackson {@link ProtobufMapper}, unmarshal type and JSON view
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     */
    public JacksonProtobufDataFormat(ProtobufMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        super(mapper, unmarshalType, jsonView);
    }

    @Override
    public String getDataFormatName() {
        return "protobufJackson";
    }

    @Override
    protected String getDefaultContentType() {
        return "application/protobuf";
    }

    @Override
    protected ProtobufMapper createNewObjectMapper() {
        // Enable BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES by default as defense-in-depth against gadget-chain
        // deserialization when polymorphic typing is enabled, consistent with camel-jackson (CAMEL-23786)
        // and the transform/Protobuf.java mapper.
        return ProtobufMapper.builder()
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
                .build();
    }

    @Override
    protected Class<? extends ObjectMapper> getObjectMapperClass() {
        return ProtobufMapper.class;
    }

}
