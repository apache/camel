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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.Registry;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ObjectHelper;
import static org.apache.camel.util.ObjectHelper.isNullOrBlank;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A {@link Processor} which converts the inbound exchange to a method
 * invocation on a POJO
 * 
 * @version $Revision: $
 */
public class BeanProcessor extends ServiceSupport implements Processor {
    public static final String METHOD_NAME = "org.apache.camel.MethodName";
    private static final Log LOG = LogFactory.getLog(BeanProcessor.class);

    private final Object pojo;
    private final BeanInfo beanInfo;
    private Method method;
    private String methodName;
    private final Processor processor;

    public BeanProcessor(Object pojo, BeanInfo beanInfo) {
        this.pojo = pojo;
        this.beanInfo = beanInfo;
        this.processor = CamelContextHelper.convertTo(beanInfo.getCamelContext(), Processor.class, pojo);
    }

    public BeanProcessor(Object pojo, CamelContext camelContext, ParameterMappingStrategy parameterMappingStrategy) {
        this(pojo, new BeanInfo(camelContext, pojo.getClass(), parameterMappingStrategy));
    }

    public BeanProcessor(Object pojo, CamelContext camelContext) {
        this(pojo, camelContext, createParameterMappingStrategy(camelContext));
    }

    public static ParameterMappingStrategy createParameterMappingStrategy(CamelContext camelContext) {
        Registry registry = camelContext.getRegistry();
        ParameterMappingStrategy answer = registry.lookup(ParameterMappingStrategy.class.getName(),
                                                          ParameterMappingStrategy.class);
        if (answer == null) {
            answer = new DefaultParameterMappingStrategy();
        }
        return answer;
    }
    @Override
    public String toString() {
        String description = method != null ? " " + method : "";
        return "BeanProcessor[" + pojo + description + "]";
    }

    public void process(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug(">>>> invoking method for: " + exchange);
        }

        // do we have a custom adapter for this POJO to a Processor
        if (processor != null) {
            processor.process(exchange);
            return;
        }
        Message in = exchange.getIn();
        BeanInvocation beanInvoke = in.getBody(BeanInvocation.class);
        if (beanInvoke != null) {
            beanInvoke.invoke(pojo, exchange);
            return;
        }

        MethodInvocation invocation;
        if (method != null) {
            invocation = beanInfo.createInvocation(method, pojo, exchange);
        } else {
            // lets pass in the method name to use if its specified
            if (ObjectHelper.isNotNullAndNonEmpty(methodName)) {
                if (isNullOrBlank(in.getHeader(METHOD_NAME, String.class))) {
                    in.setHeader(METHOD_NAME, methodName);
                }
            }
            invocation = beanInfo.createInvocation(pojo, exchange);
        }
        if (invocation == null) {
            throw new IllegalStateException("No method invocation could be created, no maching method could be found on: " + pojo);
        }
        try {
            Object value = invocation.proceed();
            if (value != null) {
                exchange.getOut().setBody(value);
            }
        } catch (InvocationTargetException e) {
            // lets unwrap the exception
            Throwable cause = e.getTargetException();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            else {
                // TODO deal with errors!
                throw e;
            }
        } catch (Exception e) {
            throw e;
        } catch (Throwable throwable) {
            throw new Exception(throwable);
        }
    }

    // Properties
    // -----------------------------------------------------------------------

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(processor);
    }
}
