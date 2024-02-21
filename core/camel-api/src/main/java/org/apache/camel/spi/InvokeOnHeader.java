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
package org.apache.camel.spi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Message;

/**
 * Marks a method as being invoked for a specific header value.
 * <p/>
 * The method can either be executed synchronously or asynchronously (with {@link AsyncCallback}.
 * <p/>
 * A method is only asynchronously executed if the method has {@link AsyncCallback} as a parameter. In this situation
 * then the method should not return a value (void). And its the responsible of the method to invoke
 *
 * <pre>
 * callback.done(false)
 * </pre>
 *
 * when to continue routing.
 * <p/>
 * Synchronous methods can either be void or return a value. If a value is returned then the value will be set as the
 * response body.
 * <p/>
 * The method accepts the following parameters:
 * <ul>
 * <li>Exchange - the current exchange</li>
 * <li>Message - the current message</li>
 * <li>CamelContext - the camel context</li>
 * <li>AsyncCallback - for asynchronous processing</li>
 * <li>Object - Object or any other type is regarded as the current message body, converted to the given type</li>
 * </ul>
 * Component implementation producers should extend org.apache.camel.support.HeaderSelectorProducer. And use Camel maven
 * tooling (camel-package-maven-plugin) to generate java source code that selects and invokes the method at runtime.
 *
 * @see Message#getHeader(String)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InvokeOnHeader {

    /**
     * Name of header.
     */
    String value();
}
