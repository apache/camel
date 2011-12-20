/**
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
package org.apache.camel.component.jackson;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using <a href="http://jackson.codehaus.org/">Jackson</a> to marshal to and from JSON.
 */
public class JacksonDataFormat implements DataFormat {

    private final ObjectMapper objectMapper;
    private Class<?> unmarshalType;

    /**
     * Use the default Jackson {@link ObjectMapper} and {@link Map}
     */
    public JacksonDataFormat() {
        this(new ObjectMapper(), HashMap.class);
    }

    /**
     * Use the default Jackson {@link ObjectMapper} and with a custom
     * unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonDataFormat(Class<?> unmarshalType) {
        this(new ObjectMapper(), unmarshalType);
    }

    /**
     * Use a custom Jackson mapper and and unmarshal type
     *
     * @param mapper        the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public JacksonDataFormat(ObjectMapper mapper, Class<?> unmarshalType) {
        this.objectMapper = mapper;
        this.unmarshalType = unmarshalType;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        this.objectMapper.writeValue(stream, graph);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return this.objectMapper.readValue(stream, this.unmarshalType);
    }

    // Properties
    // -------------------------------------------------------------------------

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public ObjectMapper getObjectMapper() {
        return this.objectMapper;
    }

}
