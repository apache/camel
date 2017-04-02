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
package org.apache.camel.dataformat.soap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBIntrospector;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.ws.WebFault;
import javax.xml.ws.soap.SOAPFaultException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.xmlsoap.schemas.soap.envelope.Body;
import org.xmlsoap.schemas.soap.envelope.Detail;
import org.xmlsoap.schemas.soap.envelope.Envelope;
import org.xmlsoap.schemas.soap.envelope.Fault;
import org.xmlsoap.schemas.soap.envelope.Header;
import org.xmlsoap.schemas.soap.envelope.ObjectFactory;

/**
 * Marshaling from Objects to <b>SOAP 1.1</b> and back by using JAXB. The classes to be
 * processed need to have JAXB annotations. For marshaling a ElementNameStrategy
 * is used to determine how the top level elements in SOAP are named as this can
 * not be extracted from JAXB.
 */
public class Soap11DataFormatAdapter implements SoapDataFormatAdapter {

    private static final String SOAP_PACKAGE_NAME = Envelope.class.getPackage().getName();
    private static final QName FAULT_CODE_SERVER = new QName("http://schemas.xmlsoap.org/soap/envelope/", "Receiver");

    private final SoapJaxbDataFormat dataFormat;
    private final ObjectFactory objectFactory;

    public Soap11DataFormatAdapter(SoapJaxbDataFormat dataFormat) {
        this.dataFormat = dataFormat;
        this.objectFactory = new ObjectFactory();
    }

    public SoapJaxbDataFormat getDataFormat() {
        return dataFormat;
    }

    @Override
    public Object doMarshal(Exchange exchange, Object inputObject, OutputStream stream, String soapAction) throws IOException {
        Body body = objectFactory.createBody();
        Header header = objectFactory.createHeader();

        Throwable exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        if (exception == null) {
            exception = exchange.getIn().getHeader(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }

        final List<Object> bodyContent;
        List<Object> headerContent = new ArrayList<Object>();
        if (exception != null) {
            bodyContent = new ArrayList<Object>();
            bodyContent.add(createFaultFromException(exception));
        } else {
            if (!dataFormat.isIgnoreUnmarshalledHeaders()) {
                List<Object> inboundSoapHeaders = (List<Object>) exchange.getIn().getHeader(SoapJaxbDataFormat.SOAP_UNMARSHALLED_HEADER_LIST);
                if (null != inboundSoapHeaders) {
                    headerContent.addAll(inboundSoapHeaders);
                }
            }
            bodyContent = getDataFormat().createContentFromObject(inputObject, soapAction, headerContent);
        }

        for (Object elem : bodyContent) {
            body.getAny().add(elem);
        }
        for (Object elem : headerContent) {
            header.getAny().add(elem);
        }
        Envelope envelope = new Envelope();
        if (headerContent.size() > 0) {
            envelope.setHeader(header);
        }
        envelope.setBody(body);
        JAXBElement<Envelope> envelopeEl = objectFactory.createEnvelope(envelope);
        return envelopeEl;
    }

    /**
     * Creates a SOAP fault from the exception and populates the message as well
     * as the detail. The detail object is read from the method getFaultInfo of
     * the throwable if present
     * 
     * @param exception the cause exception
     * @return SOAP fault from given Throwable
     */
    @SuppressWarnings("unchecked")
    private JAXBElement<Fault> createFaultFromException(final Throwable exception) {
        WebFault webFault = exception.getClass().getAnnotation(WebFault.class);
        if (webFault == null || webFault.targetNamespace() == null) {
            throw new RuntimeException("The exception " + exception.getClass().getName()
                    + " needs to have an WebFault annotation with name and targetNamespace", exception);
        }
        QName name = new QName(webFault.targetNamespace(), webFault.name());
        Object faultObject;
        try {
            Method method = exception.getClass().getMethod("getFaultInfo");
            faultObject = method.invoke(exception);
        } catch (Exception e) {
            throw new RuntimeCamelException("Exception while trying to get fault details", e);
        }
        Fault fault = new Fault();
        fault.setFaultcode(FAULT_CODE_SERVER);
        fault.setFaultstring(exception.getMessage());
        Detail detailEl = new ObjectFactory().createDetail();
        @SuppressWarnings("rawtypes")
        JAXBElement<?> faultDetailContent = new JAXBElement(name, faultObject.getClass(), faultObject);
        detailEl.getAny().add(faultDetailContent);
        fault.setDetail(detailEl);
        return new ObjectFactory().createFault(fault);
    }

    @Override
    public Object doUnmarshal(Exchange exchange, InputStream stream, Object rootObject) throws IOException {
        if (rootObject.getClass() != Envelope.class) {
            throw new RuntimeCamelException("Expected Soap Envelope but got " + rootObject.getClass());
        }
        Envelope envelope = (Envelope) rootObject;

        Header header = envelope.getHeader();
        if (header != null) {
            List<Object> returnHeaders;
            List<Object> anyHeaderElements = envelope.getHeader().getAny();
            if (null != anyHeaderElements && !(getDataFormat().isIgnoreUnmarshalledHeaders())) {
                if (getDataFormat().isIgnoreJAXBElement()) {
                    returnHeaders = new ArrayList<Object>();
                    for (Object headerEl : anyHeaderElements) {
                        returnHeaders.add(JAXBIntrospector.getValue(headerEl));
                    }
                } else {
                    returnHeaders = anyHeaderElements;
                }
                exchange.getOut().setHeader(SoapJaxbDataFormat.SOAP_UNMARSHALLED_HEADER_LIST, returnHeaders);
            }
        }

        List<Object> anyElement = envelope.getBody().getAny();
        if (anyElement.size() == 0) {
            // No parameter so return null
            return null;

        }
        Object payloadEl = anyElement.get(0);
        Object payload = JAXBIntrospector.getValue(payloadEl);
        if (payload instanceof Fault) {
            Exception exception = createExceptionFromFault((Fault) payload);
            exchange.setException(exception);
            return null;
        } else {
            return getDataFormat().isIgnoreJAXBElement() ? payload : payloadEl;
        }
    }

    /**
     * Creates an exception and eventually an embedded bean that contains the
     * fault detail. The exception class is determined by using the
     * elementNameStrategy. The qName of the fault detail should match the
     * WebFault annotation of the Exception class. If no fault detail is set a
     * SOAPFaultException is created.
     * 
     * @param fault Soap fault
     * @return created Exception
     */
    private Exception createExceptionFromFault(Fault fault) {
        String message = fault.getFaultstring();

        Detail faultDetail = fault.getDetail();
        if (faultDetail == null || faultDetail.getAny().size() == 0) {
            try {
                return new SOAPFaultException(SOAPFactory.newInstance().createFault(message, fault.getFaultcode()));
            } catch (SOAPException e) {
                throw new RuntimeCamelException(e);
            }
        }

        Object detailObj = faultDetail.getAny().get(0);

        if (!(detailObj instanceof JAXBElement)) {
            try {
                return new SOAPFaultException(SOAPFactory.newInstance().createFault(message, fault.getFaultcode()));
            } catch (SOAPException e) {
                throw new RuntimeCamelException(e);
            }
        }

        JAXBElement<?> detailEl = (JAXBElement<?>) detailObj;
        Class<? extends Exception> exceptionClass = getDataFormat().getElementNameStrategy().findExceptionForFaultName(detailEl.getName());
        Constructor<? extends Exception> messageConstructor;
        Constructor<? extends Exception> constructor;

        try {
            Object detail = JAXBIntrospector.getValue(detailEl);
            try {
                constructor = exceptionClass.getConstructor(String.class, detail.getClass());
                return constructor.newInstance(message, detail);
            } catch (NoSuchMethodException e) {
                messageConstructor = exceptionClass.getConstructor(String.class);
                return messageConstructor.newInstance(message);
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public String getSoapPackageName() {
        return SOAP_PACKAGE_NAME;
    }

}

