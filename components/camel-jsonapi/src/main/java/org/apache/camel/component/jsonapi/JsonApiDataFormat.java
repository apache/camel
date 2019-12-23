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
package org.apache.camel.component.jsonapi;

import java.io.InputStream;
import java.io.OutputStream;

import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

/**
 * JSonApi data format for marshal/unmarshal
 */
@Dataformat("jsonApi")
public class JsonApiDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private Class<?>[] dataFormatTypes;
    private Class<?> mainFormatType;

    public JsonApiDataFormat() {
    }

    public JsonApiDataFormat(Class<?>[] dataFormatTypes) {
        this.dataFormatTypes = dataFormatTypes;
    }

    public JsonApiDataFormat(Class<?> mainFormatType, Class<?>[] dataFormatTypes) {
        this.mainFormatType = mainFormatType;
        this.dataFormatTypes = dataFormatTypes;
    }
    
    @Override
    public String getDataFormatName() {
        return "jsonApi";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        ResourceConverter converter = new ResourceConverter(dataFormatTypes);
        byte[] objectAsBytes = converter.writeDocument(new JSONAPIDocument<>(graph));
        stream.write(objectAsBytes);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        ResourceConverter converter = new ResourceConverter(dataFormatTypes);
        JSONAPIDocument<?> jsonApiDocument = converter.readDocument(stream, mainFormatType);
        return jsonApiDocument.get();
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    /**
     * The classes to take into account while marshalling
     */
    public void setDataFormatTypes(Class<?>[] dataFormatTypes) {
        this.dataFormatTypes = dataFormatTypes;
    }

    /**
     * The classes to take into account while unmarshalling
     */
    public void setMainFormatType(Class<?> mainFormatType) {
        this.mainFormatType = mainFormatType;
    }
}
