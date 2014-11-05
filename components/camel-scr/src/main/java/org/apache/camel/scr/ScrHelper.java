package org.apache.camel.scr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings("unused")
public class ScrHelper {

    private static Logger log = LoggerFactory.getLogger(ScrHelper.class);

    public static Map<String, String> getScrProperties(String componentName) throws Exception {
        return getScrProperties(String.format("target/classes/OSGI-INF/%s.xml", componentName), componentName);
    }

    public static Map<String, String> getScrProperties(String xmlLocation, String componentName) throws Exception {
        Map<String, String> result = new HashMap<>();
        final Document dom = readXML(new File(xmlLocation));
        final XPath xPath = XPathFactory.newInstance(XPathFactory.DEFAULT_OBJECT_MODEL_URI, "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl", null).newXPath();
        xPath.setNamespaceContext(new NamespaceContext() {
            @Override
            public String getNamespaceURI(String prefix) {
                switch (prefix) {
                    case "scr":
                        try {
                            XPathExpression scrNamespace = xPath.compile("/*/namespace::*[name()='scr']");
                            Node node = (Node) scrNamespace.evaluate(dom, XPathConstants.NODE);
                            return node.getNodeValue();
                        } catch (XPathExpressionException e) {
                            e.printStackTrace();
                        }
                        return "http://www.osgi.org/xmlns/scr/v1.1.0";
                }
                return XMLConstants.NULL_NS_URI;
            }

            @Override
            public String getPrefix(String namespaceURI) {
                return null;
            }

            @Override
            public Iterator getPrefixes(String namespaceURI) {
                return null;
            }
        });
        String propertyListExpression = String.format("/components/scr:component[@name='%s']/property", componentName);
        XPathExpression propertyList = xPath.compile(propertyListExpression);
        XPathExpression propertyName = xPath.compile("@name");
        XPathExpression propertyValue = xPath.compile("@value");
        NodeList nodes = (NodeList) propertyList.evaluate(dom, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            result.put((String) propertyName.evaluate(node, XPathConstants.STRING), (String) propertyValue.evaluate(node, XPathConstants.STRING));
        }
        return result;
    }

    private static Document readXML(File xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        return builder.parse(xml);
    }
}
