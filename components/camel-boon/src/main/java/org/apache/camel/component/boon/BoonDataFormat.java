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
package org.apache.camel.component.boon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.NonManagedService;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.boon.json.JsonFactory;
import org.boon.json.ObjectMapper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> (
 * {@link DataFormat}) using <a
 * href="http://richardhightower.github.io/site/Boon/">Boon</a> to marshal to
 * and from JSON.
 */
public class BoonDataFormat extends ServiceSupport implements DataFormat, DataFormatName, NonManagedService {

    private final ObjectMapper objectMapper;
    private Class<?> unmarshalType;
    private boolean useList;

    public BoonDataFormat() {
        this(null);
    }

    /**
     * Use the default Boon {@link ObjectMapper} and with a custom unmarshal
     * type
     * 
     * @param unmarshalType the custom unmarshal type
     */
    public BoonDataFormat(Class<?> unmarshalType) {
        this(unmarshalType, JsonFactory.create());
    }

    /**
     * Use a custom unmarshal type and Boon mapper
     * 
     * @param mapper the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public BoonDataFormat(Class<?> unmarshalType, ObjectMapper mapper) {
        this.unmarshalType = unmarshalType;
        this.objectMapper = mapper;
    }

    @Override
    public String getDataFormatName() {
        return "boon";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        BufferedWriter writer = IOHelper.buffered(new OutputStreamWriter(stream, ExchangeHelper.getCharsetName(exchange)));
        objectMapper.toJson(graph, writer);
        writer.close();
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(stream, ExchangeHelper.getCharsetName(exchange)));
        Object result;
        try {
            if (unmarshalType != null) {
                result = objectMapper.fromJson(reader, unmarshalType);
            } else {
                result = objectMapper.fromJson(reader);
            }
        } finally {
            IOHelper.close(reader);
        }
        return result;
    }

    @Override
    protected void doStart() throws Exception {
        if (useList) {
            useList();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
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
    
    public Boolean getUseList() {
        return useList;
    }

    public void setUseList(Boolean useList) {
        this.useList = useList;
    }    

    /**
     * Uses {@link java.util.List} when unmarshalling.
     */
    public void useList() {
        setUnmarshalType(List.class);
    }
}
