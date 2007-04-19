package org.apache.camel.spring.xml;

import org.apache.camel.spring.xml.CamelBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

public class CamelNamespaceHandler extends NamespaceHandlerSupport {

	public void init() {
		 registerBeanDefinitionParser("routeBuilder", new CamelBeanDefinitionParser());    
	}

}
