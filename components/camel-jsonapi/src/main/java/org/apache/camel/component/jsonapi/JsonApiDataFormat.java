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
import java.util.ArrayList;
import java.util.List;

import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

/**
 * JSonApi data format for marshal/unmarshal
 */
@Dataformat("jsonApi")
public class JsonApiDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private CamelContext camelContext;

    private String dataFormatTypes;
    private Class<?>[] dataFormatTypeClasses;
    private String mainFormatType;
    private Class<?> mainFormatTypeClass;

    public JsonApiDataFormat() {
    }

    public JsonApiDataFormat(Class<?>[] dataFormatTypesClasses) {
        this.dataFormatTypeClasses = dataFormatTypesClasses;
    }

    public JsonApiDataFormat(Class<?> mainFormatTypeClass, Class<?>[] dataFormatTypes) {
        this.mainFormatTypeClass = mainFormatTypeClass;
        this.dataFormatTypeClasses = dataFormatTypes;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public String getDataFormatName() {
        return "jsonApi";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        ResourceConverter converter = new ResourceConverter(dataFormatTypeClasses);
        byte[] objectAsBytes = converter.writeDocument(new JSONAPIDocument<>(graph));
        stream.write(objectAsBytes);
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return unmarshal(exchange, (Object) stream);
    }

    @Override
    public Object unmarshal(Exchange exchange, Object body) throws Exception {
        ResourceConverter converter = new ResourceConverter(dataFormatTypeClasses);

        JSONAPIDocument<?> doc;
        if (body instanceof byte[] arr) {
            doc = converter.readDocument(arr, mainFormatTypeClass);
        } else {
            InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, exchange, body);
            doc = converter.readDocument(is, mainFormatTypeClass);
        }
        return doc.get();
    }

    public String getDataFormatTypes() {
        return dataFormatTypes;
    }

    /**
     * The classes (FQN name) to take into account for the marshalling. Multiple class names can be separated by comma.
     */
    public void setDataFormatTypes(String dataFormatTypes) {
        this.dataFormatTypes = dataFormatTypes;
    }

    public Class<?>[] getDataFormatTypeClasses() {
        return dataFormatTypeClasses;
    }

    /**
     * The classes to take into account for the marshalling.
     */
    public void setDataFormatTypeClasses(Class<?>[] dataFormatTypeClasses) {
        this.dataFormatTypeClasses = dataFormatTypeClasses;
    }

    public String getMainFormatType() {
        return mainFormatType;
    }

    /**
     * The class (FQN name) to take into account while unmarshalling
     */
    public void setMainFormatType(String mainFormatType) {
        this.mainFormatType = mainFormatType;
    }

    public Class<?> getMainFormatTypeClass() {
        return mainFormatTypeClass;
    }

    /**
     * The class to take into account while unmarshalling
     */
    public void setMainFormatTypeClass(Class<?> mainFormatTypeClass) {
        this.mainFormatTypeClass = mainFormatTypeClass;
    }

    @Override
    protected void doBuild() throws Exception {
        if (dataFormatTypeClasses == null && dataFormatTypes != null) {
            List<Class<?>> classes = new ArrayList<>();
            for (String name : dataFormatTypes.split(",")) {
                Class<?> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(name);
                classes.add(clazz);
            }
            dataFormatTypeClasses = classes.toArray(new Class<?>[0]);
        }
        if (mainFormatTypeClass == null && mainFormatType != null) {
            mainFormatTypeClass = getCamelContext().getClassResolver().resolveMandatoryClass(mainFormatType);
        }
    }

}
