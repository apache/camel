package org.apache.camel.component.pojo;

import java.lang.reflect.Method;

public class PojoInvocation {

	private final Object proxy;
	private final Method method;
	private final Object[] args;

	public PojoInvocation(Object proxy, Method method, Object[] args) {
		this.proxy = proxy;
		this.method = method;
		this.args = args;
	}

	public Object[] getArgs() {
		return args;
	}

	public Method getMethod() {
		return method;
	}

	public Object getProxy() {
		return proxy;
	}


}
