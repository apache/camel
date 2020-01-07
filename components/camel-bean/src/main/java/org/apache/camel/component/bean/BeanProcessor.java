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

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.service.ServiceSupport;

public class BeanProcessor extends ServiceSupport implements AsyncProcessor {

    private final DelegateBeanProcessor delegate;

    public BeanProcessor(Object pojo, BeanInfo beanInfo) {
        this.delegate = new DelegateBeanProcessor(pojo, beanInfo);
    }

    public BeanProcessor(Object pojo, CamelContext camelContext, ParameterMappingStrategy parameterMappingStrategy) {
        this.delegate = new DelegateBeanProcessor(pojo, camelContext, parameterMappingStrategy);
    }

    public BeanProcessor(Object pojo, CamelContext camelContext) {
        this.delegate = new DelegateBeanProcessor(pojo, camelContext);
    }

    public BeanProcessor(BeanHolder beanHolder) {
        this.delegate = new DelegateBeanProcessor(beanHolder);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        delegate.process(exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return delegate.process(exchange, callback);
    }

    @Override
    public CompletableFuture<Exchange> processAsync(Exchange exchange) {
        return delegate.processAsync(exchange);
    }

    public Processor getProcessor() {
        return delegate.getProcessor();
    }

    public BeanHolder getBeanHolder() {
        return delegate.getBeanHolder();
    }

    public Object getBean() {
        return delegate.getBean();
    }

    public String getMethod() {
        return delegate.getMethod();
    }

    public void setMethod(String method) {
        delegate.setMethod(method);
    }

    public BeanScope getScope() {
        return delegate.getScope();
    }

    public void setScope(BeanScope scope) {
        delegate.setScope(scope);
    }

    public boolean isShorthandMethod() {
        return delegate.isShorthandMethod();
    }

    public void setShorthandMethod(boolean shorthandMethod) {
        delegate.setShorthandMethod(shorthandMethod);
    }

    @Override
    protected void doStart() throws Exception {
        delegate.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        delegate.doStop();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private static final class DelegateBeanProcessor extends AbstractBeanProcessor {

        public DelegateBeanProcessor(Object pojo, BeanInfo beanInfo) {
            super(pojo, beanInfo);
        }

        public DelegateBeanProcessor(Object pojo, CamelContext camelContext, ParameterMappingStrategy parameterMappingStrategy) {
            super(pojo, camelContext, parameterMappingStrategy);
        }

        public DelegateBeanProcessor(Object pojo, CamelContext camelContext) {
            super(pojo, camelContext);
        }

        public DelegateBeanProcessor(BeanHolder beanHolder) {
            super(beanHolder);
        }
    }

}
