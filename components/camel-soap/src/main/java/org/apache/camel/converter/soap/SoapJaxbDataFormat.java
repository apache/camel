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
package org.apache.camel.converter.soap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.namespace.QName;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.converter.soap.name.ElementNameStrategy;
import org.apache.camel.converter.soap.name.ExceptionNameStrategy;
import org.apache.camel.converter.soap.name.TypeNameStrategy;
import org.apache.camel.spi.ClassResolver;
import org.xmlsoap.schemas.soap.envelope.Body;
import org.xmlsoap.schemas.soap.envelope.Detail;
import org.xmlsoap.schemas.soap.envelope.Envelope;
import org.xmlsoap.schemas.soap.envelope.Fault;
import org.xmlsoap.schemas.soap.envelope.ObjectFactory;

/**
 * Marshaling from Objects to SOAP and back by using JAXB. The classes to be
 * processed need to have JAXB annotations. For marshaling a
 * ElementNameStrategy is used to determine how the top level elements in SOAP
 * are named as this can not be extracted from JAXB.
 */
public class SoapJaxbDataFormat extends JaxbDataFormat {
    private static final String SOAP_PACKAGE_NAME = Envelope.class.getPackage().getName();

    private ElementNameStrategy elementNameStrategy;

    /**
     * Remember to set the context path when using this constructor
     */
    public SoapJaxbDataFormat() {
        super();
    }

    /**
     * Initialize with JAXB context path
     * 
     * @param contexPath
     */
    public SoapJaxbDataFormat(String contexPath) {
        super(contexPath);
    }

    /**
     * Initialize the data format. The serviceInterface is necessary to
     * determine the element name and namespace of the element inside the soap
     * body when marshaling
     * 
     * @param jaxbPackage
     *            package for JAXB context
     * @param serviceInterface
     *            webservice interface
     */
    public SoapJaxbDataFormat(String contextPath, ElementNameStrategy elementNameStrategy) {
        this(contextPath);
        this.elementNameStrategy = elementNameStrategy;
    }

    /**
     * Marshal inputObject to SOAP xml. If the exchange or message has an EXCEPTION_CAUGTH
     * property or header then instead of the object the exception is marshaled.
     * 
     * To determine the name of the top level xml elment the elementNameStrategy is used.
     */
    public void marshal(Exchange exchange, final Object inputObject, OutputStream stream) throws IOException {
        if (elementNameStrategy == null) {
            elementNameStrategy = new TypeNameStrategy();
        }

        String soapAction = (String) exchange.getProperty(Exchange.SOAP_ACTION);
        Body body = new Body();
        ClassResolver classResolver = exchange.getContext().getClassResolver();

        Throwable exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        if (exception == null) {
            exception = exchange.getIn().getHeader(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        final JAXBElement<?> content;
        if (exception != null) {
            content = createFaultFromException(exception, soapAction, classResolver);
        } else {
            content = createBodyContentFromObject(inputObject, soapAction, classResolver);
        }
        body.getAny().add(content);
        Envelope envelope = new Envelope();
        envelope.setBody(body);
        JAXBElement<Envelope> envelopeEl = new ObjectFactory().createEnvelope(envelope);
        super.marshal(exchange, envelopeEl, stream);
    }

    /**
     * Create body content from a non Exception object. If the inputObject is a
     * BeanInvocation the following should be considered: The first parameter
     * will be used for the SOAP body. BeanInvocations with more than one
     * parameter are not supported. So the interface should be in doc lit bare
     * style.
     * 
     * @param inputObject object to be put into the SOAP body
     * @param soapAction for name resolution
     * @param classResolver for name resolution
     * @return JAXBElement for the body content
     */
    @SuppressWarnings("unchecked")
    private JAXBElement<?> createBodyContentFromObject(final Object inputObject, String soapAction,
            ClassResolver classResolver) {
        Object graph;
        if (inputObject instanceof BeanInvocation) {
            BeanInvocation bi = (BeanInvocation) inputObject;
            if (bi.getArgs().length > 1) {
                throw new RuntimeCamelException(
                        "SoapDataFormat does not work with Beaninvocations that contain more than 1 parameter");
            }
            graph = (bi.getArgs().length == 1) ? bi.getArgs()[0] : null;
        } else {
            graph = inputObject;
        }
        QName name = elementNameStrategy.findQNameForSoapActionOrType(soapAction, graph.getClass(), classResolver);
        return new JAXBElement(name, graph.getClass(), graph);
    }

    /**
     * Creates a SOAP fault from the exception and populates the message as well
     * as the detail. The detail object is read from the method getFaultInfo of
     * the throwable if present
     * 
     * @param exception
     * @param soapAction
     * @param classResolver
     * @return SOAP fault from given Throwable
     */
    @SuppressWarnings("unchecked")
    private JAXBElement<Fault> createFaultFromException(final Throwable exception, String soapAction,
            ClassResolver classResolver) {
        QName name = new ExceptionNameStrategy().findQNameForSoapActionOrType(soapAction, exception.getClass(),
                classResolver);
        Object faultObject = null;
        try {
            Method method = exception.getClass().getMethod("getFaultInfo");
            faultObject = method.invoke(exception);
        } catch (Exception e) {
            throw new RuntimeCamelException("Exception while trying to get fault details", e);
        }
        Fault fault = new Fault();
        fault
                .setFaultcode(new QName(exception.getClass().getPackage().getName(), exception.getClass()
                        .getSimpleName()));
        fault.setFaultstring(exception.getMessage());
        Detail detailEl = new ObjectFactory().createDetail();
        JAXBElement<?> faultDetailContent = new JAXBElement(name, faultObject.getClass(), faultObject);
        detailEl.getAny().add(faultDetailContent);
        fault.setDetail(detailEl);
        return new ObjectFactory().createFault(fault);
    }

    /**
     * Unmarshal a given SOAP xml stream and return the content of the SOAP body
     */
    public Object unmarshal(Exchange exchange, InputStream stream) throws IOException {
        Object rootObject = JAXBIntrospector.getValue(super.unmarshal(exchange, stream));
        if (rootObject.getClass() != Envelope.class) {
            throw new RuntimeCamelException("Expected Soap Envelope but got " + rootObject.getClass());
        }
        Envelope envelope = (Envelope) rootObject;
        Object payloadEl = envelope.getBody().getAny().get(0);
        return (isIgnoreJAXBElement()) ? JAXBIntrospector.getValue(payloadEl) : payloadEl;
    }

    /**
     * Added the generated SOAP package to the JAXB context so Soap datatypes
     * are available
     */
    @Override
    protected JAXBContext createContext() throws JAXBException {
        if (getContextPath() != null) {
            return JAXBContext.newInstance(SOAP_PACKAGE_NAME + ":" + getContextPath());
        } else {
            return JAXBContext.newInstance();
        }
    }

    public void setElementNameStrategy(ElementNameStrategy elementNameStrategy) {
        this.elementNameStrategy = elementNameStrategy;
    }

}
