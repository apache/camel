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

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.easymock.classextension.EasyMockSupport;
import org.junit.Before;
import org.junit.Test;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.same;

public class FilteringXmlStreamWriterTest extends EasyMockSupport {
    private FilteringXmlStreamWriter filteringXmlStreamWriter;
    private NonXmlCharFilterer nonXmlCharFiltererMock;
    private XMLStreamWriter xmlStreamWriterMock;

    // only testing non-generated methods, those that do apply filtering

    @Before
    public void setUp() {
        xmlStreamWriterMock = createStrictMock(XMLStreamWriter.class);
        nonXmlCharFiltererMock = createStrictMock(NonXmlCharFilterer.class);
        filteringXmlStreamWriter = new FilteringXmlStreamWriter(xmlStreamWriterMock);
        filteringXmlStreamWriter.nonXmlCharFilterer = nonXmlCharFiltererMock;
    }

    @Test
    public void testWriteAttribute2Args() throws XMLStreamException {
        expect(nonXmlCharFiltererMock.filter("value")).andReturn("filteredValue");
        xmlStreamWriterMock.writeAttribute("localName", "filteredValue");
        replayAll();

        filteringXmlStreamWriter.writeAttribute("localName", "value");
        verifyAll();
    }

    @Test
    public void testWriteAttribute3Args() throws XMLStreamException {
        expect(nonXmlCharFiltererMock.filter("value")).andReturn("filteredValue");
        xmlStreamWriterMock.writeAttribute("namespaceURI", "localName", "filteredValue");
        replayAll();

        filteringXmlStreamWriter.writeAttribute("namespaceURI", "localName", "value");
        verifyAll();
    }

    @Test
    public void testWriteAttribute4Args() throws XMLStreamException {
        expect(nonXmlCharFiltererMock.filter("value")).andReturn("filteredValue");
        xmlStreamWriterMock.writeAttribute("prefix", "namespaceURI", "localName", "filteredValue");
        replayAll();

        filteringXmlStreamWriter.writeAttribute("prefix", "namespaceURI", "localName", "value");
        verifyAll();
    }

    @Test
    public void testWriteCData() throws XMLStreamException {
        expect(nonXmlCharFiltererMock.filter("value")).andReturn("filteredValue");
        xmlStreamWriterMock.writeCData("filteredValue");
        replayAll();

        filteringXmlStreamWriter.writeCData("value");
        verifyAll();
    }

    @Test
    public void testWriteCharacters1Arg() throws XMLStreamException {
        expect(nonXmlCharFiltererMock.filter("value")).andReturn("filteredValue");
        xmlStreamWriterMock.writeCharacters("filteredValue");
        replayAll();

        filteringXmlStreamWriter.writeCharacters("value");
        verifyAll();
    }

    @Test
    public void testWriteComment() throws XMLStreamException {
        expect(nonXmlCharFiltererMock.filter("value")).andReturn("filteredValue");
        xmlStreamWriterMock.writeComment("filteredValue");
        replayAll();

        filteringXmlStreamWriter.writeComment("value");
        verifyAll();
    }

    @Test
    public void testWriteCharacters3Args() throws XMLStreamException {
        char[] buffer = new char[] {'a', 'b', 'c'};
        expect(nonXmlCharFiltererMock.filter(same(buffer), eq(2), eq(3))).andReturn(true);
        xmlStreamWriterMock.writeCharacters(same(buffer), eq(2), eq(3));
        replayAll();

        filteringXmlStreamWriter.writeCharacters(buffer, 2, 3);
        verifyAll();
    }

}
