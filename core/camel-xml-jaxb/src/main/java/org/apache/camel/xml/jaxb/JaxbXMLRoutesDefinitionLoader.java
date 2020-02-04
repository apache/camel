package org.apache.camel.xml.jaxb;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.util.ObjectHelper;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;


import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * JAXB based {@link XMLRoutesDefinitionLoader}. This is the default loader used historically by Camel.
 * The camel-xml-io parser is a light-weight alternative.
 */
public class JaxbXMLRoutesDefinitionLoader implements XMLRoutesDefinitionLoader {

    @Override
    public Object loadRoutesDefinition(CamelContext context, InputStream inputStream) throws Exception {
        XmlConverter xmlConverter = newXmlConverter(context);
        Document dom = xmlConverter.toDOMDocument(inputStream, null);

        JAXBContext jaxbContext = getJAXBContext(context);

        Map<String, String> namespaces = new LinkedHashMap<>();
        extractNamespaces(dom, namespaces);

        Binder<Node> binder = jaxbContext.createBinder();
        Object result = binder.unmarshal(dom);

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to RoutesDefinition using JAXB");
        }

        // can either be routes or a single route
        RoutesDefinition answer;
        if (result instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition)result;
            answer = new RoutesDefinition();
            applyNamespaces(route, namespaces);
            answer.getRoutes().add(route);
        } else if (result instanceof RoutesDefinition) {
            answer = (RoutesDefinition)result;
            for (RouteDefinition route : answer.getRoutes()) {
                applyNamespaces(route, namespaces);
            }
        } else {
            throw new IllegalArgumentException("Unmarshalled object is an unsupported type: " + ObjectHelper.className(result) + " -> " + result);
        }

        return answer;
    }

    @Override
    public Object loadRestsDefinition(CamelContext context, InputStream inputStream) throws Exception {
        // load routes using JAXB
        Unmarshaller unmarshaller = getJAXBContext(context).createUnmarshaller();
        Object result = unmarshaller.unmarshal(inputStream);

        if (result == null) {
            throw new IOException("Cannot unmarshal to rests using JAXB from input stream: " + inputStream);
        }

        // can either be routes or a single route
        RestsDefinition answer;
        if (result instanceof RestDefinition) {
            RestDefinition rest = (RestDefinition)result;
            answer = new RestsDefinition();
            answer.getRests().add(rest);
        } else if (result instanceof RestsDefinition) {
            answer = (RestsDefinition)result;
        } else {
            throw new IllegalArgumentException("Unmarshalled object is an unsupported type: " + ObjectHelper.className(result) + " -> " + result);
        }

        return answer;
    }

    @Override
    public String toString() {
        return "camel-xml-jaxb";
    }

    private static JAXBContext getJAXBContext(CamelContext context) throws JAXBException {
        ModelJAXBContextFactory factory = context.adapt(ExtendedCamelContext.class).getModelJAXBContextFactory();
        return factory.newJAXBContext();
    }

    /**
     * Creates a new {@link XmlConverter}
     *
     * @param context CamelContext if provided
     * @return a new XmlConverter instance
     */
    private static XmlConverter newXmlConverter(CamelContext context) {
        XmlConverter xmlConverter;
        if (context != null) {
            TypeConverterRegistry registry = context.getTypeConverterRegistry();
            xmlConverter = registry.getInjector().newInstance(XmlConverter.class, false);
        } else {
            xmlConverter = new XmlConverter();
        }
        return xmlConverter;
    }

    /**
     * Extract all XML namespaces from the root element in a DOM Document
     *
     * @param document the DOM document
     * @param namespaces the map of namespaces to add new found XML namespaces
     */
    private static void extractNamespaces(Document document, Map<String, String> namespaces) throws JAXBException {
        NamedNodeMap attributes = document.getDocumentElement().getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            String nsPrefix = item.getNodeName();
            if (nsPrefix != null && nsPrefix.startsWith("xmlns")) {
                String nsValue = item.getNodeValue();
                String[] nsParts = nsPrefix.split(":");
                if (nsParts.length == 1) {
                    namespaces.put(nsParts[0], nsValue);
                } else if (nsParts.length == 2) {
                    namespaces.put(nsParts[1], nsValue);
                } else {
                    // Fallback on adding the namespace prefix as we find it
                    namespaces.put(nsPrefix, nsValue);
                }
            }
        }
    }

    private static NamespaceAware getNamespaceAwareFromExpression(ExpressionNode expressionNode) {
        ExpressionDefinition ed = expressionNode.getExpression();

        NamespaceAware na = null;
        Expression exp = ed.getExpressionValue();
        if (exp instanceof NamespaceAware) {
            na = (NamespaceAware)exp;
        } else if (ed instanceof NamespaceAware) {
            na = (NamespaceAware)ed;
        }

        return na;
    }

    private static void applyNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        Iterator<ExpressionNode> it = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        while (it.hasNext()) {
            NamespaceAware na = getNamespaceAwareFromExpression(it.next());
            if (na != null) {
                na.setNamespaces(namespaces);
            }
        }
    }
}
