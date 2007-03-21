package org.apache.camel.spring;

import java.util.ArrayList;

import org.springframework.beans.factory.BeanFactory;

public class RouteBuilderStatement {
	private ArrayList<RouteBuilderAction> actions;

	public void create(BeanFactory beanFactory, Object builder) {
		Object currentBuilder = builder;
		RouteBuilderAction lastAction=null;
		for (RouteBuilderAction action : actions) {
			// The last action may have left us without a builder to invoke next!
			if( builder == null ) {
				throw new IllegalArgumentException("Invalid route configuration.  The '"+lastAction.getName()+"' action cannot be followed by the '"+action.getName()+"' action.");
			}
			currentBuilder = action.invoke(beanFactory, currentBuilder);
			lastAction = action;
		}
	}

	public ArrayList<RouteBuilderAction> getActions() {
		return actions;
	}
	public void setActions(ArrayList<RouteBuilderAction> actions) {
		this.actions = actions;
	}
}
