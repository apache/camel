/**
 * 
 */
package org.apache.camel.spring;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import org.apache.camel.builder.FluentArg;

public class MethodInfo {
	final Method method;
	final LinkedHashMap<String, Class> parameters;
	final LinkedHashMap<String, FluentArg> annotations;
	
	public MethodInfo(Method method, LinkedHashMap<String, Class> parameters, LinkedHashMap<String, FluentArg> annotations) {
		this.method=method;
		this.parameters=parameters;
		this.annotations = annotations;
	}

	public String getName() {
		return method.getName();
	}
}