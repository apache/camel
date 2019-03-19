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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.spi.Injector;
import org.apache.camel.support.ObjectHelper;

/**
 * A default implementation of {@link Injector} which just uses reflection to
 * instantiate new objects using their zero argument constructor,
 * and then performing bean post processing using {@link DefaultCamelBeanPostProcessor}.
 * <p/>
 * For more complex implementations try the Spring or Guice implementations.
 */
public class DefaultInjector implements Injector  {

    // use the reflection injector
    private final CamelBeanPostProcessor postProcessor;

    public DefaultInjector(CamelContext context) {
        postProcessor = context.getBeanPostProcessor();
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        T answer = ObjectHelper.newInstance(type);
        if (answer != null) {
            try {
                postProcessor.postProcessBeforeInitialization(answer, answer.getClass().getName());
                postProcessor.postProcessAfterInitialization(answer, answer.getClass().getName());
            } catch (Exception e) {
                throw new RuntimeCamelException("Error during post processing of bean " + answer, e);
            }
        }
        return answer;
    }

    @Override
    public boolean supportsAutoWiring() {
        return false;
    }
}
