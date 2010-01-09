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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLStreamException;

import org.apache.camel.Exchange;
import org.easymock.classextension.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.same;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JaxbDataFormatTest extends EasyMockSupport {
    private JaxbDataFormat jaxbDataFormat;
    private Exchange exchangeMock;
    private Marshaller marshallerMock;

    @Before
    public void setUp() {
        jaxbDataFormat = new JaxbDataFormat();
        marshallerMock = createStrictMock(Marshaller.class);
        exchangeMock = createStrictMock(Exchange.class);
    }

    @Test
    public void testNeedFiltering() {
        // tests combinations of data format option and exchange property
        expect(
                exchangeMock.getProperty(Exchange.FILTER_NON_XML_CHARS, false,
                        Boolean.class)).andReturn(false);
        replayAll();

        assertFalse("Not expected filtering here", jaxbDataFormat.needFiltering(exchangeMock));
        verifyAll();
        resetAll();

        expect(
                exchangeMock.getProperty(Exchange.FILTER_NON_XML_CHARS, false,
                        Boolean.class)).andReturn(true);
        replayAll();

        assertTrue("Expected filtering here", jaxbDataFormat.needFiltering(exchangeMock));
        verifyAll();
        resetAll();

        jaxbDataFormat.setFilterNonXmlChars(true);
        expect(
                exchangeMock.getProperty(Exchange.FILTER_NON_XML_CHARS, true,
                        Boolean.class)).andReturn(false);
        replayAll();

        assertFalse("Not expected filtering here", jaxbDataFormat.needFiltering(exchangeMock));
        verifyAll();
        resetAll();

        expect(
                exchangeMock.getProperty(Exchange.FILTER_NON_XML_CHARS, true,
                        Boolean.class)).andReturn(true);
        replayAll();

        assertTrue("Expected filtering here", jaxbDataFormat.needFiltering(exchangeMock));
        verifyAll();
        resetAll();
    }

    @Test
    public void testMarshalFilteringDisabled() throws XMLStreamException, JAXBException {
        JaxbDataFormat jaxbDataFormatMock = createMockBuilder(JaxbDataFormat.class)
                .addMockedMethod("needFiltering").createStrictMock();
        Object graph = new Object();
        OutputStream stream = new ByteArrayOutputStream();

        expect(jaxbDataFormatMock.needFiltering(exchangeMock)).andReturn(false);
        marshallerMock.marshal(same(graph), same(stream));
        replayAll();

        jaxbDataFormatMock.marshal(exchangeMock, graph, stream, marshallerMock);
        verifyAll();
    }

    @Test
    public void testMarshalFilteringEnabled() throws XMLStreamException, JAXBException {
        JaxbDataFormat jaxbDataFormatMock = createMockBuilder(JaxbDataFormat.class)
            .addMockedMethod("needFiltering").createStrictMock();

        Object graph = new Object();
        OutputStream stream = new ByteArrayOutputStream();

        expect(jaxbDataFormatMock.needFiltering(exchangeMock)).andReturn(true);
        marshallerMock.marshal(same(graph), isA(FilteringXmlStreamWriter.class));
        replayAll();

        jaxbDataFormatMock.marshal(exchangeMock, graph, stream, marshallerMock);
        verifyAll();
    }
}
