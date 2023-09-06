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

import org.apache.camel.AsyncCallback;
import org.apache.camel.BeanScope;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.Processor;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Processor} which converts the inbound exchange to a method invocation on a POJO
 */
public abstract class AbstractBeanProcessor extends AsyncProcessorSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanProcessor.class);

    private final BeanHolder beanHolder;
    private transient Processor processor;
    private transient Object bean;
    private transient boolean lookupProcessorDone;
    private final Object lock = new Object();
    private BeanScope scope;
    private String method;
    private boolean shorthandMethod;

    protected AbstractBeanProcessor(Object pojo, BeanInfo beanInfo) {
        this(new ConstantBeanHolder(pojo, beanInfo));
    }

    protected AbstractBeanProcessor(BeanHolder beanHolder) {
        this.beanHolder = beanHolder;
    }

    @Override
    public String toString() {
        return "BeanProcessor[" + beanHolder + (method != null ? "#" + method : "") + "]";
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // do we have an explicit method name we always should invoke (either configured on endpoint or as a header)
        final String explicitMethodName = exchange.getIn().getHeader(BeanConstants.BEAN_METHOD_NAME, method, String.class);

        final Object beanInstance;
        final BeanInfo beanInfo;
        try {
            beanInstance = beanHolder.getBean(exchange);
            // get bean info for this bean instance (to avoid thread issue)
            beanInfo = doGetBeanInfo(beanInstance);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }

        // do we have a custom adapter for this POJO to a Processor
        // but only do this if allowed
        // we need to check beanHolder is Processor is support, to avoid the bean cached issue
        if (allowProcessor(explicitMethodName, beanInfo)) {
            final Processor target = getCustomAdapter(exchange, beanInstance);
            if (target != null) {
                useCustomAdapter(exchange, callback, target);

                return true;
            }
        }

        return useMethodInvocation(exchange, callback, explicitMethodName, beanInfo, beanInstance);
    }

    private static boolean useMethodInvocation(
            Exchange exchange, AsyncCallback callback, String explicitMethodName, BeanInfo beanInfo, Object beanInstance) {
        final Message in = exchange.getIn();

        // set explicit method name to invoke as a header, which is how BeanInfo can detect it
        if (explicitMethodName != null) {
            in.setHeader(BeanConstants.BEAN_METHOD_NAME, explicitMethodName);
        }

        final MethodInvocation invocation;
        try {
            invocation = beanInfo.createInvocation(beanInstance, exchange);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        } finally {
            // must remove headers as they were provisional
            if (explicitMethodName != null) {
                in.removeHeader(Exchange.BEAN_METHOD_NAME);
            }
        }

        if (invocation == null) {
            exchange.setException(new IllegalStateException(
                    "No method invocation could be created, no matching method could be found on: " + beanInstance));
            callback.done(true);
            return true;
        }

        // invoke invocation
        return invocation.proceed(callback);
    }

    private Processor getCustomAdapter(Exchange exchange, Object beanTmp) {
        Processor target = getProcessor();
        if (target == null) {
            // only attempt to lookup the processor once or nearly once
            // allow cache by default or if the scope is singleton
            boolean allowCache = scope == null || scope == BeanScope.Singleton;
            if (allowCache) {
                if (!lookupProcessorDone) {
                    synchronized (lock) {
                        lookupProcessorDone = true;
                        // so if there is a custom type converter for the bean to processor
                        target = exchange.getContext().getTypeConverter().tryConvertTo(Processor.class, exchange, beanTmp);
                        processor = target;
                    }
                }
            } else {
                // so if there is a custom type converter for the bean to processor
                target = exchange.getContext().getTypeConverter().tryConvertTo(Processor.class, exchange, beanTmp);
            }
        }
        return target;
    }

    private static void useCustomAdapter(Exchange exchange, AsyncCallback callback, Processor target) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Using a custom adapter as bean invocation: {}", target);
        }
        try {
            target.process(exchange);
        } catch (AssertionError | Exception e) {
            exchange.setException(e);
        } finally {
            callback.done(true);
        }
    }

    private BeanInfo doGetBeanInfo(Object beanTmp) {
        BeanInfo beanInfo = beanHolder.getBeanInfo(beanTmp);
        if (beanInfo == null) {
            // fallback and use old way
            beanInfo = beanHolder.getBeanInfo();
        }
        return beanInfo;
    }

    protected Processor getProcessor() {
        return processor;
    }

    protected BeanHolder getBeanHolder() {
        return this.beanHolder;
    }

    public Object getBean() {
        return beanHolder.getBean(null);
    }

    // Properties
    // -----------------------------------------------------------------------

    public String getMethod() {
        return method;
    }

    public BeanScope getScope() {
        return scope;
    }

    public void setScope(BeanScope scope) {
        this.scope = scope;
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
     * Sets whether to support getter style method name, so you can say the method is called 'name' but it will invoke
     * the 'getName' method.
     * <p/>
     * Is by default turned off.
     */
    public void setShorthandMethod(boolean shorthandMethod) {
        this.shorthandMethod = shorthandMethod;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected void doInit() throws Exception {
        // optimize to only get (create) a processor if really needed
        if (beanHolder.supportProcessor() && allowProcessor(method, beanHolder.getBeanInfo())) {
            processor = beanHolder.getProcessor();
            ServiceHelper.initService(processor);
        } else if (beanHolder instanceof ConstantBeanHolder) {
            try {
                // Start the bean if it implements Service interface and if cached so meant to be reused
                bean = beanHolder.getBean(null);
                ServiceHelper.initService(bean);
            } catch (NoSuchBeanException e) {
                // ignore
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (processor != null) {
            ServiceHelper.startService(processor);
        } else if (bean != null) {
            ServiceHelper.startService(bean);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (processor != null) {
            ServiceHelper.stopService(processor);
        } else if (bean != null) {
            ServiceHelper.stopService(bean);
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        if (processor != null) {
            ServiceHelper.stopAndShutdownService(processor);
        } else if (bean != null) {
            ServiceHelper.stopAndShutdownService(bean);
        }
    }

    private boolean allowProcessor(String explicitMethodName, BeanInfo info) {
        if (explicitMethodName != null) {
            // don't allow if explicit method name is given, as we then must invoke this method
            return false;
        }

        // don't allow if any of the methods has a @Handler annotation
        // as the @Handler annotation takes precedence and is supposed to trigger invocation
        // of the given method
        if (info.hasAnyMethodHandlerAnnotation()) {
            return false;
        }

        // fallback and allow using the processor
        return true;
    }
}
