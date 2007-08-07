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

import org.apache.camel.Exchange;

public class BeanInvocation {

    private final Object proxy;
    private final Method method;
    private final Object[] args;

    public BeanInvocation(Object proxy, Method method, Object[] args) {
        this.proxy = proxy;
        this.method = method;
        this.args = args;
    }

    public Object[] getArgs() {
        return args;
    }

    public Method getMethod() {
        return method;
    }

    public Object getProxy() {
        return proxy;
    }

    /**
     * This causes us to invoke the endpoint Pojo using reflection.
     * 
     * @param pojo
     */
    public void invoke(Object pojo, Exchange exchange) {
        try {
            Object response = getMethod().invoke(pojo, getArgs());
            exchange.getOut().setBody(response);
        } catch (InvocationTargetException e) {
            exchange.setException(e.getCause());
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
