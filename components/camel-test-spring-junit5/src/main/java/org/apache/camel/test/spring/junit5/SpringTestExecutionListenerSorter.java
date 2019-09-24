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
package org.apache.camel.test.spring.junit5;

import org.springframework.core.Ordered;

/**
 * This class centralizes the order of execution of spring test execution listeners:
 * <ol>
 *  <li>{@link CamelSpringTestContextLoaderTestExecutionListener}</li>
 *  <li>{@link DisableJmxTestExecutionListener}</li>
 *  <li>{@link CamelSpringBootExecutionListener}</li>
 *  <li>{@link StopWatchTestExecutionListener}</li>
 *  <li>Spring default listeners</li>
 * </ol>
 */
public final class SpringTestExecutionListenerSorter {

    private SpringTestExecutionListenerSorter() {
    }

    public static int getPrecedence(Class<?> clazz) {
        if (clazz == StopWatchTestExecutionListener.class) {
            return Ordered.HIGHEST_PRECEDENCE + 4000;
        } else if (clazz == CamelSpringBootExecutionListener.class) {
            return Ordered.HIGHEST_PRECEDENCE + 3000;
        } else if (clazz == DisableJmxTestExecutionListener.class) {
            return Ordered.HIGHEST_PRECEDENCE + 2000;
        } else if (clazz == CamelSpringTestContextLoaderTestExecutionListener.class) {
            return Ordered.HIGHEST_PRECEDENCE + 1000;
        }
        throw new IllegalArgumentException("Impossible to get the precedence of the class " + clazz.getName() + ".");
    }

}
