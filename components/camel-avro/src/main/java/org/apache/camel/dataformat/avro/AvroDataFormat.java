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
package org.apache.camel.dataformat.avro;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Decoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

@Dataformat("avro")
public class AvroDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    private static final String GENERIC_CONTAINER_CLASSNAME = GenericContainer.class.getName();
    private CamelContext camelContext;
    private Object schema;
    private transient Schema actualSchema;
    private String instanceClassName;

    public AvroDataFormat() {
    }

    public AvroDataFormat(Schema schema) {
        this.schema = schema;
    }

    @Override
    public String getDataFormatName() {
        return "avro";
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
    protected void doStart() throws Exception {
        if (schema != null) {
            if (schema instanceof Schema) {
                actualSchema = (Schema) schema;
            } else {
                actualSchema = loadSchema(schema.getClass().getName());
            }
        } else if (instanceClassName != null) {
            actualSchema = loadSchema(instanceClassName);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    // the getter/setter for Schema is Object type in the API

    public Object getSchema() {
        return actualSchema != null ? actualSchema : schema;
    }

    public void setSchema(Object schema) {
        this.schema = schema;
    }

    public String getInstanceClassName() {
        return instanceClassName;
    }

    public void setInstanceClassName(String className) {
        instanceClassName = className;
    }

    protected Schema loadSchema(String className) throws CamelException, ClassNotFoundException {
        // must use same class loading procedure to ensure working in OSGi
        Class<?> instanceClass = camelContext.getClassResolver().resolveMandatoryClass(className);
        Class<?> genericContainer = camelContext.getClassResolver().resolveMandatoryClass(GENERIC_CONTAINER_CLASSNAME);

        if (genericContainer.isAssignableFrom(instanceClass)) {
            try {
                Method method = instanceClass.getMethod("getSchema");
                return (Schema) method.invoke(camelContext.getInjector().newInstance(instanceClass));
            } catch (Exception ex) {
                throw new CamelException("Error calling getSchema on " + instanceClass, ex);
            }
        } else {
            throw new CamelException("Class " + instanceClass + " must be instanceof " + GENERIC_CONTAINER_CLASSNAME);
        }
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        // the schema should be from the graph class name
        Schema useSchema = actualSchema != null ? actualSchema : loadSchema(graph.getClass().getName());

        DatumWriter<Object> datum = new SpecificDatumWriter<>(useSchema);
        Encoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datum.write(graph, encoder);
        encoder.flush();
    }

    @Override
    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        ObjectHelper.notNull(actualSchema, "schema", this);

        ClassLoader classLoader = null;
        Class<?> clazz = camelContext.getClassResolver().resolveClass(actualSchema.getFullName());

        if (clazz != null) {
            classLoader = clazz.getClassLoader();
        }
        SpecificData specificData = new SpecificDataNoCache(classLoader);
        DatumReader<GenericRecord> reader = new SpecificDatumReader<>(null, null, specificData);
        reader.setSchema(actualSchema);
        Decoder decoder = DecoderFactory.get().binaryDecoder(inputStream, null);
        Object result = reader.read(null, decoder);
        return result;
    }

}
