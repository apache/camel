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
package org.apache.camel.spring.remoting;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.component.bean.CamelInvocationHandler;
import org.apache.camel.util.CamelContextHelper;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * A Spring interceptor which sends a message exchange to an endpoint before the method is invoked
 * 
 * @version $Revision$
 */
public class SendBeforeInterceptor implements MethodInterceptor, CamelContextAware, InitializingBean, DisposableBean {
    private String uri;
    private CamelContext camelContext;
    private CamelInvocationHandler invocationHandler;
    private Producer producer;

    public Object invoke(MethodInvocation invocation) throws Throwable {
        invocationHandler.invoke(invocation.getThis(), invocation.getMethod(), invocation.getArguments());
        return invocation.proceed();
    }

    public void afterPropertiesSet() throws Exception {
        notNull(uri, "uri");
        notNull(camelContext, "camelContext");

        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, uri);
        producer = endpoint.createProducer();
        producer.start();
        invocationHandler = new CamelInvocationHandler(endpoint, producer);
    }

    public void destroy() throws Exception {
        if (producer != null) {
            producer.stop();
        }
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    // Properties
    //-----------------------------------------------------------------------
    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
