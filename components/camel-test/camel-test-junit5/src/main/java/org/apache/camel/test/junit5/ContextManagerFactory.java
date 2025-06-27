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
package org.apache.camel.test.junit5;

/**
 * A factory class for creating context managers
 */
public class ContextManagerFactory {

    /**
     * Callback types
     */
    public enum Type {
        BEFORE_ALL,
        BEFORE_EACH,
    }

    /**
     * Creates a new context manager
     *
     * @param  type                 callback type that initiated the request
     * @param  testConfiguration    the test configuration instance
     * @param  contextConfiguration the context configuration instance
     * @return                      a new context manager instance
     */
    public CamelContextManager createContextManager(
            Type type, TestExecutionConfiguration testConfiguration, CamelContextConfiguration contextConfiguration) {
        return switch (type) {
            case BEFORE_ALL:
                yield new LegacyCamelContextManager(testConfiguration, contextConfiguration);
            case BEFORE_EACH:
                yield new TransientCamelContextManager(testConfiguration, contextConfiguration);
        };
    }
}
