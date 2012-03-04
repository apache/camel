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
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Processor} which converts the inbound exchange to a method
 * invocation on a POJO
 *
 * @version 
 */
public class BeanProcessor extends ServiceSupport implements AsyncProcessor {
    private static final transient Logger LOG = LoggerFactory.getLogger(BeanProcessor.class);

    private boolean multiParameterArray;
    private Method methodObject;
    private String method;
    private BeanHolder beanHolder;
    private boolean shorthandMethod;

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

    public boolean process(Exchange exchange, AsyncCallback callback) {
        // do we have an explicit method name we always should invoke (either configured on endpoint or as a header)
        String explicitMethodName = exchange.getIn().getHeader(Exchange.BEAN_METHOD_NAME, method, String.class);

        Object bean;
        BeanInfo beanInfo;
        try {
            bean = beanHolder.getBean();
            beanInfo = beanHolder.getBeanInfo();
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // do we have a custom adapter for this POJO to a Processor
        // should not be invoked if an explicit method has been set
        Processor processor = getProcessor();
        if (explicitMethodName == null && processor != null) {
            LOG.trace("Using a custom adapter as bean invocation: {}", processor);
            try {
                processor.process(exchange);
            } catch (Throwable e) {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        Message in = exchange.getIn();

        // is the message proxied using a BeanInvocation?
        BeanInvocation beanInvoke = null;
        if (in.getBody() != null && in.getBody() instanceof BeanInvocation) {
            // BeanInvocation would be stored directly as the message body
            // do not force any type conversion attempts as it would just be unnecessary and cost a bit performance
            // so a regular instanceof check is sufficient
            beanInvoke = (BeanInvocation) in.getBody();
        }
        if (beanInvoke != null) {
            // Now it gets a bit complicated as ProxyHelper can proxy beans which we later
            // intend to invoke (for example to proxy and invoke using spring remoting).
            // and therefore the message body contains a BeanInvocation object.
            // However this can causes problem if we in a Camel route invokes another bean,
            // so we must test whether BeanHolder and BeanInvocation is the same bean or not
            LOG.trace("Exchange IN body is a BeanInvocation instance: {}", beanInvoke);
            Class<?> clazz = beanInvoke.getMethod().getDeclaringClass();
            boolean sameBean = clazz.isInstance(bean);
            if (LOG.isDebugEnabled()) {
                LOG.debug("BeanHolder bean: {} and beanInvocation bean: {} is same instance: {}", new Object[]{bean.getClass(), clazz, sameBean});
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

        MethodInvocation invocation;
        // set explicit method name to invoke as a header, which is how BeanInfo can detect it
        if (explicitMethodName != null) {
            in.setHeader(Exchange.BEAN_METHOD_NAME, explicitMethodName);
        }
        try {
            invocation = beanInfo.createInvocation(bean, exchange);
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        } finally {
            // must remove headers as they were provisional
            in.removeHeader(Exchange.BEAN_MULTI_PARAMETER_ARRAY);
            in.removeHeader(Exchange.BEAN_METHOD_NAME);
        }
        if (invocation == null) {
            throw new IllegalStateException("No method invocation could be created, no matching method could be found on: " + bean);
        }

        Object value;
        try {
            AtomicBoolean sync = new AtomicBoolean(true);
            value = invocation.proceed(callback, sync);
            if (!sync.get()) {
                LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                // the remainder of the routing will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());
        } catch (InvocationTargetException e) {
            // let's unwrap the exception when it's an invocation target exception
            exchange.setException(e.getCause());
            callback.done(true);
            return true;
        } catch (Throwable e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // if the method returns something then set the value returned on the Exchange
        if (!invocation.getMethod().getReturnType().equals(Void.TYPE) && value != Void.TYPE) {
            if (exchange.getPattern().isOutCapable()) {
                // force out creating if not already created (as its lazy)
                LOG.debug("Setting bean invocation result on the OUT message: {}", value);
                exchange.getOut().setBody(value);
                // propagate headers
                exchange.getOut().getHeaders().putAll(exchange.getIn().getHeaders());
            } else {
                // if not out then set it on the in
                LOG.debug("Setting bean invocation result on the IN message: {}", value);
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

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void doStart() throws Exception {
        ServiceHelper.startService(getProcessor());
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(getProcessor());
    }
}
