package org.apache.camel.spring;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;

public class BuilderAction {

	private final MethodInfo methodInfo;
	private final HashMap<String, Object> parameterValues;

	public BuilderAction(MethodInfo methodInfo, HashMap<String, Object> parameterValues) {
		this.methodInfo = methodInfo;
		this.parameterValues = parameterValues;
	}

	public Object invoke(BeanFactory beanFactory, Object rootBuilder, Object contextBuilder) {
		SimpleTypeConverter converter = new SimpleTypeConverter();
		Object args[] = new Object[methodInfo.parameters.size()];
		int pos=0;
		for (Map.Entry<String, Class> entry :  methodInfo.parameters.entrySet()) {
			String paramName = entry.getKey();
			Class paramClass = entry.getValue();
			Object value = parameterValues.get(paramName);
			if( value != null ) {
				if( value.getClass() == RuntimeBeanReference.class ) {
					value = beanFactory.getBean(((RuntimeBeanReference)value).getBeanName());
				}
				if( value.getClass() == BuilderStatement.class ) {
					BuilderStatement bs = (BuilderStatement) value;
					value = bs.create(beanFactory, rootBuilder);
				}				
				args[pos] = converter.convertIfNecessary(value, paramClass);				
			}
			
		}
		
		try {
			return methodInfo.method.invoke(contextBuilder, args);
		} catch (InvocationTargetException e) {
			throw new IllegalArgumentException(e.getCause());
		} catch (RuntimeException e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalArgumentException(e);
		}
		
	}

	public String getName() {
		return methodInfo.getName();
	}
}
