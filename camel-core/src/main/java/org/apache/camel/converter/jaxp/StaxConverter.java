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
package org.apache.camel.converter.jaxp;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Result;
import javax.xml.transform.Source;

import org.apache.camel.Converter;

/**
 * A converter of StAX objects
 *
 * @version $Revision$
 */
@Converter
public class StaxConverter {
    private XMLInputFactory inputFactory;
    private XMLOutputFactory outputFactory;

    @Converter
    public XMLEventWriter createXMLEventWriter(OutputStream out) throws XMLStreamException {
        return getOutputFactory().createXMLEventWriter(out);
    }

    @Converter
    public XMLEventWriter createXMLEventWriter(Writer writer) throws XMLStreamException {
        return getOutputFactory().createXMLEventWriter(writer);
    }

    @Converter
    public XMLEventWriter createXMLEventWriter(Result result) throws XMLStreamException {
        return getOutputFactory().createXMLEventWriter(result);
    }

    @Converter
    public XMLStreamWriter createXMLStreamWriter(OutputStream outputStream) throws XMLStreamException {
        return getOutputFactory().createXMLStreamWriter(outputStream);
    }

    @Converter
    public XMLStreamWriter createXMLStreamWriter(Writer writer) throws XMLStreamException {
        return getOutputFactory().createXMLStreamWriter(writer);
    }

    @Converter
    public XMLStreamWriter createXMLStreamWriter(Result result) throws XMLStreamException {
        return getOutputFactory().createXMLStreamWriter(result);
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(InputStream in) throws XMLStreamException {
        return getInputFactory().createXMLStreamReader(in);
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(Reader in) throws XMLStreamException {
        return getInputFactory().createXMLStreamReader(in);
    }

    @Converter
    public XMLStreamReader createXMLStreamReader(Source in) throws XMLStreamException {
        return getInputFactory().createXMLStreamReader(in);
    }

    @Converter
    public XMLEventReader createXMLEventReader(InputStream in) throws XMLStreamException {
        return getInputFactory().createXMLEventReader(in);
    }

    @Converter
    public XMLEventReader createXMLEventReader(Reader in) throws XMLStreamException {
        return getInputFactory().createXMLEventReader(in);
    }

    @Converter
    public XMLEventReader createXMLEventReader(XMLStreamReader in) throws XMLStreamException {
        return getInputFactory().createXMLEventReader(in);
    }

    @Converter
    public XMLEventReader createXMLEventReader(Source in) throws XMLStreamException {
        return getInputFactory().createXMLEventReader(in);
    }

    // Properties
    //-------------------------------------------------------------------------

    public XMLInputFactory getInputFactory() {
        if (inputFactory == null) {
            inputFactory = XMLInputFactory.newInstance();
        }
        return inputFactory;
    }

    public void setInputFactory(XMLInputFactory inputFactory) {
        this.inputFactory = inputFactory;
    }

    public XMLOutputFactory getOutputFactory() {
        if (outputFactory == null) {
            outputFactory = XMLOutputFactory.newInstance();
        }
        return outputFactory;
    }

    public void setOutputFactory(XMLOutputFactory outputFactory) {
        this.outputFactory = outputFactory;
    }
}
