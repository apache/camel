package org.apache.camel.spring.xml;

import org.apache.camel.spring.CamelContextFactoryBean;
import org.apache.camel.spring.EndpointFactoryBean;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.PropertyValue;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class CamelNamespaceHandler extends NamespaceHandlerSupport {
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

                NodeList list = element.getElementsByTagName("routes");
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Element node = (Element) list.item(i);
                    BeanDefinition definition = routesParser.parseInternal(node, parserContext);
                    builder.addPropertyValue("routeBuilder", definition);
                }

                list = element.getElementsByTagName("endpoint");
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Element node = (Element) list.item(i);
                    BeanDefinition definition = endpointParser.parse(node, parserContext);
                    String id = node.getAttribute("id");
                    if (id != null) {
                        definition.getPropertyValues().addPropertyValue("context", builder.getBeanDefinition()); 
                        parserContext.registerComponent(new BeanComponentDefinition(definition, id));
                    }
                }
            }
        });
    }
}
