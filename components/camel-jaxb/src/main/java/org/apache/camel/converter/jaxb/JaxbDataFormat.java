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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;

/**
 * A <a href="http://activemq.apache.org/camel/data-format.html">data format</a>
 * ({@link DataFormat}) using JAXB2 to marshal to and from XML
 *
 * @version $Revision: 1.1 $
 */
public class JaxbDataFormat implements DataFormat {
    private JAXBContext context;
    private String contextPath;
    private boolean prettyPrint = true;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;

    public JaxbDataFormat() {
    }

    public JaxbDataFormat(JAXBContext context) {
        this.context = context;
    }

    public JaxbDataFormat(String contextPath) {
        this.contextPath = contextPath;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws IOException {
        try {
            getMarshaller().marshal(graph, stream);
        }
        catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException, ClassNotFoundException {
        try {
            return getUnmarshaller().unmarshal(stream);
        }
        catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public JAXBContext getContext() throws JAXBException {
        if (context == null) {
            context = createContext();
        }
        return context;
    }

    public void setContext(JAXBContext context) {
        this.context = context;
    }

    public Marshaller getMarshaller() throws JAXBException {
        if (marshaller == null) {
            marshaller = getContext().createMarshaller();
        }
        return marshaller;
    }

    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public Unmarshaller getUnmarshaller() throws JAXBException {
        if (unmarshaller == null) {
            unmarshaller = getContext().createUnmarshaller();
        }
        return unmarshaller;
    }

    public void setUnmarshaller(Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }

    protected JAXBContext createContext() throws JAXBException {
        if (contextPath != null) {
            return JAXBContext.newInstance(contextPath);
        }
        else {
            return JAXBContext.newInstance();
        }
    }
}
