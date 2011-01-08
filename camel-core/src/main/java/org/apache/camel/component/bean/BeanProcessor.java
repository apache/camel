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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A {@link Processor} which converts the inbound exchange to a method
 * invocation on a POJO
 *
 * @version $Revision$
 */
public class BeanProcessor extends ServiceSupport implements AsyncProcessor {
    private static final transient Log LOG = LogFactory.getLog(BeanProcessor.class);

    private boolean multiParameterArray;
    private Method methodObject;
    private String method;
    private BeanHolder beanHolder;
    private boolean shorthandMethod;
    @SuppressWarnings("rawtypes")
    private Class type;

    public BeanProcessor(Object pojo, BeanInfo beanInfo) {
        this(new ConstantBeanHolder(pojo, beanInfo));
    }

    public BeanProcessor(Object pojo, CamelContext camelContext, ParameterMappingStrategy parameterMappingStrategy) {
        this(pojo, new BeanInfo(camelContext, pojo.getClass(), parameterMappingStrategy));
    }

    public BeanProcessor(Object pojo, CamelContext camelContext) {
        this(pojo, camelContext, BeanInfo.createParameterMappingStrategy(camelContext));
    }

    public BeanProcessor(BeanHolder beanHolder) {
        this.beanHolder = beanHolder;
    }

    @Override
    public String toString() {
        String description = methodObject != null ? " " + methodObject : "";
        return "BeanProcessor[" + beanHolder + description + "]";
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    @SuppressWarnings({ "unused", "rawtypes" })
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // do we have an explicit method name we always should invoke
        boolean isExplicitMethod = ObjectHelper.isNotEmpty(method);
        // do we have an explicit parameter type we should invoke if we have multiple possible
        // methods
        boolean isExplicitType = ObjectHelper.isNotEmpty(type);

        Object bean = beanHolder.getBean();
        BeanInfo beanInfo = beanHolder.getBeanInfo();

        // do we have a custom adapter for this POJO to a Processor
        // should not be invoked if an explicit method has been set
        Processor processor = getProcessor();
        if (!isExplicitMethod && processor != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using a custom adapter as bean invocation: " + processor);
            }
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        Message in = exchange.getIn();
        
        // Now it gets a bit complicated as ProxyHelper can proxy beans which we later
        // intend to invoke (for example to proxy and invoke using spring remoting).
        // and therefore the message body contains a BeanInvocation object.
        // However this can causes problem if we in a Camel route invokes another bean,
        // so we must test whether BeanHolder and BeanInvocation is the same bean or not
        BeanInvocation beanInvoke = in.getBody(BeanInvocation.class);
        if (beanInvoke != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Exchange IN body is a BeanInvocation instance: " + beanInvoke);
            }
            Class<?> clazz = beanInvoke.getMethod().getDeclaringClass();
            boolean sameBean = clazz.isInstance(bean);
            if (LOG.isTraceEnabled()) {
                LOG.debug("BeanHolder bean: " + bean.getClass() + " and beanInvocation bean: " + clazz + " is same instance: " + sameBean);
            }
            if (sameBean) {
                beanInvoke.invoke(bean, exchange);
                // propagate headers
                exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
                callback.done(true);
                return true;
            }
        }
        
        // set temporary header which is a hint for the bean info that introspect the bean
        if (in.getHeader(Exchange.BEAN_MULTI_PARAMETER_ARRAY) == null) {
            in.setHeader(Exchange.BEAN_MULTI_PARAMETER_ARRAY, isMultiParameterArray());
        }

        String prevMethod = null;
        Class prevType = null;
        MethodInvocation invocation;
        if (methodObject != null) {
            invocation = beanInfo.createInvocation(methodObject, bean, exchange);
        } else {
            // we just override the bean's invocation method name here
            if (isExplicitMethod) {
                prevMethod = in.getHeader(Exchange.BEAN_METHOD_NAME, String.class);
                in.setHeader(Exchange.BEAN_METHOD_NAME, method);
            }
            if (isExplicitType) {
                prevType = in.getHeader(Exchange.BEAN_TYPE_NAME, Class.class);
                in.setHeader(Exchange.BEAN_TYPE_NAME, type);
            }
            try {
                invocation = beanInfo.createInvocation(bean, exchange);
            } catch (Throwable e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }
        if (invocation == null) {
            throw new IllegalStateException("No method invocation could be created, no matching method could be found on: " + bean);
        }

        // remove temporary header
        in.removeHeader(Exchange.BEAN_MULTI_PARAMETER_ARRAY);

        Object value = null;
        try {
            AtomicBoolean sync = new AtomicBoolean(true);
            value = invocation.proceed(callback, sync);
            if (!sync.get()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed asynchronously");
                }
                // the remainder of the routing will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed synchronously");
            }
        } catch (InvocationTargetException e) {
            // lets unwrap the exception when its an invocation target exception
            exchange.setException(e.getCause());
            callback.done(true);
            return true;
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        } finally {
            if (isExplicitMethod) {
                in.setHeader(Exchange.BEAN_METHOD_NAME, prevMethod);
            }
        }

        // if the method returns something then set the value returned on the Exchange
        if (!invocation.getMethod().getReturnType().equals(Void.TYPE) && value != Void.TYPE) {
            if (exchange.getPattern().isOutCapable()) {
                // force out creating if not already created (as its lazy)
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Setting bean invocation result on the OUT message: " + value);
                }
                exchange.getOut().setBody(value);
                // propagate headers
                exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            } else {
                // if not out then set it on the in
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Setting bean invocation result on the IN message: " + value);
                }
                exchange.getIn().setBody(value);
            }
        }

        callback.done(true);
        return true;
    }

    protected Processor getProcessor() {
        return beanHolder.getProcessor();
    }

    public Object getBean() {
        return beanHolder.getBean();
    }

    // Properties
    // -----------------------------------------------------------------------

    public Method getMethodObject() {
        return methodObject;
    }

    public void setMethodObject(Method methodObject) {
        this.methodObject = methodObject;
    }

    public String getMethod() {
        return method;
    }

    public boolean isMultiParameterArray() {
        return multiParameterArray;
    }

    public void setMultiParameterArray(boolean mpArray) {
        multiParameterArray = mpArray;
    }

    /**
     * Sets the method name to use
     */
    public void setMethod(String method) {
        this.method = method;
    }

    public boolean isShorthandMethod() {
        return shorthandMethod;
    }

    /**
     * Sets whether to support getter style method name, so you can
     * say the method is called 'name' but it will invoke the 'getName' method.
     * <p/>
     * Is by default turned off.
     */
    public void setShorthandMethod(boolean shorthandMethod) {
        this.shorthandMethod = shorthandMethod;
    }
    
    @SuppressWarnings("rawtypes")
    public Class getType() {
        return type;
    }

    /**
     * Sets the type/class name to which the body should converted before the suitable method is
     * determined.
     * @param type
     */
    @SuppressWarnings("rawtypes")
    public void setType(Class type) {
        this.type = type;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void doStart() throws Exception {
        ServiceHelper.startService(getProcessor());
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(getProcessor());
    }
}
