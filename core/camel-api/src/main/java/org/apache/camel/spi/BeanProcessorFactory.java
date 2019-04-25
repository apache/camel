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

import java.lang.reflect.Method;

import org.apache.camel.CamelContext;
import org.apache.camel.Processor;

/**
 * Factory for creating a {@link Processor} that can invoke a method on a bean and supporting using Camel
 * bean parameter bindings.
 * <p/>
 * This requires to have camel-bean on the classpath.
 */
public interface BeanProcessorFactory {

    /**
     * Creates the bean processor
     *
     * @param camelContext  the camel context
     * @param pojo          the bean
     * @param method        the method to invoke
     * @return the created processor
     * @throws Exception is thrown if error creating the processor
     */
    Processor createBeanProcessor(CamelContext camelContext, Object pojo, Method method) throws Exception;
}
