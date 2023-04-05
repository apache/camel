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
package org.apache.camel.dataformat.soap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.JAXBIntrospector;

import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.dataformat.soap.name.ElementNameStrategy;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;
import org.apache.camel.dataformat.soap.name.TypeNameStrategy;
import org.apache.camel.spi.annotations.Dataformat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data format supporting SOAP 1.1 and 1.2.
 */
@Dataformat("soap")
public class SoapDataFormat extends JaxbDataFormat {

    public static final String SOAP_UNMARSHALLED_HEADER_LIST = "org.apache.camel.dataformat.soap.UNMARSHALLED_HEADER_LIST";

    private static final Logger LOG = LoggerFactory.getLogger(SoapDataFormat.class);

    private SoapDataFormatAdapter adapter;
    private ElementNameStrategy elementNameStrategy = new TypeNameStrategy();
    private boolean ignoreUnmarshalledHeaders;
    private String version;

    /**
     * Remember to set the context path when using this constructor
     */
    public SoapDataFormat() {
    }

    /**
     * Initialize with JAXB context path
     */
    public SoapDataFormat(String contextPath) {
        super(contextPath);
    }

    /**
     * Initialize the data format. The serviceInterface is necessary to determine the element name and namespace of the
     * element inside the soap body when marshalling
     */
    public SoapDataFormat(String contextPath, ElementNameStrategy elementNameStrategy) {
        this(contextPath);
        this.elementNameStrategy = elementNameStrategy;
    }

    @Override
    public String getDataFormatName() {
        return "soap";
    }

    @Override
    protected void doStart() throws Exception {
        if ("1.2".equals(version)) {
            LOG.debug("Using SOAP 1.2 adapter");
            adapter = new Soap12DataFormatAdapter(this);
        } else {
            LOG.debug("Using SOAP 1.1 adapter");
            adapter = new Soap11DataFormatAdapter(this);
        }
        super.doStart();
    }

    /**
     * Marshal inputObjects to SOAP xml. If the exchange or message has an EXCEPTION_CAUGTH property or header then
     * instead of the object the exception is marshaled.
     *
     * To determine the name of the top level xml elements the elementNameStrategy is used.
     */
    @Override
    public void marshal(Exchange exchange, Object inputObject, OutputStream stream) throws IOException {
        String soapAction = getSoapActionFromExchange(exchange);
        Object envelope = adapter.doMarshal(exchange, inputObject, stream, soapAction);

        // and continue in super
        super.marshal(exchange, envelope, stream);
    }

    /**
     * Create body content from a non Exception object. So the interface should be in doc lit bare style.
     *
     * @param  inputObject object to be put into the SOAP body
     * @param  soapAction  for name resolution
     * @return             JAXBElement for the body content
     */
    protected List<Object> createContentFromObject(
            final Object inputObject, String soapAction) {
        List<Object> bodyParts = new ArrayList<>();
        bodyParts.add(inputObject);

        List<Object> bodyElements = new ArrayList<>();
        for (Object bodyObj : bodyParts) {
            QName name = elementNameStrategy.findQNameForSoapActionOrType(soapAction, bodyObj.getClass());
            if (name == null) {
                LOG.warn("Could not find QName for class {}", bodyObj.getClass().getName());
            } else {
                bodyElements.add(getElement(bodyObj, name));
            }
        }

        return bodyElements;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private JAXBElement<?> getElement(Object fromObj, QName name) {

        Object value = null;

        // In the case of a parameter, the class of the value of the holder class
        // is used for the mapping rather than the holder class itself.

        if (fromObj instanceof jakarta.xml.ws.Holder) {
            jakarta.xml.ws.Holder holder = (jakarta.xml.ws.Holder) fromObj;
            value = holder.value;
            if (null == value) {
                return null;
            }
        } else {
            value = fromObj;
        }

        return new JAXBElement(name, value.getClass(), value);
    }

    /**
     * Unmarshal a given SOAP xml stream and return the content of the SOAP body
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        String soapAction = getSoapActionFromExchange(exchange);

        // Determine the method name for an eventual BeanProcessor in the route
        if (soapAction != null && elementNameStrategy instanceof ServiceInterfaceStrategy) {
            ServiceInterfaceStrategy strategy = (ServiceInterfaceStrategy) elementNameStrategy;
            String methodName = strategy.getMethodForSoapAction(soapAction);
            exchange.getOut().setHeader(Exchange.BEAN_METHOD_NAME, methodName);
        }

        // Store soap action for an eventual later marshal step.
        // This is necessary as the soap action in the message may get lost on the way
        if (soapAction != null) {
            exchange.setProperty(Exchange.SOAP_ACTION, soapAction);
        }

        Object unmarshalledObject = super.unmarshal(exchange, stream);
        Object rootObject = JAXBIntrospector.getValue(unmarshalledObject);

        return adapter.doUnmarshal(exchange, stream, rootObject);
    }

    private String getSoapActionFromExchange(Exchange exchange) {
        Message inMessage = exchange.getIn();
        String soapAction = inMessage.getHeader(Exchange.SOAP_ACTION, String.class);
        if (soapAction == null) {
            soapAction = inMessage.getHeader("SOAPAction", String.class);
            if (soapAction != null && soapAction.startsWith("\"")) {
                soapAction = soapAction.substring(1, soapAction.length() - 1);
            }
        }
        if (soapAction == null) {
            soapAction = exchange.getProperty(Exchange.SOAP_ACTION, String.class);
        }
        return soapAction;
    }

    /**
     * Added the generated SOAP package to the JAXB context so Soap datatypes are available
     */
    @Override
    protected JAXBContext createContext() throws JAXBException {
        if (getContextPath() != null) {
            return JAXBContext.newInstance(adapter.getSoapPackageName() + ":" + getContextPath());
        } else {
            return JAXBContext.newInstance();
        }
    }

    public ElementNameStrategy getElementNameStrategy() {
        return elementNameStrategy;
    }

    public void setElementNameStrategy(Object nameStrategy) {
        if (nameStrategy != null) {
            if (nameStrategy instanceof ElementNameStrategy) {
                this.elementNameStrategy = (ElementNameStrategy) nameStrategy;
            } else {
                throw new IllegalArgumentException(
                        "The argument for setElementNameStrategy should be subClass of "
                                                   + ElementNameStrategy.class.getName());
            }
        }
    }

    public boolean isIgnoreUnmarshalledHeaders() {
        return ignoreUnmarshalledHeaders;
    }

    public void setIgnoreUnmarshalledHeaders(boolean ignoreUnmarshalledHeaders) {
        this.ignoreUnmarshalledHeaders = ignoreUnmarshalledHeaders;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

}
