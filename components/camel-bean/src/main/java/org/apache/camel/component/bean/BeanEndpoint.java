/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.bean;

import java.util.Map;

import org.apache.camel.BeanScope;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Invoke methods of Java beans stored in Camel registry.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "bean", title = "Bean", syntax = "bean:beanName", producerOnly = true,
             category = { Category.CORE, Category.SCRIPT }, headersClass = BeanConstants.class)
public class BeanEndpoint extends DefaultEndpoint {
    private transient BeanHolder beanHolder;
    private transient BeanProcessor processor;
    @UriPath(label = "common", description = "Sets the name of the bean to invoke")
    @Metadata(required = true)
    private String beanName;
    @UriParam(label = "common", description = "Sets the name of the method to invoke on the bean")
    private String method;
    @UriParam(label = "common", defaultValue = "Singleton", description = "Scope of bean."
                                                                          + " When using singleton scope (default) the bean is created or looked up only once and reused for the lifetime of the endpoint."
                                                                          + " The bean should be thread-safe in case concurrent threads is calling the bean at the same time."
                                                                          + " When using request scope the bean is created or looked up once per request (exchange). This can be used if you want to store state on a bean"
                                                                          + " while processing a request and you want to call the same bean instance multiple times while processing the request."
                                                                          + " The bean does not have to be thread-safe as the instance is only called from the same request."
                                                                          + " When using prototype scope, then the bean will be looked up or created per call. However in case of lookup then this is delegated "
                                                                          + " to the bean registry such as Spring or CDI (if in use), which depends on their configuration can act as either singleton or prototype scope."
                                                                          + " so when using prototype then this depends on the delegated registry.")
    private BeanScope scope = BeanScope.Singleton;
    @UriParam(prefix = "bean.", label = "advanced", description = "Used for configuring additional properties on the bean",
              multiValue = true)
    private Map<String, Object> parameters;

    public BeanEndpoint() {
        setExchangePattern(ExchangePattern.InOut);
    }

    public BeanEndpoint(String endpointUri, Component component, BeanProcessor processor) {
        super(endpointUri, component);
        this.processor = processor;
        setExchangePattern(ExchangePattern.InOut);
    }

    public BeanEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new BeanProducer(this, processor);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from a bean endpoint");
    }

    public BeanProcessor getProcessor() {
        return processor;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (processor == null) {
            BeanHolder holder = getBeanHolder();
            if (holder == null) {
                ParameterMappingStrategy strategy
                        = ParameterMappingStrategyHelper.createParameterMappingStrategy(getCamelContext());
                BeanComponent bean = getCamelContext().getComponent("bean", BeanComponent.class);
                RegistryBean registryBean
                        = new RegistryBean(getCamelContext(), beanName, strategy, bean);
                if (scope == BeanScope.Singleton) {
                    // if singleton then create a cached holder that use the same singleton instance
                    holder = registryBean.createCacheHolder();
                } else {
                    holder = registryBean;
                }
            }
            if (scope == BeanScope.Request) {
                // wrap in registry scoped
                holder = new RequestBeanHolder(holder);
            }
            processor = new BeanProcessor(holder);
            if (method != null) {
                processor.setMethod(method);
            }
            processor.setScope(scope);
            if (parameters != null) {
                holder.setOptions(parameters);
            }
        }
    }

    // Properties
    //-------------------------------------------------------------------------

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public BeanScope getScope() {
        return scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public BeanHolder getBeanHolder() {
        return beanHolder;
    }

    public void setBeanHolder(BeanHolder beanHolder) {
        this.beanHolder = beanHolder;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected String createEndpointUri() {
        return "bean:" + getBeanName() + (method != null ? "?method=" + method : "");
    }
}
