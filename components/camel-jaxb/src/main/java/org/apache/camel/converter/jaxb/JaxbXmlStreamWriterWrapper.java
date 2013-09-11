package org.apache.camel.converter.jaxb;

import javax.xml.stream.XMLStreamWriter;

/**
 * A wrapper which allows to customize the {@link XMLStreamWriter}.
 */
public interface JaxbXmlStreamWriterWrapper {

    XMLStreamWriter wrapWriter(XMLStreamWriter writer);

}
