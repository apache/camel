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
package org.apache.camel.model;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.NamedNode;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.NamespaceAware;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * Helper for the Camel {@link org.apache.camel.model model} classes.
 */
public final class ModelHelper {

    private ModelHelper() {
        // utility class
    }

    /**
     * Dumps the definition as XML
     *
     * @param context    the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param definition the definition, such as a {@link org.apache.camel.NamedNode}
     * @return the output in XML (is formatted)
     * @throws JAXBException is throw if error marshalling to XML
     */
    public static String dumpModelAsXml(CamelContext context, NamedNode definition) throws JAXBException {
        JAXBContext jaxbContext;
        if (context == null) {
            jaxbContext = createJAXBContext();
        } else {
            jaxbContext = context.getModelJAXBContextFactory().newJAXBContext();
        }

        final Map<String, String> namespaces = new LinkedHashMap<>();

        // gather all namespaces from the routes or route which is stored on the expression nodes
        if (definition instanceof RoutesDefinition) {
            List<RouteDefinition> routes = ((RoutesDefinition) definition).getRoutes();
            for (RouteDefinition route : routes) {
                extractNamespaces(route, namespaces);
            }
        } else if (definition instanceof RouteDefinition) {
            RouteDefinition route = (RouteDefinition) definition;
            extractNamespaces(route, namespaces);
        }

        // TODO: add all namespaces to the root node so the xml output includes those

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter buffer = new StringWriter();
        marshaller.marshal(definition, buffer);

        return buffer.toString();
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param context the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param xml     the xml
     * @param type    the definition type to return, will throw a {@link ClassCastException} if not the expected type
     * @return the model definition
     * @throws javax.xml.bind.JAXBException is thrown if error unmarshalling from xml to model
     */
    public static <T extends NamedNode> T createModelFromXml(CamelContext context, String xml, Class<T> type) throws JAXBException {
        // TODO: support injecting namespaces into each namespace aware node

        JAXBContext jaxbContext;
        if (context == null) {
            jaxbContext = createJAXBContext();
        } else {
            jaxbContext = context.getModelJAXBContextFactory().newJAXBContext();
        }

        StringReader reader = new StringReader(xml);
        Object result;
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            result = unmarshaller.unmarshal(reader);
        } finally {
            IOHelper.close(reader);
        }

        if (result == null) {
            throw new JAXBException("Cannot unmarshal to " + type + " using JAXB from XML: " + xml);
        }
        return type.cast(result);
    }

    /**
     * Marshal the xml to the model definition
     *
     * @param context the CamelContext, if <tt>null</tt> then {@link org.apache.camel.spi.ModelJAXBContextFactory} is not in use
     * @param stream  the xml stream
     * @param type    the definition type to return, will throw a {@link ClassCastException} if not the expected type
     * @return the model definition
     * @throws javax.xml.bind.JAXBException is thrown if error unmarshalling from xml to model
     */
    public static <T extends NamedNode> T createModelFromXml(CamelContext context, InputStream stream, Class<T> type) throws JAXBException {
        // TODO: support injecting namespaces into each namespace aware node

        JAXBContext jaxbContext;
        if (context == null) {
            jaxbContext = createJAXBContext();
        } else {
            jaxbContext = context.getModelJAXBContextFactory().newJAXBContext();
        }

        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        Object result = unmarshaller.unmarshal(stream);
        return type.cast(result);
    }

    private static JAXBContext createJAXBContext() throws JAXBException {
        // must use classloader from CamelContext to have JAXB working
        return JAXBContext.newInstance(Constants.JAXB_CONTEXT_PACKAGES, CamelContext.class.getClassLoader());
    }

    /**
     * Extract all XML namespaces from the expressions in the route
     *
     * @param route       the route
     * @param namespaces  the map of namespace to add new found XML namespaces
     */
    private static void extractNamespaces(RouteDefinition route, Map<String, String> namespaces) {
        Iterator<ExpressionNode> it = filterTypeInOutputs(route.getOutputs(), ExpressionNode.class);
        while (it.hasNext()) {
            ExpressionNode en = it.next();
            ExpressionDefinition ed = en.getExpression();

            // java-dsl sets directly expression so try this first
            NamespaceAware na = null;
            Expression exp = ed.getExpressionValue();
            if (exp != null && exp instanceof NamespaceAware) {
                na = (NamespaceAware) exp;
            } else if (ed instanceof NamespaceAware) {
                na = (NamespaceAware) ed;
            }

            if (na != null) {
                Map<String, String> map = na.getNamespaces();
                if (map != null && !map.isEmpty()) {
                    namespaces.putAll(map);
                }
            }
        }
    }
}
