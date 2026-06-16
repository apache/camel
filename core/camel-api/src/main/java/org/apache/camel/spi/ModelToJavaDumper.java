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
import org.apache.camel.NamedNode;

/**
 * SPI for dumping model definitions into Java DSL representation.
 *
 * @since 4.21
 */
public interface ModelToJavaDumper {

    /**
     * Service factory key.
     */
    String FACTORY = "modeljava-dumper";

    /**
     * Dumps the definition as Java DSL
     *
     * @param  context    the CamelContext
     * @param  definition the definition, such as a {@link NamedNode}
     * @return            the output in Java DSL (is formatted)
     * @throws Exception  is throw if error marshalling to Java DSL
     */
    String dumpModelAsJava(CamelContext context, NamedNode definition) throws Exception;

    /**
     * Dumps the definition as Java DSL
     *
     * @param  context             the CamelContext
     * @param  definition          the definition, such as a {@link NamedNode}
     * @param  resolvePlaceholders whether to resolve property placeholders in the dumped Java DSL
     * @param  generatedIds        whether to include auto generated IDs
     * @return                     the output in Java DSL (is formatted)
     * @throws Exception           is throw if error marshalling to Java DSL
     */
    String dumpModelAsJava(
            CamelContext context, NamedNode definition,
            boolean resolvePlaceholders, boolean generatedIds)
            throws Exception;

}
