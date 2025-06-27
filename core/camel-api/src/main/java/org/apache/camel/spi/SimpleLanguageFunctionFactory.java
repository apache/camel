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

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;

/**
 * A factory for extending the simple language with functions from external components.
 * <p/>
 * This requires to have the external component JAR on the classpath.
 */
public interface SimpleLanguageFunctionFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "simple-function-factory";

    /**
     * Creates the {@link Expression} that performs the function.
     *
     * @param  camelContext the camel context
     * @param  function     the function
     * @param  index        index of the function in the literal input
     *
     * @return              the created function as an expression, or <tt>null</tt> if not supported by this factory.
     */
    Expression createFunction(CamelContext camelContext, String function, int index);

    /**
     * Creates the Java source code that performs the function (for csimple).
     *
     * @param  camelContext the camel context
     * @param  function     the function
     * @param  index        index of the function in the literal input
     * @return              the source code or <tt>null</tt> if not supported by this factory.
     */
    String createCode(CamelContext camelContext, String function, int index);

}
