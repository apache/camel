/**
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

import org.apache.camel.Component;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Endpoint for the bean component.
 *
 * @version 
 */
@UriEndpoint(scheme = "bean", producerOnly = true, label = "core,java")
public class BeanEndpoint extends ProcessorEndpoint {
    private BeanHolder beanHolder;
    @UriPath
    private String beanName;
    @UriParam(defaultValue = "false")
    private boolean cache;
    @UriParam(defaultValue = "false")
    @Deprecated
    private boolean multiParameterArray;
    @UriParam
    private String method;

    public BeanEndpoint() {
        init();
    }

    public BeanEndpoint(String endpointUri, Component component, BeanProcessor processor) {
        super(endpointUri, component, processor);
        init();
    }

    public BeanEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        init();
    }

    // Properties
    //-------------------------------------------------------------------------

    public String getBeanName() {
        return beanName;
    }

    /**
     * Sets the name of the bean to invoke
     */
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public boolean isMultiParameterArray() {
        return multiParameterArray;
    }

    /**
     * How to treat the parameters which are passed from the message body;
     * if it is true, the message body should be an array of parameters.
     * <p/>
     * Note: This option is used internally by Camel, and is not intended for end users to use.
     *
     * @deprecated this option is used internally by Camel, and is not intended for end users to use
     */
    @Deprecated
    public void setMultiParameterArray(boolean mpArray) {
        multiParameterArray = mpArray;
    }

    public boolean isCache() {
        return cache;
    }

    /**
     * If enabled, Camel will cache the result of the first Registry look-up.
     * Cache can be enabled if the bean in the Registry is defined as a singleton scope.
     */
    public void setCache(boolean cache) {
        this.cache = cache;
    }

    public String getMethod() {
        return method;
    }

    /**
     * Sets the name of the method to invoke on the bean
     */
    public void setMethod(String method) {
        this.method = method;
    }

    public BeanHolder getBeanHolder() {
        return beanHolder;
    }

    public void setBeanHolder(BeanHolder beanHolder) {
        this.beanHolder = beanHolder;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected String createEndpointUri() {
        return "bean:" + getBeanName() + (method != null ? "?method=" + method : "");
    }

    private void init() {
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    protected Processor createProcessor() throws Exception {
        BeanHolder holder = getBeanHolder();
        if (holder == null) {
            RegistryBean registryBean = new RegistryBean(getCamelContext(), beanName);
            if (cache) {
                holder = registryBean.createCacheHolder();
            } else {
                holder = registryBean;
            }
        }
        BeanProcessor processor = new BeanProcessor(holder);
        if (method != null) {
            processor.setMethod(method);
        }
        processor.setMultiParameterArray(isMultiParameterArray());

        return processor;
    }
}
