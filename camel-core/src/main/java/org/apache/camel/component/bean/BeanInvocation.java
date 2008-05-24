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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Invocation of beans that can handle being serialized.
 */
public class BeanInvocation implements Externalizable {
    private Object[] args;
    private MethodBean methodBean;
    private transient Method method;

    public BeanInvocation() {
    }

    public BeanInvocation(Method method, Object[] args) {
        this.method = method;
        this.args = args;
    }

    @Override
    public String toString() {
        Object list = null;
        if (args != null) {
            list = Arrays.asList(args);
        }
        return "BeanInvocation " + method + " with " + list + "]";
    }

    public Object[] getArgs() {
        return args;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    /**
     * This causes us to invoke the endpoint Pojo using reflection.
     *
     * @param pojo     the bean on which to perform this invocation
     * @param exchange the exchange carrying the method invocation
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

    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        methodBean = ObjectHelper.cast(MethodBean.class, objectInput.readObject());
        try {
            method = methodBean.getMethod();
        } catch (NoSuchMethodException e) {
            throw IOHelper.createIOException(e);
        }
        args = ObjectHelper.cast(Object[].class, objectInput.readObject());
    }

    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        if (methodBean == null) {
            methodBean = new MethodBean(method);
        }
        objectOutput.writeObject(methodBean);
        objectOutput.writeObject(args);
    }
}
