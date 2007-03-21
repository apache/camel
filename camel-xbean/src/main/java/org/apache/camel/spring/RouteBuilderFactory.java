package org.apache.camel.spring;

import java.util.ArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.FactoryBean;

public class RouteBuilderFactory implements FactoryBean {
	private ArrayList<RouteBuilderStatement> routes;
	private boolean singleton;

	class SpringRouteBuilder extends RouteBuilder {
		private ArrayList<RouteBuilderStatement> routes;

		@Override
		public void configure() {
			for (RouteBuilderStatement routeFactory : routes) {
				routeFactory.create(this);
			}
		}

		public ArrayList<RouteBuilderStatement> getRoutes() {
			return routes;
		}
		public void setRoutes(ArrayList<RouteBuilderStatement> routes) {
			this.routes = routes;
		}
	}
	
	public Object getObject() throws Exception {
		SpringRouteBuilder builder = new SpringRouteBuilder();
		builder.setRoutes(routes);
		return builder;
	}

	public Class getObjectType() {
		return SpringRouteBuilder.class;
	}

	public boolean isSingleton() {
		return singleton;
	}
	public void setSingleton(boolean singleton) {
		this.singleton = singleton;
	}

	public ArrayList<RouteBuilderStatement> getRoutes() {
		return routes;
	}
	public void setRoutes(ArrayList<RouteBuilderStatement> routes) {
		this.routes = routes;
	}


}
