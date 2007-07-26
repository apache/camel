/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Reader;

/**
 * @version $Revision: 1.1 $
 */
public class FallbackTypeConverter implements TypeConverter {
    private static final transient Log log = LogFactory.getLog(FallbackTypeConverter.class);

    public <T> T convertTo(Class<T> type, Object value) {
        log.debug("Investigating JAXB type conversions");

        try {
            XmlRootElement element = type.getAnnotation(XmlRootElement.class);
            if (element != null) {
                return unmarshall(type, value);
            }
            return null;
        }
        catch (JAXBException e) {
            throw new RuntimeCamelException(e);
        }
    }

    protected <T> T unmarshall(Class<T> type, Object value) throws JAXBException {
        // lets try parse via JAXB2
        JAXBContext context = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = context.createUnmarshaller();

        // TODO use the type converters here
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
}
