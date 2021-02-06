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
 * The method must have either of the following method signatures:
 * 
 * <pre>
 * void theMethodName(Message message) throws Exception;
 * 
 * Object theMethodName(Message message) throws Exception;
 * 
 * boolean theMethodName(Message message, AsyncCallback callback) throws Exception;
 * </pre>
 * 
 * If the method includes the {@link AsyncCallback} type, then the return value must be boolean, as part of the async
 * callback contract. Throwing exceptions is optional and can be omitted.
 * <p/>
 * This can be used by Component implementations that uses org.apache.camel.support.HeaderSelectorProducer.
 *
 * This requires to use Camel maven tooling (camel-package-maven-plugin) to generate java source code
 * that selects and invokes the method at runtime.
 *
 * @see Message#getHeader(String)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InvokeOnHeader {

    // TODO: Update javadoc as parameter binding has improved

    /**
     * Name of header.
     */
    String value();
}
