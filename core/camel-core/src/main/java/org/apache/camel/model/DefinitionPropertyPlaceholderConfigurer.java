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
package org.apache.camel.model;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.model.placeholder.DefinitionPropertiesPlaceholderProviderHelper;
import org.apache.camel.spi.PropertyPlaceholderConfigurer;

/**
 * To be used for configuring property placeholder options on the EIP models.
 */
public interface DefinitionPropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer {

    /**
     * Gets the options on the model definition which supports property placeholders and can be resolved.
     * This will be all the string based options.
     *
     * @return key/values of options
     */
    default Map<String, Supplier<String>> getReadPropertyPlaceholderOptions(CamelContext camelContext) {
        PropertyPlaceholderConfigurer configurer = DefinitionPropertiesPlaceholderProviderHelper.provider(this).orElse(null);
        return configurer != null ? configurer.getReadPropertyPlaceholderOptions(camelContext) : null;
    }

    /**
     * To update an existing property using the function with the key/value and returning the changed value
     * This will be all the string based options.
     */
    default Map<String, Consumer<String>> getWritePropertyPlaceholderOptions(CamelContext camelContext) {
        PropertyPlaceholderConfigurer aware = DefinitionPropertiesPlaceholderProviderHelper.provider(this).orElse(null);
        return aware != null ? aware.getWritePropertyPlaceholderOptions(camelContext) : null;
    }

}
