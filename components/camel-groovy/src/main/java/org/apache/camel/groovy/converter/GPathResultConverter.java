package org.apache.camel.groovy.converter;

import groovy.util.XmlSlurper;
import groovy.util.slurpersupport.GPathResult;
import groovy.xml.StreamingMarkupBuilder;
import org.apache.camel.Converter;
import org.apache.camel.StringSource;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringWriter;

@Converter
public class GPathResultConverter {

    @Converter
    public static GPathResult fromString(String input) throws ParserConfigurationException, SAXException, IOException {
        return new XmlSlurper().parseText(input);
    }

    @Converter
    public static GPathResult fromStringSource(StringSource input) throws IOException, SAXException, ParserConfigurationException {
        return fromString(input.getText());
    }

    @Converter
    public static GPathResult fromNode(Node input) throws IOException, SAXException, ParserConfigurationException, TransformerException {
        StringWriter writer = new StringWriter();
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new DOMSource(input), new StreamResult(writer));
        return fromString(writer.toString());
    }
}
