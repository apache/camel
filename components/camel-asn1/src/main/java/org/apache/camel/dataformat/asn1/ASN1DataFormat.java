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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import com.beanit.asn1bean.ber.ReverseByteArrayOutputStream;
import org.apache.camel.Exchange;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;

@Dataformat("asn1")
@BodyTypes(marshalIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, dependsOn = "unmarshalType",
                                 byOption = @BodyType.When(option = "usingIterator", value = "true",
                                                           carriers = { Carrier.BYTES, Carrier.POJO }),
                                 description = "BER-encoded data, written as it is. With usingIterator, a byte array or a"
                                               + " Bouncy Castle ASN1Primitive, and with unmarshalType as well an object of a"
                                               + " class the asn1bean compiler generated"),
           marshalOut = @BodyType(value = Carrier.STREAM, format = "binary",
                                  description = "The BER-encoded data in the stream cache, or a byte array when stream caching"
                                                + " is off. With usingIterator, and no unmarshalType, nothing is written for a"
                                                + " body that is neither a byte array nor an ASN1Primitive"),
           unmarshalIn = @BodyType(value = { Carrier.BYTES, Carrier.STREAM, Carrier.FILE }, format = "binary",
                                   description = "BER-encoded data: one record, or several one after the other"),
           unmarshalOut = @BodyType(value = Carrier.BYTES,
                                    byOption = @BodyType.When(option = "usingIterator", value = "true",
                                                              carriers = Carrier.ITERATOR),
                                    description = "The records, encoded again, in one byte array. With usingIterator, an"
                                                  + " Iterator for the split EIP: of messages with the bytes of one record"
                                                  + " each, or of objects of the class unmarshalType names"))
public class ASN1DataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private boolean usingIterator;
    private Class<?> unmarshalType;

    public ASN1DataFormat() {
        this.usingIterator = false;
    }

    public ASN1DataFormat(Class<?> unmarshalType) {
        this.usingIterator = true;
        this.unmarshalType = unmarshalType;
    }

    @Override
    public String getDataFormatName() {
        return "asn1";
    }

    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        InputStream berOut = null;
        if (usingIterator) {
            if (unmarshalType != null) {
                encodeGenericTypeObject(exchange, stream);
                return;
            }
            Object body = exchange.getIn().getBody();
            if (body instanceof ASN1Primitive) {
                ASN1Primitive asn1Primitive = ObjectHelper.cast(ASN1Primitive.class, body);
                berOut = new ByteArrayInputStream(asn1Primitive.getEncoded());
            } else if (body instanceof byte[]) {
                berOut = new ByteArrayInputStream(ObjectHelper.cast(byte[].class, body));
            }
        } else {
            byte[] byteInput = exchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, exchange, graph);
            berOut = new ByteArrayInputStream(byteInput);
        }
        try {
            if (berOut != null) {
                IOHelper.copy(berOut, stream);
            }
        } finally {
            IOHelper.close(berOut, stream);
        }
    }

    private void encodeGenericTypeObject(Exchange exchange, OutputStream stream) throws Exception {
        Class<?>[] paramOut = new Class<?>[1];
        paramOut[0] = OutputStream.class;
        try (ReverseByteArrayOutputStream berOut = new ReverseByteArrayOutputStream(IOHelper.DEFAULT_BUFFER_SIZE / 256, true)) {
            Method encodeMethod = exchange.getIn().getBody().getClass().getDeclaredMethod("encode", paramOut);
            encodeMethod.invoke(exchange.getIn().getBody(), berOut);
            stream.write(berOut.getArray());
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        if (usingIterator) {
            if (unmarshalType != null) {
                return new ASN1GenericIterator(unmarshalType, stream);
            }
            return new ASN1MessageIterator(exchange, stream);
        } else {
            ASN1Primitive asn1Record;
            byte[] asn1Bytes;
            try (ASN1InputStream ais = new ASN1InputStream(stream);
                 ByteArrayOutputStream asn1Out = new ByteArrayOutputStream();) {
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

    public Class<?> getUnmarshalType() {
        return unmarshalType;
    }

    public void setUnmarshalType(Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
    }

}
