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

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;

/**
 * SPI that serialises Camel route model definitions to their YAML DSL representation.
 * <p/>
 * The implementation is provided by {@code camel-yaml-io} and discovered via the service key {@link #FACTORY}. Callers
 * supply a {@link org.apache.camel.CamelContext} and a {@link org.apache.camel.NamedNode} route definition; the dumper
 * returns a formatted YAML string. The overloaded
 * {@link #dumpModelAsYaml(org.apache.camel.CamelContext, org.apache.camel.NamedNode, boolean, boolean, boolean, boolean)
 * dumpModelAsYaml} variant adds control over placeholder resolution, URI expansion into key-value parameters,
 * auto-generated ID inclusion, and source-location metadata. Separate methods handle bean definitions and global
 * data-format definitions. The primary consumers are {@code camel-jbang} export commands, the developer console, and
 * {@link org.apache.camel.CamelContext} route-dump helpers.
 * <p/>
 * See <a href="https://camel.apache.org/manual/camel-jbang.html">Camel CLI (camel-jbang)</a> for the export commands
 * that use this dumper.
 *
 * @see   ModelToXMLDumper
 * @see   ModelToJavaDumper
 * @since 4.0
 */
public interface ModelToYAMLDumper {

    /**
     * Service factory key.
     */
    String FACTORY = "modelyaml-dumper";

    /**
     * Dumps the definition as YAML
     *
     * @param  context    the CamelContext
     * @param  definition the definition, such as a {@link NamedNode}
     * @return            the output in YAML (is formatted)
     * @throws Exception  is throw if error marshalling to YAML
     */
    String dumpModelAsYaml(CamelContext context, NamedNode definition) throws Exception;

    /**
     * Dumps the definition as YAML
     *
     * @param  context             the CamelContext
     * @param  definition          the definition, such as a {@link NamedNode}
     * @param  resolvePlaceholders whether to resolve property placeholders in the dumped YAML
     * @param  uriAsParameters     whether to expand uri into a key/value parameters
     * @param  generatedIds        whether to include auto generated IDs
     * @param  sourceLocation      whether to include source location:line
     * @return                     the output in YAML (is formatted)
     * @throws Exception           is throw if error marshalling to YAML
     */
    String dumpModelAsYaml(
            CamelContext context, NamedNode definition,
            boolean resolvePlaceholders, boolean uriAsParameters, boolean generatedIds,
            boolean sourceLocation)
            throws Exception;

    /**
     * Dumps the beans as YAML
     *
     * @param  context   the CamelContext
     * @param  beans     list of beans (BeanFactoryDefinition)
     * @return           the output in YAML (is formatted)
     * @throws Exception is throw if error marshalling to YAML
     */
    String dumpBeansAsYaml(CamelContext context, List<Object> beans) throws Exception;

    /**
     * Dumps the global data formats as YAML
     *
     * @param  context     the CamelContext
     * @param  dataFormats list of data formats (DataFormatDefinition)
     * @return             the output in YAML (is formatted)
     * @throws Exception   is throw if error marshalling to YAML
     */
    String dumpDataFormatsAsYaml(CamelContext context, Map<String, Object> dataFormats) throws Exception;

}
