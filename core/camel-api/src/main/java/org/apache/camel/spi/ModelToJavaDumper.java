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
 * SPI that serialises Camel route model definitions to their Java DSL representation (source code).
 * <p/>
 * The implementation is provided by {@code camel-java-io} and discovered via the service key {@link #FACTORY}. Callers
 * supply a {@link org.apache.camel.CamelContext} and a {@link org.apache.camel.NamedNode} route definition; the dumper
 * returns a formatted Java source string. The overloaded
 * {@link #dumpModelAsJava(org.apache.camel.CamelContext, org.apache.camel.NamedNode, boolean, boolean) dumpModelAsJava}
 * variant adds control over placeholder resolution and auto-generated ID inclusion. The primary consumers are
 * {@code camel-jbang} export commands that convert running integrations into a Java DSL project.
 * <p/>
 * See <a href="https://camel.apache.org/manual/camel-jbang.html">Camel CLI (camel-jbang)</a> for the export commands
 * that use this dumper.
 *
 * @see   ModelToXMLDumper
 * @see   ModelToYAMLDumper
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

    /**
     * Dumps the definition as Java DSL
     *
     * @param  context             the CamelContext
     * @param  definition          the definition, such as a {@link NamedNode}
     * @param  resolvePlaceholders whether to resolve property placeholders in the dumped Java DSL
     * @param  generatedIds        whether to include auto generated IDs
     * @param  sourceLocation      whether to include source location information as comments
     * @return                     the output in Java DSL (is formatted)
     * @throws Exception           is throw if error marshalling to Java DSL
     */
    default String dumpModelAsJava(
            CamelContext context, NamedNode definition,
            boolean resolvePlaceholders, boolean generatedIds, boolean sourceLocation)
            throws Exception {
        return dumpModelAsJava(context, definition, resolvePlaceholders, generatedIds);
    }

}
