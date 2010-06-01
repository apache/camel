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
package org.apache.camel.blueprint.handler;

import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.xml.bind.Binder;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.aries.blueprint.NamespaceHandler;
import org.apache.aries.blueprint.ParserContext;
import org.apache.aries.blueprint.mutable.MutableBeanMetadata;
import org.apache.aries.blueprint.mutable.MutablePassThroughMetadata;
import org.apache.camel.blueprint.BlueprintCamelContext;
import org.apache.camel.blueprint.CamelContextFactoryBean;
import org.apache.camel.core.xml.AbstractCamelContextFactoryBean;
import org.apache.camel.util.ObjectHelper;
import org.osgi.service.blueprint.container.ComponentDefinitionException;
import org.osgi.service.blueprint.reflect.ComponentMetadata;
import org.osgi.service.blueprint.reflect.Metadata;

public class CamelNamespaceHandler implements NamespaceHandler {

    private static final String CAMEL_CONTEXT = "camelContext";

    private static final String SPRING_NS = "http://camel.apache.org/schema/spring";
    private static final String BLUEPRINT_NS = "http://camel.apache.org/schema/blueprint";

    private JAXBContext jaxbContext;

    public static void renameNamespaceRecursive(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Document doc = node.getOwnerDocument();
            if (((Element) node).getNamespaceURI().equals(BLUEPRINT_NS)) {
                doc.renameNode(node, SPRING_NS, node.getNodeName());
            }
        }
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); ++i) {
            renameNamespaceRecursive(list.item(i));
        }
    }

    public URL getSchemaLocation(String namespace) {
        return getClass().getClassLoader().getResource("camel-blueprint.xsd");
    }

    @SuppressWarnings("unchecked")
    public Set<Class> getManagedClasses() {
        return new HashSet<Class>(Arrays.asList(
                BlueprintCamelContext.class
        ));
    }

    public Metadata parse(Element element, ParserContext context) {
        renameNamespaceRecursive(element);
        if (element.getNodeName().equals(CAMEL_CONTEXT)) {
            // Find the id, generate one if needed
            String contextId = element.getAttribute("id");
            if (ObjectHelper.isEmpty(contextId)) {
                contextId = "camelContext";
                element.setAttribute("id", contextId);
            }

            // now lets parse the routes with JAXB
            Binder<Node> binder;
            try {
                binder = getJaxbContext().createBinder();
            } catch (JAXBException e) {
                throw new ComponentDefinitionException("Failed to create the JAXB binder : " + e, e);
            }
            Object value = parseUsingJaxb(element, context, binder);
            if (!(value instanceof CamelContextFactoryBean)) {
                throw new ComponentDefinitionException("Expected an instance of " + CamelContextFactoryBean.class);
            }

            MutablePassThroughMetadata factory = context.createMetadata(MutablePassThroughMetadata.class);
            factory.setId(".camelBlueprint.passThrough." + contextId);
            factory.setObject(new PassThroughCallable<Object>(value));

            MutableBeanMetadata factory2 = context.createMetadata(MutableBeanMetadata.class);
            factory2.setId(".camelBlueprint.factory." + contextId);
            factory2.setFactoryComponent(factory);
            factory2.setFactoryMethod("call");
            factory2.setInitMethod("afterPropertiesSet");
            factory2.setDestroyMethod("destroy");

            MutableBeanMetadata ctx = context.createMetadata(MutableBeanMetadata.class);
            ctx.setId(contextId);
            ctx.setFactoryComponent(factory2);
            ctx.setFactoryMethod("getContext");
            ctx.setInitMethod("init");
            ctx.setDestroyMethod("destroy");

            return ctx;
        }
        return null;
    }

    public ComponentMetadata decorate(Node node, ComponentMetadata component, ParserContext context) {
        return null;
    }

    protected Object parseUsingJaxb(Element element, ParserContext parserContext, Binder<Node> binder) {
        try {
            return binder.unmarshal(element);
        } catch (JAXBException e) {
            throw new ComponentDefinitionException("Failed to parse JAXB element: " + e, e);
        }
    }

    public JAXBContext getJaxbContext() throws JAXBException {
        if (jaxbContext == null) {
            jaxbContext = createJaxbContext();
        }
        return jaxbContext;
    }

    protected JAXBContext createJaxbContext() throws JAXBException {
        StringBuilder packages = new StringBuilder();
        for (Class cl : getJaxbPackages()) {
            if (packages.length() > 0) {
                packages.append(":");
            }
            packages.append(cl.getName().substring(0, cl.getName().lastIndexOf('.')));
        }
        return JAXBContext.newInstance(packages.toString(), getClass().getClassLoader());
    }

    protected Set<Class> getJaxbPackages() {
        Set<Class> classes = new HashSet<Class>();
        classes.add(CamelContextFactoryBean.class);
        classes.add(AbstractCamelContextFactoryBean.class);
        classes.add(org.apache.camel.ExchangePattern.class);
        classes.add(org.apache.camel.model.RouteDefinition.class);
        classes.add(org.apache.camel.model.config.StreamResequencerConfig.class);
        classes.add(org.apache.camel.model.dataformat.DataFormatsDefinition.class);
        classes.add(org.apache.camel.model.language.ExpressionDefinition.class);
        classes.add(org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition.class);
        return classes;
    }

    public static class PassThroughCallable<T> implements Callable<T> {

        private T value;

        public PassThroughCallable(T value) {
            this.value = value;
        }

        public T call() throws Exception {
            return value;
        }
    }

}
