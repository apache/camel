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

import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The <a href="http://camel.apache.org/bean.html">bean component</a> is for invoking Java beans from Camel.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "bean", title = "Bean", syntax = "bean:beanName", producerOnly = true, label = "core,java")
public class BeanEndpoint extends DefaultEndpoint {
    private transient BeanHolder beanHolder;
    private transient BeanProcessor processor;
    @UriPath(description = "Sets the name of the bean to invoke") @Metadata(required = "true")
    private String beanName;
    @UriParam(description = "Sets the name of the method to invoke on the bean")
    private String method;
    @UriParam(label = "advanced", description = "If enabled, Camel will cache the result of the first Registry look-up."
            + " Cache can be enabled if the bean in the Registry is defined as a singleton scope.")
    private boolean cache;
    @UriParam(label = "advanced", description = "How to treat the parameters which are passed from the message body."
            + "true means the message body should be an array of parameters.")
    @Deprecated @Metadata(deprecationNode = "This option is used internally by Camel, and is not intended for end users to use.")
    private boolean multiParameterArray;
    @UriParam(prefix = "bean.", label = "advanced", description = "Used for configuring additional properties on the bean", multiValue = true)
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

    @Override
    public boolean isSingleton() {
        return true;
    }

    public BeanProcessor getProcessor() {
        return processor;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (processor == null) {
            BeanHolder holder = getBeanHolder();
            if (holder == null) {
                RegistryBean registryBean = new RegistryBean(getCamelContext(), beanName);
                if (cache) {
                    holder = registryBean.createCacheHolder();
                } else {
                    holder = registryBean;
                }
            }
            processor = new BeanProcessor(holder);
            if (method != null) {
                processor.setMethod(method);
            }
            processor.setMultiParameterArray(isMultiParameterArray());
            if (parameters != null) {
                setProperties(processor, parameters);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
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

    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Used for configuring additional properties on the bean
     */
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
