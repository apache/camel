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
package org.apache.camel.component.cxf.converter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;

import javax.xml.namespace.QName;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.component.cxf.common.DataFormat;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ExchangeHelper;
import org.apache.cxf.message.MessageContentsList;

import static org.apache.camel.TypeConverter.MISS_VALUE;

/**
 * The <a href="http://camel.apache.org/type-converter.html">Type Converters</a> for CXF related types' converting .
 */
@Converter(generateLoader = true)
public final class CxfConverter {

    private CxfConverter() {
        // Helper class
    }

    @Converter
    public static MessageContentsList toMessageContentsList(final Object[] array) {
        if (array != null) {
            return new MessageContentsList(array);
        } else {
            return new MessageContentsList();
        }
    }

    @Converter
    public static QName toQName(String qname) {
        return QName.valueOf(qname);
    }

    @Converter
    public static Object[] toArray(Object object) {
        if (object instanceof Collection) {
            return ((Collection<?>) object).toArray();
        } else {
            Object[] answer;
            if (object == null) {
                answer = new Object[0];
            } else {
                answer = new Object[1];
                answer[0] = object;
            }
            return answer;
        }
    }

    @Converter
    public static String soapMessageToString(final SOAPMessage soapMessage, Exchange exchange)
            throws SOAPException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        soapMessage.writeTo(baos);
        return baos.toString(ExchangeHelper.getCharsetName(exchange));
    }

    @Converter
    public static InputStream soapMessageToInputStream(final SOAPMessage soapMessage, Exchange exchange)
            throws SOAPException, IOException {
        CachedOutputStream cos = new CachedOutputStream(exchange);
        soapMessage.writeTo(cos);
        return cos.getInputStream();
    }

    @Converter
    public static DataFormat toDataFormat(final String name) {
        return DataFormat.valueOf(name.toUpperCase());
    }

    /**
     * Use a fallback type converter so we can convert the embedded list element if the value is MessageContentsList.
     * The algorithm of this converter finds the first non-null list element from the list and applies conversion to the
     * list element if can determine this MessageContentsList is used in CXF context(first element is the return value
     * while others are Holders).
     *
     * @param  type     the desired type to be converted to
     * @param  exchange optional exchange which can be null
     * @param  value    the object to be converted
     * @param  registry type converter registry
     * @return          the converted value of the desired type or null if no suitable converter found
     */
    @SuppressWarnings("unchecked")
    @Converter(fallback = true)
    public static <T> T convertTo(
            Class<T> type, Exchange exchange, Object value,
            TypeConverterRegistry registry) {

        // CXF-WS MessageContentsList class
        if (MessageContentsList.class.isAssignableFrom(value.getClass())) {
            MessageContentsList list = (MessageContentsList) value;

            if (list.size() > 1 && type == String.class) {
                //to check if the MessageContentsList is used in CXF context
                //If not, use the general way to convert from List.class to String.class
                boolean foundHolder = false;
                for (Object embedded : list) {
                    if (embedded != null && embedded.getClass().getName().equals("javax.xml.ws.Holder")) {
                        foundHolder = true;
                        break;
                    }
                }
                if (!foundHolder) {
                    // this isn't a typical CXF MessageContentsList
                    // just using other fallback converters
                    return null;
                }
            }

            // try to turn the first array element into the object that we want
            for (Object embedded : list) {
                if (embedded != null) {
                    if (type.isInstance(embedded)) {
                        return type.cast(embedded);
                    } else {
                        TypeConverter tc = registry.lookup(type, embedded.getClass());
                        if (tc == null) {
                            // maybe one of its interface fits
                            for (Class<?> clazz : embedded.getClass().getInterfaces()) {
                                tc = registry.lookup(type, clazz);
                                if (tc != null) {
                                    break;
                                }
                            }
                        }
                        if (tc != null) {
                            Object result = tc.convertTo(type, exchange, embedded);
                            if (result != null) {
                                return (T) result;
                            }
                            // there is no suitable result will be return
                            break;
                        }
                    }
                }
            }
            // return void to indicate its not possible to convert at this time
            return (T) MISS_VALUE;
        }

        return null;
    }
}
