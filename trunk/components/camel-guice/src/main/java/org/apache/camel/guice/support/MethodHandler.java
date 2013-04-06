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
package org.apache.camel.guice.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Allows a method with a given annotation {@code A} on an injectee of type
 * {@code I} to be processed in some way on each injectee using a custom
 * strategy.
 * 
 * @version
 */
public interface MethodHandler<I, A extends Annotation> {

    /**
     * Process the given method which is annotated with the annotation on the
     * injectee after the injectee has been injected
     */
    void afterInjection(I injectee, A annotation, Method method)
        throws InvocationTargetException, IllegalAccessException;
}
