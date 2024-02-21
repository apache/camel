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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.avro.AvroMapper;
import org.apache.camel.component.jackson.AbstractJacksonDataFormat;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Dataformat;

/**
 * Marshal POJOs to Avro and back using Jackson.
 */
@Dataformat("avroJackson")
@Metadata(firstVersion = "3.10.0", title = "Avro Jackson", excludeProperties = "library,instanceClassName,schema")
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
        return new AvroMapper();
    }

    @Override
    protected Class<? extends ObjectMapper> getObjectMapperClass() {
        return AvroMapper.class;
    }

}
