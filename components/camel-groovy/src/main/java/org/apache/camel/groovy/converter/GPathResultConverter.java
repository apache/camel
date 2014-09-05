package org.apache.camel.groovy.converter;

import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.StringSource;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;

@Converter
public class GPathResultConverter {

    private final XmlConverter xmlConverter = new XmlConverter();

    @Converter
    public GPathResult fromString(String input) throws ParserConfigurationException, SAXException, IOException {
        return new XmlSlurper().parseText(input);
    }

    @Converter
    public GPathResult fromStringSource(StringSource input) throws IOException, SAXException, ParserConfigurationException {
        return fromString(input.getText());
    }

    @Converter
    public GPathResult fromNode(Node input, Exchange exchange) throws IOException, SAXException, ParserConfigurationException, TransformerException {
        return fromString(xmlConverter.toString(input, exchange));
    }
}
