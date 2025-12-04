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

package org.apache.camel.dsl.jbang.core.commands.bind;

import java.io.InputStream;
import java.util.Map;

import org.apache.camel.util.StringHelper;

/**
 * Helper class provides access to the templates that construct the Pipe resource. Subclasses may overwrite the provider
 * to inject their own templates.
 */
public interface TemplateProvider {
    default InputStream getPipeTemplate() {
        return TemplateProvider.class.getClassLoader().getResourceAsStream("templates/pipe.yaml.tmpl");
    }

    default InputStream getStepTemplate(String stepType) {
        return TemplateProvider.class
                .getClassLoader()
                .getResourceAsStream("templates/step-%s.yaml.tmpl".formatted(stepType));
    }

    default InputStream getEndpointTemplate(String endpointType) {
        return TemplateProvider.class
                .getClassLoader()
                .getResourceAsStream("templates/endpoint-%s.yaml.tmpl".formatted(endpointType));
    }

    default InputStream getErrorHandlerTemplate(String type) {
        return TemplateProvider.class
                .getClassLoader()
                .getResourceAsStream("templates/error-handler-%s.yaml.tmpl".formatted(type));
    }

    /**
     * Creates YAML snippet representing the endpoint properties section.
     *
     * @param  props the properties to set as endpoint properties.
     * @return
     */
    default String asEndpointProperties(Map<String, Object> props) {
        StringBuilder sb = new StringBuilder();
        if (props.isEmpty()) {
            // create a dummy placeholder, so it is easier to add new properties manually
            return sb.append("#properties:\n      ").append("#key: \"value\"").toString();
        }

        sb.append("properties:\n");
        for (Map.Entry<String, Object> propertyEntry : props.entrySet()) {
            String propValue = propertyEntry.getValue().toString();

            // Take care of Strings with colon - need to quote these values to avoid breaking YAML
            if (propValue.contains(":") && !StringHelper.isQuoted(propValue)) {
                propValue = "\"%s\"".formatted(propValue);
            }

            sb.append("      ")
                    .append(propertyEntry.getKey())
                    .append(": ")
                    .append(propValue)
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Creates YAML snippet representing the error handler parameters section.
     *
     * @param props the properties to set as error handler parameters.
     */
    default String asErrorHandlerParameters(Map<String, Object> props) {
        if (props.isEmpty()) {
            return "parameters: {}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("parameters:\n");
        for (Map.Entry<String, Object> propertyEntry : props.entrySet()) {
            sb.append("        ")
                    .append(propertyEntry.getKey())
                    .append(": ")
                    .append(propertyEntry.getValue())
                    .append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Get additional indent that should be applied to endpoint templates.
     *
     * @param  type the endpoint type.
     * @return
     */
    default int getAdditionalIndent(BindingProvider.EndpointType type) {
        if (type == BindingProvider.EndpointType.ERROR_HANDLER) {
            return 4;
        }

        return 0;
    }
}
