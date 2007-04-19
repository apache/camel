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

import java.util.ArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

/**
 * A {@link FactoryBean} which creates a RouteBuilder by parsing an XML file
 *
 * @version $Revision: 521369 $
 */
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
