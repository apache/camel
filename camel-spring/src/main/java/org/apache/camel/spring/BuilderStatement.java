package org.apache.camel.spring;

import java.util.ArrayList;

import org.springframework.beans.factory.BeanFactory;

public class BuilderStatement {
	private ArrayList<BuilderAction> actions;
	private Class returnType;

	public Object create(BeanFactory beanFactory, Object rootBuilder) {
		Object currentBuilder = rootBuilder;
		BuilderAction lastAction=null;
		for (BuilderAction action : actions) {
			// The last action may have left us without a builder to invoke next!
			if( currentBuilder == null ) {
				throw new IllegalArgumentException("Invalid configuration.  The '"+lastAction.getName()+"' action cannot be followed by the '"+action.getName()+"' action.");
			}
			currentBuilder = action.invoke(beanFactory, rootBuilder, currentBuilder);
			lastAction = action;
		}
		return currentBuilder;
	}

	public ArrayList<BuilderAction> getActions() {
		return actions;
	}
	public void setActions(ArrayList<BuilderAction> actions) {
		this.actions = actions;
	}

	public Class getReturnType() {
		return returnType;
	}
	public void setReturnType(Class returnType) {
		this.returnType = returnType;
		
	}

}
