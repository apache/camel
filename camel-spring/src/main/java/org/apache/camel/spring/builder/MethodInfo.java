/**
 * 
 */
package org.apache.camel.spring.builder;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import org.apache.camel.builder.Fluent;
import org.apache.camel.builder.FluentArg;

public class MethodInfo {
	
	final Method method;
	final Fluent methodAnnotation;
	final LinkedHashMap<String, Class> parameters;
	final LinkedHashMap<String, FluentArg> parameterAnnotations;
	
	public MethodInfo(Method method, Fluent fluentAnnotation, LinkedHashMap<String, Class> parameters, LinkedHashMap<String, FluentArg> annotations) {
		this.method=method;
		this.methodAnnotation = fluentAnnotation;
		this.parameters=parameters;
		this.parameterAnnotations = annotations;
	}

	public String getName() {
		return method.getName();
	}
}