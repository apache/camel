package org.apache.camel.spring;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.camel.builder.Fluent;
import org.apache.camel.builder.FluentArg;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
			Element previousElement=null;
			for (int i = 0; i < childElements.getLength(); ++i) {
				Node node = childElements.item(i);
				if( node.getNodeType() == Node.ELEMENT_NODE ) {
					currentBuilder = parseAction(currentBuilder, actions, (Element)node, previousElement);
					previousElement = (Element)node;
				}
			}
		}
		
		rc.setActions(actions);
		return rc;
	}

	private Class parseAction(Class currentBuilder, ArrayList<RouteBuilderAction> actions, Element element, Element previousElement) {
		
		String actionName = element.getLocalName();
		
		// Get a list of method names that match the action.
		ArrayList<MethodInfo> methods = findFluentMethodsWithName( currentBuilder, element.getLocalName() );		
		if( methods.isEmpty() ) {
			throw new IllegalRouteException(actionName, previousElement==null? null : previousElement.getLocalName());
		}
		
		// Pick the best method out of the list.  Sort by argument length.  Pick first longest match.
		Collections.sort(methods, new Comparator<MethodInfo>(){
			public int compare(MethodInfo m1, MethodInfo m2) {
				return m1.method.getParameterTypes().length - m2.method.getParameterTypes().length; 
			}
		});
		
		// Build an ordered list of the element attributes.
		HashMap<String, Object> attributes = getAttributes(element);
		
		// Do we have enough parameters for this action.
		MethodInfo match=findBestMethod(methods, attributes);
		if( match == null )
			throw new IllegalRouteException(actionName, previousElement==null? null : previousElement.getLocalName());
			
		actions.add( new RouteBuilderAction(match, attributes) );
		return match.method.getReturnType();
	}

	private MethodInfo findBestMethod(ArrayList<MethodInfo> methods, HashMap<String, Object> attributes) {
		Set<String> attributeNames = attributes.keySet();
		for (MethodInfo method : methods) {
			Set<String> parameterNames = method.parameters.keySet();
			
			// If all the parameters are specified as parameters.
			if(    attributeNames.size()==parameterNames.size() 
				&& attributeNames.containsAll(parameterNames)) {
				return method;
			}
		}
		return null;
	}

	private HashMap<String, Object> getAttributes(Element element) {
		HashMap<String, Object> attributes = new HashMap<String, Object>();
		NamedNodeMap childNodes = element.getAttributes();
		for( int i=0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if( node.getNodeType() == Node.ATTRIBUTE_NODE ) {
				Attr attr = (Attr) node;
				
				String str = attr.getValue();
				Object value = str;
				
				// If the value starts with # then it's a bean reference
				if( str.startsWith("#")) {
					str = str.substring(1);
					// Support using ## to escape the bean reference feature.
					if( !str.startsWith("#")) {
						value = new RuntimeBeanReference(str);
					}					
				}
				
				attributes.put(attr.getName(), value);
			}
		}
		return attributes;
	}

	/**
	 * Finds all the methods on the clazz that match the name and which have the
	 * {@see Fluent} annotation and whoes parameters have the {@see FluentArg} annotation.
	 *  
	 * @param clazz
	 * @param name
	 * @return
	 */
	private ArrayList<MethodInfo> findFluentMethodsWithName(Class clazz, String name) {
		ArrayList<MethodInfo> rc = new ArrayList<MethodInfo>();
		Method[] methods = clazz.getMethods();
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			if( name.equals(method.getName())) {
				
				if( !method.isAnnotationPresent(Fluent.class) ) {
					List<Annotation> l = Arrays.asList(method.getAnnotations());
					System.out.println(l);
					continue;
				}
				
				
				LinkedHashMap<String, Class> map = new LinkedHashMap<String, Class>();
				Class<?>[] parameters = method.getParameterTypes();
				for (int j = 0; j < parameters.length; j++) {
					Class<?> parameter = parameters[j];
					FluentArg annotation = getParameterAnnotation(FluentArg.class, method, j);
					if( annotation!=null ) {
						map.put(annotation.value(), parameter);
					} else {
						break;
					}
				}
				
				// If all the parameters were annotated...
				if( parameters.length == map.size() ) {
					rc.add(new MethodInfo(method, map));
				}
			}
		}
		return rc;
	}

	private <T> T getParameterAnnotation(Class<T> annotationClass, Method method, int index) {
		Annotation[] annotations = method.getParameterAnnotations()[index];
		for (int i = 0; i < annotations.length; i++) {
			if( annotationClass.isAssignableFrom(annotations[i].getClass()) ) {
				return (T)annotations[i];
			}
		}
		return null;
	}

}
