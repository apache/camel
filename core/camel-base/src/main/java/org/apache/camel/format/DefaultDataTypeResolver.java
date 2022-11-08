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

package org.apache.camel.format;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.spi.InputType;
import org.apache.camel.spi.OutputType;
import org.apache.camel.spi.annotations.DataType;

import java.util.*;

/**
 * DataType resolver able to resolve data type implementations. First looks for data types defined on the component level.
 * As a fallback looks for a default data type implementation registered in the Camel context.
 * The Lookup is based on the component scheme and the given data type name.
 */
//ToDo: Add resource lookup for data types defined by components
public class DefaultDataTypeResolver implements CamelContextAware {

    private CamelContext camelContext;

    private final Map<String, List<OutputType>> componentOutputTypes = new HashMap<>();
    private final Map<String, List<InputType>> componentInputTypes = new HashMap<>();

    /**
     * Get output type for given component scheme and format name.
     * @param scheme
     * @param format
     * @return
     */
    public Optional<OutputType> getOutputType(String scheme, String format) {
        if (format == null) {
            return Optional.empty();
        }

        Optional<OutputType> componentDataType = getComponentOutputTypes(scheme).stream()
                .filter(dt -> dt.getClass().isAnnotationPresent(DataType.class))
                .filter(dt -> format.equals(dt.getClass().getAnnotation(DataType.class).name()))
                .findFirst();

        if (componentDataType.isPresent()) {
            return componentDataType;
        }

        return getDefaultOutputTypeOrEmpty(format);
    }

    /**
     * Retrieve default data output type from Camel context for given format name.
     * @param format
     * @return
     */
    private Optional<OutputType> getDefaultOutputTypeOrEmpty(String format) {
        Optional<OutputType> camelDataType = getComponentOutputTypes("camel").stream()
                .filter(dt -> dt.getClass().isAnnotationPresent(DataType.class))
                .filter(dt -> format.equals(dt.getClass().getAnnotation(DataType.class).name()))
                .findFirst();

        if (camelDataType.isPresent()) {
            return camelDataType;
        }

        Optional<OutputType> foundInRegistry = Optional.ofNullable(camelContext.getRegistry().lookupByNameAndType(format, OutputType.class));
        if (foundInRegistry.isPresent()) {
            return foundInRegistry;
        }

        return Optional.empty();
    }

    /**
     * Get input data type from given component scheme and format name.
     * @param scheme
     * @param format
     * @return
     */
    public Optional<InputType> getInputType(String scheme, String format) {
        if (format == null) {
            return Optional.empty();
        }

        Optional<InputType> componentDataType = getComponentInputTypes(scheme).stream()
                .filter(dt -> dt.getClass().isAnnotationPresent(DataType.class))
                .filter(dt -> format.equals(dt.getClass().getAnnotation(DataType.class).name()))
                .findFirst();

        if (componentDataType.isPresent()) {
            return componentDataType;
        }

        return getDefaultInputTypeOrEmpty(format);
    }

    /**
     * Retrieve default data output type from Camel context for given format name.
     * @param format
     * @return
     */
    private Optional<InputType> getDefaultInputTypeOrEmpty(String format) {
        Optional<InputType> camelDataType = getComponentInputTypes("camel").stream()
                .filter(dt -> dt.getClass().isAnnotationPresent(DataType.class))
                .filter(dt -> format.equals(dt.getClass().getAnnotation(DataType.class).name()))
                .findFirst();

        if (camelDataType.isPresent()) {
            return camelDataType;
        }

        Optional<InputType> foundInRegistry = Optional.ofNullable(camelContext.getRegistry().lookupByNameAndType(format, InputType.class));
        if (foundInRegistry.isPresent()) {
            return foundInRegistry;
        }

        return Optional.empty();
    }

    /**
     * Retrieve list of output data types defined on the component level.
     * @param scheme
     * @return
     */
    private List<OutputType> getComponentOutputTypes(String scheme) {
        return componentOutputTypes.getOrDefault(scheme, Collections.emptyList());
    }

    /**
     * Retrieve list of input data types defined on the component level.
     * @param scheme
     * @return
     */
    private List<InputType> getComponentInputTypes(String scheme) {
        return componentInputTypes.getOrDefault(scheme, Collections.emptyList());
    }

    /**
     * Register new output type for given component.
     * @param scheme
     * @param types
     * @return
     */
    public DefaultDataTypeResolver registerComponentOutputType(String scheme, OutputType... types) {
        if (!componentOutputTypes.containsKey(scheme)) {
            componentOutputTypes.put(scheme, new ArrayList<>());
        }

        this.componentOutputTypes.get(scheme).addAll(Arrays.asList(types));
        return this;
    }

    /**
     * Register new input type for given component.
     * @param scheme
     * @param types
     * @return
     */
    public DefaultDataTypeResolver registerComponentInputType(String scheme, InputType... types) {
        if (!componentInputTypes.containsKey(scheme)) {
            componentInputTypes.put(scheme, new ArrayList<>());
        }

        this.componentInputTypes.get(scheme).addAll(Arrays.asList(types));
        return this;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }
}
