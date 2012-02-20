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
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ObjectHelper;

public class AvroDataFormat implements DataFormat {

    private Schema schema;
    private String instanceClassName;

    /**
     * @param schema
     */
    public AvroDataFormat(Schema schema) {
        this.schema = schema;
    }

    public AvroDataFormat() {
    }

    public synchronized Schema getSchema(Exchange exchange, Object graph) throws Exception {
        if (schema == null) {
            if (instanceClassName != null) {
                return loadDefaultSchema(instanceClassName, exchange.getContext());
            }
            if (graph != null && graph instanceof GenericContainer) {
                return loadDefaultSchema(graph.getClass().getName(), exchange.getContext());
            } else {
                throw new CamelException("There is not schema for avro marshaling / unmarshaling");
            }
        }
        return schema;
    }

    public void setSchema(Object schema) {
        if (schema instanceof Schema) {
            this.schema = (Schema) schema;
        } else {
            throw new IllegalArgumentException("The argument for setDefaultInstance should be subClass of " + Schema.class.getName());
        }
    }

    public void setInstanceClass(String className) throws Exception {
        ObjectHelper.notNull(className, "AvroDataFormat messageClass");
        instanceClassName = className;
    }

    protected Schema loadDefaultSchema(String className, CamelContext context) throws CamelException, ClassNotFoundException {
        Class<?> instanceClass = context.getClassResolver().resolveMandatoryClass(className);
        if (GenericContainer.class.isAssignableFrom(instanceClass)) {
            try {
                Method method = instanceClass.getMethod("getSchema", new Class[0]);
                return (Schema) method.invoke(instanceClass.newInstance(), new Object[0]);
            } catch (Exception ex) {
                throw new CamelException("Can't set the defaultInstance of AvroDataFormat with "
                        + className + ", caused by " + ex);
            }
        } else {
            throw new CamelException("Can't set the shcema of AvroDataFormat with "
                    + className + ", as the class is not a subClass of SpecificData");
        }
    }

    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        DatumWriter<Object> datum = new SpecificDatumWriter<Object>(getSchema(exchange, graph));
        Encoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        datum.write(graph, encoder);
        encoder.flush();
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        DatumReader<GenericRecord> reader = new SpecificDatumReader<GenericRecord>(getSchema(exchange, null));
        Decoder decoder = DecoderFactory.get().binaryDecoder(inputStream, null);
        Object result = reader.read(null, decoder);
        return result;
    }

}
