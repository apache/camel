/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.xml;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.camel.builder.Fluent;
import org.apache.camel.builder.FluentArg;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
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
		ArrayList<BuilderStatement> routes = new ArrayList<BuilderStatement>(childElements.size());

		if (childElements != null && childElements.size() > 0) {
			for (int i = 0; i < childElements.size(); ++i) {
				Element routeElement = (Element) childElements.get(i);

				ArrayList<BuilderAction> actions = new ArrayList<BuilderAction>();
				Class type = parseBuilderElement(routeElement, RouteBuilder.class, actions);
				BuilderStatement statement = new BuilderStatement();
				statement.setReturnType(type);
				statement.setActions(actions);
				routes.add(statement);
			}
		}

		factory.addPropertyValue("routes", routes);
		return factory.getBeanDefinition();
	}

	/**
	 * Use reflection to figure out what is the valid next element.
	 */
	private Class parseBuilderElement(Element element, Class<RouteBuilder> builder, ArrayList<BuilderAction> actions) {
		Class currentBuilder = builder;
		NodeList childElements = element.getChildNodes();
		Element previousElement = null;
		for (int i = 0; i < childElements.getLength(); ++i) {
			Node node = childElements.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				currentBuilder = parseAction(currentBuilder, actions, (Element) node, previousElement);
				previousElement = (Element) node;
				BuilderAction action = actions.get(actions.size()-1);
				
				if( action.getMethodInfo().methodAnnotation.nestedActions() ) {
					currentBuilder = parseBuilderElement((Element) node, currentBuilder, actions);
				} else {
					// Make sure the there are no child elements.
					if( hasChildElements(node) ) {
						throw new IllegalArgumentException("The element "+node.getLocalName()+" should not have any child elements.");
					}
				}
				
			}
		}
		
		// Add the builder actions that are annotated with @Fluent(callOnElementEnd=true) 
		if( currentBuilder!=null ) {
			Method[] methods = currentBuilder.getMethods();
			for (int i = 0; i < methods.length; i++) {
				Method method = methods[i];
				Fluent annotation = method.getAnnotation(Fluent.class);
				if( annotation!=null && annotation.callOnElementEnd() ) {
					
					if( method.getParameterTypes().length > 0 ) {
						throw new RuntimeException("Only methods with no parameters can annotated with @Fluent(callOnElementEnd=true): "+method); 
					}
					
					MethodInfo methodInfo = new MethodInfo(method, annotation, new LinkedHashMap<String, Class>(), new LinkedHashMap<String, FluentArg>());
					actions.add(new BuilderAction(methodInfo, new HashMap<String, Object>()));
					currentBuilder = method.getReturnType();
				}
			}
		}
		return currentBuilder;
	}

	private boolean hasChildElements(Node node) {
		NodeList nl = node.getChildNodes();
		for (int j = 0; j < nl.getLength(); ++j) {
			if( nl.item(j).getNodeType() == Node.ELEMENT_NODE ) {
				return true;
			}
		}
		return false;
	}

	private Class parseAction(Class currentBuilder, ArrayList<BuilderAction> actions, Element element, Element previousElement) {

		String actionName = element.getLocalName();

		// Get a list of method names that match the action.
		ArrayList<MethodInfo> methods = findFluentMethodsWithName(currentBuilder, element.getLocalName());
		if (methods.isEmpty()) {
			throw new IllegalActionException(actionName, previousElement == null ? null : previousElement.getLocalName());
		}

		// Pick the best method out of the list. Sort by argument length. Pick
		// first longest match.
		Collections.sort(methods, new Comparator<MethodInfo>() {
			public int compare(MethodInfo m1, MethodInfo m2) {
				return m1.method.getParameterTypes().length - m2.method.getParameterTypes().length;
			}
		});

		// Build the possible list of arguments from the attributes and child
		// elements
		HashMap<String, Object> attributeArguments = getArugmentsFromAttributes(element);
		HashMap<String, ArrayList<Element>> elementArguments = getArgumentsFromElements(element);

		// Find the first method that we can supply arguments for.
		MethodInfo match = null;
		match = findMethodMatch(methods, attributeArguments.keySet(), elementArguments.keySet());
		if (match == null)
			throw new IllegalActionException(actionName, previousElement == null ? null : previousElement.getLocalName());

		// Move element arguments into the attributeArguments map if needed. 
		Set<String> parameterNames = new HashSet<String>(match.parameters.keySet());
		parameterNames.removeAll(attributeArguments.keySet());
		for (String key : parameterNames) {
			ArrayList<Element> elements = elementArguments.get(key);
			Class clazz = match.parameters.get(key);
			Object value = convertTo(elements, clazz);
			attributeArguments.put(key, value);
			for (Element el : elements) {
				// remove the argument nodes so that they don't get interpreted as
				// actions.
				el.getParentNode().removeChild(el);
			}
		}
		
		actions.add(new BuilderAction(match, attributeArguments));
		return match.method.getReturnType();
	}

	private Object convertTo(ArrayList<Element> elements, Class clazz) {

		if( clazz.isArray() || elements.size() > 1 ) {
			Object array = Array.newInstance(clazz.getComponentType(), elements.size());
			for( int i=0; i < elements.size(); i ++ ) {
				ArrayList<Element> e = new ArrayList<Element>(1);
				e.add(elements.get(i));
				Object value = convertTo(e, clazz.getComponentType());
				Array.set(array, i, value);
			}
			return array;
		} else {
			
			Element element = elements.get(0);
			String ref = element.getAttribute("ref");
			if( StringUtils.hasText(ref) ) {
				return new RuntimeBeanReference(ref);
			}
			
			// Use a builder to create the value..
			if( hasChildElements(element) ) {
				
				ArrayList<BuilderAction> actions = new ArrayList<BuilderAction>();
				Class type = parseBuilderElement(element, RouteBuilder.class, actions);
				BuilderStatement statement = new BuilderStatement();
				statement.setReturnType(type);
				statement.setActions(actions);
				
				if( !clazz.isAssignableFrom( statement.getReturnType() ) ) {
					throw new IllegalStateException("Builder does not produce object of expected type: "+clazz.getName());
				}
				
				return statement;
			} else {
				// Just use the text in the element as the value.
				SimpleTypeConverter converter = new SimpleTypeConverter();
				return converter.convertIfNecessary(element.getTextContent(), clazz);
			}
		}
	}

	private MethodInfo findMethodMatch(ArrayList<MethodInfo> methods, Set<String> attributeNames, Set<String> elementNames) {
		for (MethodInfo method : methods) {

			// make sure all the given attribute parameters can be assigned via
			// attributes
			boolean miss = false;
			for (String key : attributeNames) {
				FluentArg arg = method.parameterAnnotations.get(key);
				if (arg == null || !arg.attribute()) {
					miss = true;
					break;
				}
			}
			if (miss)
				continue; // Keep looking...

			Set<String> parameterNames = new HashSet<String>(method.parameters.keySet());
			parameterNames.removeAll(attributeNames);

			// Bingo we found a match.
			if (parameterNames.isEmpty()) {
				return method;
			}

			// We may still be able to match using elements as parameters.
			for (String key : elementNames) {
				if (parameterNames.isEmpty()) {
					break;
				}
				// We only want to use the first child elements as arguments,
				// once we don't match, we can stop looking.
				FluentArg arg = method.parameterAnnotations.get(key);
				if (arg == null || !arg.element()) {
					break;
				}
				if (!parameterNames.remove(key)) {
					break;
				}
			}

			// All parameters found! We have a match!
			if (parameterNames.isEmpty()) {
				return method;
			}

		}
		return null;
	}

	private LinkedHashMap<String, ArrayList<Element>> getArgumentsFromElements(Element element) {
		LinkedHashMap<String, ArrayList<Element>> elements = new LinkedHashMap<String, ArrayList<Element>>();
		NodeList childNodes = element.getChildNodes();
		String lastTag = null;
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element el = (Element) node;
				String tag = el.getLocalName();
				ArrayList<Element> els = elements.get(tag);
				if (els == null) {
					els = new ArrayList<Element>();
					elements.put(el.getLocalName(), els);
					els.add(el);
					lastTag = tag;
				} else {
					// add to array if the elements are consecutive
					if (tag.equals(lastTag)) {
						els.add(el);
						lastTag = tag;
					}
				}
			}
		}
		return elements;
	}

	private HashMap<String, Object> getArugmentsFromAttributes(Element element) {
		HashMap<String, Object> attributes = new HashMap<String, Object>();
		NamedNodeMap childNodes = element.getAttributes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
				Attr attr = (Attr) node;

				String str = attr.getValue();
				Object value = str;

				// If the value starts with # then it's a bean reference
				if (str.startsWith("#")) {
					str = str.substring(1);
					// Support using ## to escape the bean reference feature.
					if (!str.startsWith("#")) {
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
	 * {@see Fluent} annotation and whoes parameters have the {@see FluentArg}
	 * annotation.
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
			if (!method.isAnnotationPresent(Fluent.class)) {
				continue;
			}
			
			// Use the fluent supplied name for the action, or the method name if not set.
			Fluent fluentAnnotation = method.getAnnotation(Fluent.class);
			if ( StringUtils.hasText(fluentAnnotation.value()) ? 
					name.equals(fluentAnnotation.value()) :
					name.equals(method.getName()) ) {

				LinkedHashMap<String, Class> map = new LinkedHashMap<String, Class>();
				LinkedHashMap<String, FluentArg> amap = new LinkedHashMap<String, FluentArg>();
				Class<?>[] parameters = method.getParameterTypes();
				for (int j = 0; j < parameters.length; j++) {
					Class<?> parameter = parameters[j];
					FluentArg annotation = getParameterAnnotation(FluentArg.class, method, j);
					if (annotation != null) {
						map.put(annotation.value(), parameter);
						amap.put(annotation.value(), annotation);
					} else {
						break;
					}
				}

				// If all the parameters were annotated...
				if (parameters.length == map.size()) {
					rc.add(new MethodInfo(method, fluentAnnotation, map, amap));
				}
			}
		}
		return rc;
	}

	private <T> T getParameterAnnotation(Class<T> annotationClass, Method method, int index) {
		Annotation[] annotations = method.getParameterAnnotations()[index];
		for (int i = 0; i < annotations.length; i++) {
			if (annotationClass.isAssignableFrom(annotations[i].getClass())) {
				return (T) annotations[i];
			}
		}
		return null;
	}

}
