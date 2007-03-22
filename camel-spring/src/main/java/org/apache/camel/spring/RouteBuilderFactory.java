package org.apache.camel.spring;

import java.util.ArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

public class RouteBuilderFactory implements FactoryBean, BeanFactoryAware {
	private ArrayList<BuilderStatement> routes;
	private boolean singleton;
	private BeanFactory beanFactory;

	class SpringRouteBuilder extends RouteBuilder {
		private ArrayList<BuilderStatement> routes;
		private BeanFactory beanFactory;

		@Override
		public void configure() {
			for (BuilderStatement routeFactory : routes) {
				routeFactory.create(beanFactory, this);
			}
		}

		public ArrayList<BuilderStatement> getRoutes() {
			return routes;
		}
		public void setRoutes(ArrayList<BuilderStatement> routes) {
			this.routes = routes;
		}

		public void setBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}
	}
	
	public Object getObject() throws Exception {
		SpringRouteBuilder builder = new SpringRouteBuilder();
		builder.setBeanFactory(beanFactory);
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

	public ArrayList<BuilderStatement> getRoutes() {
		return routes;
	}
	public void setRoutes(ArrayList<BuilderStatement> routes) {
		this.routes = routes;
	}

	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

}
