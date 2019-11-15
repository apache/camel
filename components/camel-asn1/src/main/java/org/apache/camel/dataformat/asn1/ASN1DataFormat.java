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
package org.apache.camel.dataformat.asn1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;

@Dataformat("asn1")
public class ASN1DataFormat extends ServiceSupport implements DataFormat, DataFormatName {
    private boolean usingIterator;
    private String clazzName;

    public ASN1DataFormat() {
        this.usingIterator = false;
    }

    public ASN1DataFormat(String clazzName) {
        this.usingIterator = true;
        this.clazzName = clazzName;
    }

    public ASN1DataFormat(Class<?> clazz) {
        this.usingIterator = true;
        this.clazzName = clazz.getName();
    }

    @Override
    public String getDataFormatName() {
        return "asn1";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        InputStream berOut = null;
        if (usingIterator) {
            if (clazzName != null) {
                Class<?> clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(clazzName);
                encodeGenericTypeObject(exchange, clazz, stream);
                return;
            }
            Object record = exchange.getIn().getBody();
            if (record instanceof ASN1Primitive) {
                ASN1Primitive asn1Primitive = ObjectHelper.cast(ASN1Primitive.class, record);
                berOut = new ByteArrayInputStream(asn1Primitive.getEncoded());
            } else if (record instanceof byte[]) {
                berOut = new ByteArrayInputStream(ObjectHelper.cast(byte[].class, record));
            }
        } else {
            byte[] byteInput = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, graph);
            berOut = new ByteArrayInputStream(byteInput);
        }
        try {
            IOHelper.copy(berOut, stream);
        } finally {
            IOHelper.close(berOut, stream);
        }
    }

    private void encodeGenericTypeObject(Exchange exchange, Class<?> clazz, OutputStream stream)
        throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException {
        Class<?>[] paramOut = new Class<?>[1];
        paramOut[0] = OutputStream.class;
        ReverseByteArrayOutputStream berOut = new ReverseByteArrayOutputStream(IOHelper.DEFAULT_BUFFER_SIZE / 256, true);
        Method encodeMethod = exchange.getIn().getBody().getClass().getDeclaredMethod("encode", paramOut);
        encodeMethod.invoke(exchange.getIn().getBody(), berOut);
        stream.write(berOut.getArray());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        if (usingIterator) {
            if (clazzName != null) {
                Class<?> clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(clazzName);
                ASN1GenericIterator asn1GenericIterator = new ASN1GenericIterator(clazz, stream);
                return asn1GenericIterator;
            }
            ASN1MessageIterator asn1MessageIterator = new ASN1MessageIterator(exchange, stream);
            return asn1MessageIterator;
        } else {
            ASN1Primitive asn1Record = null;
            byte[] asn1Bytes;
            try (ASN1InputStream ais = new ASN1InputStream(stream); ByteArrayOutputStream asn1Out = new ByteArrayOutputStream();) {
                while (ais.available() > 0) {
                    asn1Record = ais.readObject();
                    asn1Out.write(asn1Record.getEncoded());
                }
                asn1Bytes = asn1Out.toByteArray();
            }
            return asn1Bytes;
        }
    }

    public boolean isUsingIterator() {
        return usingIterator;
    }

    public void setUsingIterator(boolean usingIterator) {
        this.usingIterator = usingIterator;
    }

    public String getClazzName() {
        return clazzName;
    }

    public void setClazzName(String clazzName) {
        this.clazzName = clazzName;
    }

    @Override
    protected void doStart() throws Exception {
        // no op
    }

    @Override
    protected void doStop() throws Exception {
        // no op
    }

}
