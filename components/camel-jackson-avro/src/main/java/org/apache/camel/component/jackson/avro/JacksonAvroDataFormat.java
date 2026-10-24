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
package org.apache.camel.component.jackson.avro;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import org.apache.camel.component.jackson.AbstractJacksonDataFormat;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * Marshal POJOs to Avro and back using Jackson.
 */
@Dataformat("avroJackson")
@Metadata(firstVersion = "3.10.0", title = "Avro Jackson",
          excludeProperties = "library,instanceClassName,schema,serializablePackages")
@BodyTypes(marshalIn = @BodyType(value = { Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                 description = "A POJO, a Map or a JsonNode to write with the Avro schema of the"
                                               + " schemaResolver, or a List for an array schema. A String or bytes are not"
                                               + " written as they are"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "binary",
                                  description = "The Avro binary data, without the schema, in the stream cache, or a byte"
                                                + " array when stream caching is off"),
           unmarshalIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "binary",
                                   description = "Avro binary data of the schema of the schemaResolver. A String fails"),
           unmarshalOut = @BodyType(value = { Carrier.MAP, Carrier.LIST },
                                    dependsOn = { "unmarshalType", "collectionType", "allowUnmarshallType", "allowJmsType" },
                                    byOption = @BodyType.When(option = "useList", value = "true", carriers = Carrier.LIST),
                                    description = "A Map (LinkedHashMap) for an Avro record, a List (ArrayList) for an array;"
                                                  + " an object of the class unmarshalType names, such as JsonNode"))
public class JacksonAvroDataFormat extends AbstractJacksonDataFormat {

    /**
     * Use the default Jackson {@link AvroMapper} and {@link Object}
     */
    public JacksonAvroDataFormat() {
    }

    /**
     * Use the default Jackson {@link AvroMapper} and with a custom unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonAvroDataFormat(Class<?> unmarshalType) {
        super(unmarshalType);
    }

    /**
     * Use the default Jackson {@link AvroMapper} and with a custom unmarshal type and JSON view
     *
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     */
    public JacksonAvroDataFormat(Class<?> unmarshalType, Class<?> jsonView) {
        super(unmarshalType, jsonView);
    }

    /**
     * Use a custom Jackson {@link AvroMapper} and and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonAvroDataFormat(AvroMapper mapper, Class<?> unmarshalType) {
        super(mapper, unmarshalType);
    }

    /**
     * Use a custom Jackson {@link AvroMapper}, unmarshal type and JSON view
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     * @param jsonView      marker class to specify properties to be included during marshalling. See also
     */
    public JacksonAvroDataFormat(AvroMapper mapper, Class<?> unmarshalType, Class<?> jsonView) {
        super(mapper, unmarshalType, jsonView);
    }

    @Override
    public String getDataFormatName() {
        return "avroJackson";
    }

    @Override
    protected String getDefaultContentType() {
        return "application/avro";
    }

    @Override
    protected AvroMapper createNewObjectMapper() {
        // Enable BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES by default as defense-in-depth against gadget-chain
        // deserialization when polymorphic typing is enabled, consistent with camel-jackson (CAMEL-23786)
        // and the transform/Avro.java mapper.
        return AvroMapper.builder()
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
                .build();
    }

    @Override
    protected Class<? extends ObjectMapper> getObjectMapperClass() {
        return AvroMapper.class;
    }

}
