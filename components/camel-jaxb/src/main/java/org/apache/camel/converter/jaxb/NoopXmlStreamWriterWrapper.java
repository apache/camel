package org.apache.camel.converter.jaxb;

import javax.xml.stream.XMLStreamWriter;

public class NoopXmlStreamWriterWrapper implements JaxbXmlStreamWriterWrapper {

    @Override
    public XMLStreamWriter wrapWriter(XMLStreamWriter writer) {
        return writer;
    }
}
