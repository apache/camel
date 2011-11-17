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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamException;

import org.apache.camel.Exchange;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.isA;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JaxbDataFormatTest {
    private JaxbDataFormat jaxbDataFormat;
    @Mock
    private Exchange exchangeMock;
    @Mock
    private Marshaller marshallerMock;
    @Mock
    private JaxbDataFormat jaxbDataFormatMock;
    @Mock
    private JAXBContext jaxbContextMock;
    @Mock
    private Unmarshaller unmarshallerMock;


    @Before
    public void setUp() {
        jaxbDataFormat = new JaxbDataFormat();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNeedFilteringDisabledFiltering() {
        // tests combinations of data format option and exchange property
        when(exchangeMock.getProperty(anyString(), anyObject(), any(Class.class)))
                .thenReturn(false);

        jaxbDataFormat.needFiltering(exchangeMock);
        verify(exchangeMock).getProperty(Exchange.FILTER_NON_XML_CHARS, false, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNeedFilteringEnabledFiltering() {
        when(exchangeMock.getProperty(anyString(), anyObject(), any(Class.class))).thenReturn(true);
        jaxbDataFormat.setFilterNonXmlChars(true);
        jaxbDataFormat.needFiltering(exchangeMock);
        verify(exchangeMock).getProperty(Exchange.FILTER_NON_XML_CHARS, true, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNeedFilteringTruePropagates() {
        // tests combinations of data format option and exchange property
        when(exchangeMock.getProperty(anyString(), anyObject(), any(Class.class)))
                .thenReturn(true);

        assertTrue("Expecting filtering here", jaxbDataFormat.needFiltering(exchangeMock));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testNeedFilteringFalsePropagates() {
        // tests combinations of data format option and exchange property
        when(exchangeMock.getProperty(anyString(), anyObject(), any(Class.class)))
                .thenReturn(false);

        assertFalse("Not expecting filtering here", jaxbDataFormat.needFiltering(exchangeMock));
    }

    @Test
    public void testMarshalFilteringDisabled() throws IOException, XMLStreamException, JAXBException {
        doCallRealMethod().when(jaxbDataFormatMock).marshal(any(Exchange.class), anyObject(),
                any(OutputStream.class), any(Marshaller.class));
        when(jaxbDataFormatMock.needFiltering(exchangeMock)).thenReturn(false);

        Object graph = new Object();
        OutputStream stream = new ByteArrayOutputStream();

        jaxbDataFormatMock.marshal(exchangeMock, graph, stream, marshallerMock);
        verify(marshallerMock).marshal(same(graph), same(stream));
    }

    @Test
    public void testMarshalFilteringEnabled() throws XMLStreamException, JAXBException {
        doCallRealMethod().when(jaxbDataFormatMock).marshal(any(Exchange.class), anyObject(),
                any(OutputStream.class), any(Marshaller.class));
        when(jaxbDataFormatMock.needFiltering(exchangeMock)).thenReturn(true);

        Object graph = new Object();
        OutputStream stream = new ByteArrayOutputStream();

        jaxbDataFormatMock.marshal(exchangeMock, graph, stream, marshallerMock);
        verify(marshallerMock).marshal(same(graph), isA(FilteringXmlStreamWriter.class));

    }

    @Test
    public void testUnmarshalFilteringDisabled() throws IOException, JAXBException {
        doCallRealMethod().when(jaxbDataFormatMock).unmarshal(any(Exchange.class), 
                any(InputStream.class));

        when(jaxbDataFormatMock.getContext()).thenReturn(jaxbContextMock);
        when(jaxbContextMock.createUnmarshaller()).thenReturn(unmarshallerMock);

        when(jaxbDataFormatMock.needFiltering(exchangeMock)).thenReturn(false);

        InputStream stream = new ByteArrayInputStream(new byte[] {});

        jaxbDataFormatMock.unmarshal(exchangeMock, stream);
        verify(unmarshallerMock).unmarshal((InputStream) argThat(not(instanceOf(NonXmlFilterReader.class))));
    }

    @Test
    public void testUnmarshalFilteringEnabled() throws IOException, JAXBException {
        doCallRealMethod().when(jaxbDataFormatMock).unmarshal(any(Exchange.class), 
                any(InputStream.class));

        when(jaxbDataFormatMock.getContext()).thenReturn(jaxbContextMock);
        when(jaxbContextMock.createUnmarshaller()).thenReturn(unmarshallerMock);

        when(jaxbDataFormatMock.needFiltering(exchangeMock)).thenReturn(true);

        InputStream stream = new ByteArrayInputStream(new byte[] {});

        jaxbDataFormatMock.unmarshal(exchangeMock, stream);
        verify(unmarshallerMock).unmarshal(any(NonXmlFilterReader.class));
    }

}
