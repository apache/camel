/**
 * 
 */
package org.apache.camel.spring;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;

import org.springframework.beans.BeanWrapperImpl;

public class MethodInfo {
	final Method method;
	final LinkedHashMap<String, Class> parameters;
	
	public MethodInfo(Method method, LinkedHashMap<String, Class> parameters) {
		this.method=method;
		this.parameters=parameters;
	}

	public String getName() {
		return method.getName();
	}
}