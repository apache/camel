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
package org.apache.camel.processor.aggregate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;

/**
 * An {@link AggregationStrategy} that adapts to a POJO.
 * <p/>
 * This allows end users to use POJOs for the aggregation logic, instead of having to implement the
 * Camel API {@link AggregationStrategy}.
 */
public final class AggregationStrategyBeanAdapter extends ServiceSupport implements AggregationStrategy, CamelContextAware {

    private static final List<Method> EXCLUDED_METHODS = new ArrayList<Method>();
    private CamelContext camelContext;
    private Object pojo;
    private final Class<?> type;
    private String methodName;
    private boolean allowNullOldExchange;
    private boolean allowNullNewExchange;
    private volatile AggregationStrategyMethodInfo mi;

    static {
        // exclude all java.lang.Object methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Object.class.getMethods()));
        // exclude all java.lang.reflect.Proxy methods as we dont want to invoke them
        EXCLUDED_METHODS.addAll(Arrays.asList(Proxy.class.getMethods()));
    }

    /**
     * Creates this adapter.
     *
     * @param pojo the pojo to use.
     */
    public AggregationStrategyBeanAdapter(Object pojo) {
        this(pojo, null);
    }

    /**
     * Creates this adapter.
     *
     * @param type the class type of the pojo
     */
    public AggregationStrategyBeanAdapter(Class<?> type) {
        this(type, null);
    }

    /**
     * Creates this adapter.
     *
     * @param pojo the pojo to use.
     * @param methodName the name of the method to call
     */
    public AggregationStrategyBeanAdapter(Object pojo, String methodName) {
        this.pojo = pojo;
        this.type = pojo.getClass();
        this.methodName = methodName;
    }

    /**
     * Creates this adapter.
     *
     * @param type the class type of the pojo
     * @param methodName the name of the method to call
     */
    public AggregationStrategyBeanAdapter(Class<?> type, String methodName) {
        this.type = type;
        this.pojo = null;
        this.methodName = methodName;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public boolean isAllowNullOldExchange() {
        return allowNullOldExchange;
    }

    public void setAllowNullOldExchange(boolean allowNullOldExchange) {
        this.allowNullOldExchange = allowNullOldExchange;
    }

    public boolean isAllowNullNewExchange() {
        return allowNullNewExchange;
    }

    public void setAllowNullNewExchange(boolean allowNullNewExchange) {
        this.allowNullNewExchange = allowNullNewExchange;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (!allowNullOldExchange && oldExchange == null) {
            return newExchange;
        }
        if (!allowNullNewExchange && newExchange == null) {
            return oldExchange;
        }

        try {
            Object out = mi.invoke(pojo, oldExchange, newExchange);
            if (out != null) {
                if (oldExchange != null) {
                    oldExchange.getIn().setBody(out);
                } else {
                    newExchange.getIn().setBody(out);
                }
            }
        } catch (Exception e) {
            if (oldExchange != null) {
                oldExchange.setException(e);
            } else {
                newExchange.setException(e);
            }
        }
        return oldExchange != null ? oldExchange : newExchange;
    }

    /**
     * Validates whether the given method is valid.
     *
     * @param method  the method
     * @return true if valid, false to skip the method
     */
    protected boolean isValidMethod(Method method) {
        // must not be in the excluded list
        for (Method excluded : EXCLUDED_METHODS) {
            if (method.equals(excluded)) {
                return false;
            }
        }

        // must be a public method
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }

        // return type must not be void and it should not be a bridge method
        if (method.getReturnType().equals(Void.TYPE) || method.isBridge()) {
            return false;
        }

        return true;
    }

    private static boolean isStaticMethod(Method method) {
        return Modifier.isStatic(method.getModifiers());
    }

    @Override
    protected void doStart() throws Exception {
        Method found = null;
        if (methodName != null) {
            for (Method method : type.getMethods()) {
                if (isValidMethod(method) && method.getName().equals(methodName)) {
                    if (found == null) {
                        found = method;
                    } else {
                        throw new IllegalArgumentException("The bean " + type + " has 2 or more methods with the name " + methodName);
                    }
                }
            }
        } else {
            for (Method method : type.getMethods()) {
                if (isValidMethod(method)) {
                    if (found == null) {
                        found = method;
                    } else {
                        throw new IllegalArgumentException("The bean " + type + " has 2 or more methods and no explicit method name was configured.");
                    }
                }
            }
        }

        if (found == null) {
            throw new UnsupportedOperationException("Cannot find a valid method with name: " + methodName + " on bean type: " + type);
        }

        // if its not a static method then we must have an instance of the pojo
        if (!isStaticMethod(found) && pojo == null) {
            pojo = camelContext.getInjector().newInstance(type);
        }

        // create the method info which has adapted to the pojo
        AggregationStrategyBeanInfo bi = new AggregationStrategyBeanInfo(type, found);
        mi = bi.createMethodInfo();

        // in case the POJO is CamelContextAware
        if (pojo instanceof CamelContextAware) {
            ((CamelContextAware) pojo).setCamelContext(getCamelContext());
        }

        // in case the pojo is a service
        ServiceHelper.startService(pojo);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(pojo);
    }
}
