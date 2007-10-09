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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Binding;
import org.apache.camel.spi.Marshaller;
import org.apache.camel.spi.Unmarshaller;

/**
 * @version $Revision: 1.1 $
 */
public class JaxbBinding implements Binding {
    private JAXBContext context;
    private boolean prettyPrint = true;

    public JaxbBinding() {
    }

    public JaxbBinding(JAXBContext context) {
        this.context = context;
    }

    public Marshaller createMarshaller() {
        try {
            javax.xml.bind.Marshaller marshaller = getContext().createMarshaller();
            marshaller.setProperty(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, prettyPrint ? Boolean.TRUE : Boolean.FALSE);
            return new JaxbMarshaller(marshaller);
        }
        catch (JAXBException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public Unmarshaller createUnmarshaller() {
        try {
            javax.xml.bind.Unmarshaller unmarshaller = getContext().createUnmarshaller();
            return new JaxbUnmarshaller(unmarshaller);
        }
        catch (JAXBException e) {
            throw new RuntimeCamelException(e);
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

    protected JAXBContext createContext() throws JAXBException {
        return JAXBContext.newInstance();
    }

}
