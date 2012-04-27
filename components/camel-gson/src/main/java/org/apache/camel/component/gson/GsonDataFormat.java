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
package org.apache.camel.component.gson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;

import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using <a href="http://code.google.com/p/google-gson/">Gson</a> to marshal to and from JSON.
 */
public class GsonDataFormat implements DataFormat {

    private final Gson gson;
    private Class<?> unmarshalType;

    public GsonDataFormat() {
        this(Map.class);
    }

    /**
     * Use the default Gson {@link Gson} and with a custom
     * unmarshal type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public GsonDataFormat(Class<?> unmarshalType) {
        this(new Gson(), unmarshalType);
    }

    /**
     * Use the default Gson {@link Gson} and with a custom
     * unmarshal type and {@link ExclusionStrategy}
     *
     * @param unmarshalType the custom unmarshal type
     * @param exclusionStrategies one or more custom ExclusionStrategy implementations
     */
    public GsonDataFormat(Class<?> unmarshalType, ExclusionStrategy... exclusionStrategies) {
        this(createGsonWithExclusionStrategy(exclusionStrategies), unmarshalType);
    }

    /**
     * Use a custom Gson mapper and and unmarshal type
     *
     * @param gson          the custom mapper
     * @param unmarshalType the custom unmarshal type
     */
    public GsonDataFormat(Gson gson, Class<?> unmarshalType) {
        this.gson = gson;
        this.unmarshalType = unmarshalType;
    }

    private static Gson createGsonWithExclusionStrategy(ExclusionStrategy... exclusionStrategies) {
        return exclusionStrategies != null ? new GsonBuilder().setExclusionStrategies(exclusionStrategies).create() : new Gson();
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        BufferedWriter writer = IOHelper.buffered(new OutputStreamWriter(stream));
        gson.toJson(graph, writer);
        writer.close();
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(stream));
        Object result = gson.fromJson(reader, this.unmarshalType);
        reader.close();
        return result;
    }

    // Properties
    // -------------------------------------------------------------------------

    public Class<?> getUnmarshalType() {
        return this.unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

    public Gson getGson() {
        return this.gson;
    }

}
