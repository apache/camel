package org.apache.camel.spring.xml;

import static org.apache.camel.util.ObjectHelper.isNotNullOrBlank;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.EndpointFactoryBean;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.PropertyValue;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

public class CamelNamespaceHandler extends NamespaceHandlerSupport {
    private int counter;

    protected CamelBeanDefinitionParser routesParser = new CamelBeanDefinitionParser();
    protected BeanDefinitionParserSupport endpointParser = new BeanDefinitionParserSupport(EndpointFactoryBean.class);

    public void init() {
        registerBeanDefinitionParser("routes", routesParser);
        registerBeanDefinitionParser("routeBuilder", routesParser);
        registerBeanDefinitionParser("endpoint", endpointParser);

        registerBeanDefinitionParser("camelContext", new BeanDefinitionParserSupport(CamelContextFactoryBean.class) {

            @Override
            protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
                super.doParse(element, parserContext, builder);

                String contextId = element.getAttribute("id");

                Element routes = element.getOwnerDocument().createElement("routes");
                // now lets move all the content there...
                NodeList list = element.getChildNodes();
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Node child = list.item(i);
                    if (child instanceof Element) {
                        element.removeChild(child);
                        routes.appendChild(child);
                    }
                }
                String routeId = contextId + ":routes";
                routes.setAttribute("id", routeId);

                    BeanDefinition definition = routesParser.parse(routes, parserContext);
                    definition.getPropertyValues().addPropertyValue("context", new RuntimeBeanReference(contextId));
                    //definition.getPropertyValues().addPropertyValue("context", builder.getBeanDefinition());
                    //builder.addPropertyValue("routeBuilder", definition);
                    parserContext.registerComponent(new BeanComponentDefinition(definition, routeId));

/*
                NodeList list = element.getElementsByTagName("routes");
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Element node = (Element) list.item(i);
                    String routeId = node.getAttribute("id");
                    if (!isNotNullOrBlank(routeId)) {
                        routeId = "__camel_route_" + nextCounter();
                        node.setAttribute("id", routeId);
                    }
                    BeanDefinition definition = routesParser.parse(node, parserContext);
                    definition.getPropertyValues().addPropertyValue("context", new RuntimeBeanReference(contextId));
                    //definition.getPropertyValues().addPropertyValue("context", builder.getBeanDefinition());
                    //builder.addPropertyValue("routeBuilder", definition);
                    parserContext.registerComponent(new BeanComponentDefinition(definition, routeId));
                }

*/
                list = routes.getElementsByTagName("endpoint");
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Element node = (Element) list.item(i);
                    definition = endpointParser.parse(node, parserContext);
                    String id = node.getAttribute("id");
                    if (isNotNullOrBlank(id)) {
                        //definition.getPropertyValues().addPropertyValue("context", builder.getBeanDefinition());
                        definition.getPropertyValues().addPropertyValue("context", new RuntimeBeanReference(contextId));
                        parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                    }
                }
            }
        });
    }

    protected synchronized int nextCounter() {
        return ++counter;
    }
}
