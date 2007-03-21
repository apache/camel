package org.apache.camel.spring;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CamelBeanDefinitionParser extends AbstractBeanDefinitionParser {

	protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
		BeanDefinitionBuilder factory = BeanDefinitionBuilder.rootBeanDefinition(RouteBuilderFactory.class);
		
		List childElements = DomUtils.getChildElementsByTagName(element, "route");
		ArrayList<RouteBuilderStatement> routes = new ArrayList<RouteBuilderStatement>(childElements.size());

		if (childElements != null && childElements.size() > 0) {
			for (int i = 0; i < childElements.size(); ++i) {
				Element routeElement = (Element) childElements.get(i);
				RouteBuilderStatement def = parseRouteElement(routeElement);
				routes.add(def);
			}
		}

		factory.addPropertyValue("routes", routes);
		return factory.getBeanDefinition();
	}

	/** 
	 * Use reflection to figure out what is the valid next element.
	 * 
	 * @param routeElement
	 * @return
	 */
	private RouteBuilderStatement parseRouteElement(Element element) {
		RouteBuilderStatement rc = new RouteBuilderStatement();
		Class currentBuilder = RouteBuilder.class;
		
		NodeList childElements = element.getChildNodes();
		ArrayList<RouteBuilderAction> actions = new ArrayList<RouteBuilderAction>(childElements.getLength());
		if (childElements != null && childElements.getLength() > 0) {
			for (int i = 0; i < childElements.getLength(); ++i) {
				Node node = childElements.item(i);
				if( node.getNodeType() == Node.ELEMENT_NODE ) {
					currentBuilder = parseAction(currentBuilder, actions, (Element)node);
				}
			}
		}
		
		return rc;
	}

	private Class parseAction(Class currentBuilder, ArrayList<RouteBuilderAction> actions, Element element) {
		actions.add( new RouteBuilderAction(element.getNodeName()) );
		return currentBuilder;
	}

}
