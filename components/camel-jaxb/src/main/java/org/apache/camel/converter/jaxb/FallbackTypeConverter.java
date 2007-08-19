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
package org.apache.camel.converter.jaxb;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.util.JAXBSource;
import javax.xml.transform.Source;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * @version $Revision: 1.1 $
 */
public class FallbackTypeConverter implements TypeConverter, TypeConverterAware {
    private static final transient Log LOG = LogFactory.getLog(FallbackTypeConverter.class);
    private TypeConverter parentTypeConverter;
    private boolean prettyPrint = true;

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public void setTypeConverter(TypeConverter parentTypeConverter) {
        this.parentTypeConverter = parentTypeConverter;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        try {
            if (isJaxbType(type)) {
                return unmarshall(type, value);
            }
            if (value != null) {
                if (isJaxbType(value.getClass())) {
                    return marshall(type, value);
                }
            }
            return null;
        }
        catch (JAXBException e) {
            throw new RuntimeCamelException(e);
        }
    }

    protected <T> boolean isJaxbType(Class<T> type) {
        XmlRootElement element = type.getAnnotation(XmlRootElement.class);
        boolean jaxbType = element != null;
        return jaxbType;
    }

    /**
     * Lets try parse via JAXB
     */
    protected <T> T unmarshall(Class<T> type, Object value) throws JAXBException {
        JAXBContext context = createContext(type);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        if (parentTypeConverter != null) {
            InputStream inputStream = parentTypeConverter.convertTo(InputStream.class, value);
            if (inputStream != null) {
                Object unmarshalled = unmarshaller.unmarshal(inputStream);
                return type.cast(unmarshalled);
            }
            Reader reader = parentTypeConverter.convertTo(Reader.class, value);
            if (reader != null) {
                Object unmarshalled = unmarshaller.unmarshal(reader);
                return type.cast(unmarshalled);
            }
            Source source = parentTypeConverter.convertTo(Source.class, value);
            if (source != null) {
                Object unmarshalled = unmarshaller.unmarshal(source);
                return type.cast(unmarshalled);
            }
        }
        if (value instanceof String) {
            value = new StringReader((String) value);
        }
        if (value instanceof InputStream) {
            Object unmarshalled = unmarshaller.unmarshal((InputStream) value);
            return type.cast(unmarshalled);
        }
        if (value instanceof Reader) {
            Object unmarshalled = unmarshaller.unmarshal((Reader) value);
            return type.cast(unmarshalled);
        }
        return null;
    }

    protected <T> T marshall(Class<T> type, Object value) throws JAXBException {
        if (parentTypeConverter != null) {
            // lets convert the object to a JAXB source and try convert that to
            // the required source
            JAXBContext context = createContext(value.getClass());
            JAXBSource source = new JAXBSource(context, value);
            T answer = parentTypeConverter.convertTo(type, source);
            if (answer == null) {
                // lets try a stream
                StringWriter buffer = new StringWriter();
                Marshaller marshaller = context.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, isPrettyPrint() ? Boolean.TRUE : Boolean.FALSE);
                marshaller.marshal(value, buffer);
                return parentTypeConverter.convertTo(type, buffer.toString());
            }
            return answer;
        }

        // lets try convert to the type from JAXB
        return null;
    }

    protected <T> JAXBContext createContext(Class<T> type) throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(type);
        return context;
    }
}
