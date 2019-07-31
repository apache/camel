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

import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;

/**
 * An exception thrown if an attempted method invocation resulted in an ambiguous method
 * such that multiple methods match the inbound message exchange
 */
public class AmbiguousMethodCallException extends RuntimeExchangeException {

    private final Collection<MethodInfo> methods;

    public AmbiguousMethodCallException(Exchange exchange, Collection<MethodInfo> methods) {
        super("Ambiguous method invocations possible: " + methods, exchange);
        this.methods = methods;
    }

    /**
     * The ambiguous methods for which a single method could not be chosen
     */
    public Collection<MethodInfo> getMethods() {
        return methods;
    }
}
