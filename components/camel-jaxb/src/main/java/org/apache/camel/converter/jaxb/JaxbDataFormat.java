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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.IOHelper;

/**
 * A <a href="http://camel.apache.org/data-format.html">data format</a> ({@link DataFormat})
 * using JAXB2 to marshal to and from XML
 *
 * @version $Revision$
 */
public class JaxbDataFormat implements DataFormat {
    private JAXBContext context;
    private String contextPath;
    private boolean prettyPrint = true;
    private boolean ignoreJAXBElement = true;

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
            // must create a new instance of marshaller as its not thred safe
            getContext().createMarshaller().marshal(graph, stream);
        } catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException, ClassNotFoundException {
        try {
            // must create a new instance of unmarshaller as its not thred safe
            Object answer = getContext().createUnmarshaller().unmarshal(stream);
            if (answer instanceof JAXBElement && isIgnoreJAXBElement()) {
                answer = ((JAXBElement)answer).getValue();
            }
            return answer;
        } catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }    

    // Properties
    // -------------------------------------------------------------------------
    public boolean isIgnoreJAXBElement() {        
        return ignoreJAXBElement;
    }
    
    public void setIgnoreJAXBElement(boolean flag) {
        ignoreJAXBElement = flag;
    }
    
    public synchronized JAXBContext getContext() throws JAXBException {
        if (context == null) {
            context = createContext();
        }
        return context;
    }

    public void setContext(JAXBContext context) {
        this.context = context;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    protected JAXBContext createContext() throws JAXBException {
        if (contextPath != null) {
            return JAXBContext.newInstance(contextPath);
        } else {
            return JAXBContext.newInstance();
        }
    }
}
