package org.apache.camel.spring.xml;

import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.CamelContextFactoryBean;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class CamelNamespaceHandler extends NamespaceHandlerSupport {
    protected CamelBeanDefinitionParser camelBeanDefinitionParser = new CamelBeanDefinitionParser();

    public void init() {
        registerBeanDefinitionParser("routes", camelBeanDefinitionParser);
        registerBeanDefinitionParser("routeBuilder", camelBeanDefinitionParser);
        
        registerBeanDefinitionParser("camelContext", new AbstractSimpleBeanDefinitionParser() {
            protected Class getBeanClass(Element element) {
                return CamelContextFactoryBean.class;
            }

            @Override
            protected boolean isEligibleAttribute(String attributeName) {
                return super.isEligibleAttribute(attributeName) && !attributeName.equals("xmlns");
            }

            @Override
            protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
                super.doParse(element, parserContext, builder);

                NodeList list = element.getElementsByTagName("routes");
                for (int size = list.getLength(), i = 0; i < size; i++) {
                    Element node = (Element) list.item(i);
                    BeanDefinition definition = camelBeanDefinitionParser.parseInternal(node, parserContext);
                    builder.addPropertyValue("routeBuilder", definition);
                }
            }
        });
    }
}
