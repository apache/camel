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

import static org.apache.camel.util.ObjectHelper.notNull;

import java.util.ArrayList;

import org.apache.camel.CamelContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * A {@link FactoryBean} which creates a RouteBuilder by parsing an XML file. This factory bean
 * must be injected with a context and will then install the rules in the context when the routing rules
 * are created. 
 *
 * @version $Revision: 521369 $
 */
public class RouteBuilderFactoryBean implements FactoryBean, BeanFactoryAware, InitializingBean {
	private ArrayList<BuilderStatement> routes;
	private BeanFactory beanFactory;
    private CamelContext context;
    private StatementRouteBuilder builder = new StatementRouteBuilder();

    public Object getObject() throws Exception {
        return builder;
	}

	public Class getObjectType() {
		return StatementRouteBuilder.class;
	}

	public boolean isSingleton() {
		return true;
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

    public CamelContext getContext() {
        return context;
    }

    public void setContext(CamelContext context) {
        this.context = context;
    }

    public void afterPropertiesSet() throws Exception {
        notNull(context, "context");
        notNull(routes, "routes");
		builder.setBeanFactory(beanFactory);
		builder.setRoutes(routes);

        // now lets install the routes in the context
        context.addRoutes(builder);
    }
}
