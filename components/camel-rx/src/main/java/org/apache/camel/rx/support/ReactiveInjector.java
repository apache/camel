/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.rx.support;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Injector;
import org.apache.camel.util.ReflectionInjector;

public class ReactiveInjector implements Injector {

    // use the reflection injector
    private final Injector delegate = new ReflectionInjector();
    private final ReactiveBeanPostProcessor postProcessor;

    public ReactiveInjector(CamelContext context) {
        postProcessor = new ReactiveBeanPostProcessor(context);
    }

    @Override
    public <T> T newInstance(Class<T> type) {
        T answer = delegate.newInstance(type);
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
    public <T> T newInstance(Class<T> type, Object instance) {
        T answer = delegate.newInstance(type, instance);
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
}
