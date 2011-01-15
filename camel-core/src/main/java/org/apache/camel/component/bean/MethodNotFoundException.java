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

import java.util.List;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;

/**
 * @version $Revision$
 */
public class MethodNotFoundException extends CamelExchangeException {
    private static final long serialVersionUID = -7411465307141051012L;

    private final Object bean;
    private final String methodName;
    @SuppressWarnings("rawtypes")
    private final List<Class> parameterTypes;

    public MethodNotFoundException(Exchange exchange, Object pojo, String methodName) {
        this(exchange, pojo, methodName, null);
    }
    
    @SuppressWarnings("rawtypes")
    public MethodNotFoundException(Exchange exchange, Object pojo, String methodName, List<Class> parameterTypes) {
        super("Method with name: " + methodName + " and parameter types: " + parameterTypes + " not found on bean: " + pojo, exchange);
        this.methodName = methodName;
        this.bean = pojo;
        this.parameterTypes = parameterTypes;
    }

    public String getMethodName() {
        return methodName;
    }

    public Object getBean() {
        return bean;
    }

    @SuppressWarnings("rawtypes")
    public List<Class> getParameterTypes() {
        return parameterTypes;
    }
}