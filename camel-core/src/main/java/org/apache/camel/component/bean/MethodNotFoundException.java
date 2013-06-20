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

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class MethodNotFoundException extends CamelExchangeException {
    private static final long serialVersionUID = -7411465307141051012L;

    private final Object bean;
    private final String methodName;

    public MethodNotFoundException(Exchange exchange, Object pojo, String methodName) {
        super("Method with name: " + methodName + " not found on bean: " + pojo + " of type: " + ObjectHelper.className(pojo), exchange);
        this.methodName = methodName;
        this.bean = pojo;
    }

    public MethodNotFoundException(Exchange exchange, Object pojo, String methodName, String postfix) {
        super("Method with name: " + methodName + " " + postfix + " not found on bean: " + pojo + " of type: " + ObjectHelper.className(pojo), exchange);
        this.methodName = methodName;
        this.bean = pojo;
    }

    public MethodNotFoundException(Exchange exchange, Class<?> type, String methodName, boolean isStaticMethod) {
        super((isStaticMethod ? "Static method" : "Method") + " with name: " + methodName + " not found on class: " + ObjectHelper.name(type), exchange);
        this.methodName = methodName;
        this.bean = null;
    }

    public MethodNotFoundException(Object pojo, String methodName, Throwable cause) {
        super("Method with name: " + methodName + " not found on bean: " + pojo + " of type:" + ObjectHelper.className(pojo), null, cause);
        this.methodName = methodName;
        this.bean = pojo;
    }

    public MethodNotFoundException(Class<?> type, String methodName, Throwable cause) {
        super("Method with name: " + methodName + " not found on class: " + ObjectHelper.className(type), null, cause);
        this.methodName = methodName;
        this.bean = null;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getBean() {
        return bean;
    }
}