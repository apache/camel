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
package org.apache.camel.language.bean;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExpressionException;

/**
 * Exception thrown if invocation of bean failed.
 *
 * @version 
 */
public class RuntimeBeanExpressionException extends RuntimeExpressionException {
    private static final long serialVersionUID = -7184254079414493118L;

    private final Exchange exchange;
    private final String beanName;
    private final String method;

    public RuntimeBeanExpressionException(Exchange exchange, String beanName, String method, Throwable e) {
        super("Failed to invoke method: " + method + " on " + beanName + " due to: " + e, e);
        this.exchange = exchange;
        this.beanName = beanName;
        this.method = method;
    }

    public RuntimeBeanExpressionException(Exchange exchange, String beanName, String method, String message) {
        super("Failed to invoke method: " + method + " on " + beanName + " due " + message);
        this.exchange = exchange;
        this.beanName = beanName;
        this.method = method;
    }

    public String getBeanName() {
        return beanName;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public String getMethod() {
        return method;
    }
}
