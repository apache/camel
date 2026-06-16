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
package org.apache.camel.java;

import java.util.Collection;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationsDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SendDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.transformer.TransformersDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.model.validator.ValidatorsDefinition;
import org.apache.camel.spi.ModelToJavaDumper;
import org.apache.camel.spi.annotations.JdkService;

import static org.apache.camel.model.ProcessorDefinitionHelper.filterTypeInOutputs;

/**
 * Lightweight {@link ModelToJavaDumper} based on the generated {@link org.apache.camel.java.out.JavaDslModelWriter}.
 */
@JdkService(ModelToJavaDumper.FACTORY)
public class LwModelToJavaDumper implements ModelToJavaDumper {

    @Override
    public String dumpModelAsJava(CamelContext context, NamedNode definition) throws Exception {
        return dumpModelAsJava(context, definition, false, true);
    }

    @Override
    public String dumpModelAsJava(
            CamelContext context, NamedNode definition,
            boolean resolvePlaceholders, boolean generatedIds)
            throws Exception {

        Properties properties = new Properties();
        if (definition instanceof RouteTemplatesDefinition templates) {
            templates.getRouteTemplates().forEach(template -> {
                resolveEndpointDslUris(template.getRoute());
                collectTemplateProperties(template, properties);
            });
        } else if (definition instanceof RouteTemplateDefinition template) {
            resolveEndpointDslUris(template.getRoute());
            collectTemplateProperties(template, properties);
        } else if (definition instanceof RoutesDefinition routes) {
            routes.getRoutes().forEach(LwModelToJavaDumper::resolveEndpointDslUris);
        } else if (definition instanceof RouteDefinition route) {
            resolveEndpointDslUris(route);
        }

        org.apache.camel.java.out.JavaDslModelWriter writer = new org.apache.camel.java.out.JavaDslModelWriter();

        StringBuilder sb = new StringBuilder();
        if (definition instanceof RoutesDefinition rd) {
            for (RouteDefinition route : rd.getRoutes()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(writer.writeRouteDefinition(route));
            }
        } else if (definition instanceof RouteDefinition route) {
            sb.append(writer.writeRouteDefinition(route));
        } else if (definition instanceof RouteTemplatesDefinition rtd) {
            for (RouteTemplateDefinition template : rtd.getRouteTemplates()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(writer.writeRouteTemplateDefinition(template));
            }
        } else if (definition instanceof RouteTemplateDefinition template) {
            sb.append(writer.writeRouteTemplateDefinition(template));
        } else if (definition instanceof RestsDefinition rd) {
            for (RestDefinition rest : rd.getRests()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(writer.writeRestDefinition(rest));
            }
        } else if (definition instanceof RestDefinition rest) {
            sb.append(writer.writeRestDefinition(rest));
        } else if (definition instanceof RouteConfigurationsDefinition rcd) {
            for (RouteConfigurationDefinition config : rcd.getRouteConfigurations()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(writer.writeRouteConfigurationDefinition(config));
            }
        } else if (definition instanceof RouteConfigurationDefinition config) {
            sb.append(writer.writeRouteConfigurationDefinition(config));
        } else if (definition instanceof RestConfigurationDefinition restConfig) {
            sb.append(writer.writeRestConfigurationDefinition(restConfig));
        } else if (definition instanceof TransformersDefinition td) {
            for (TransformerDefinition t : td.getTransformers()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(writer.writeTransformer(t));
            }
        } else if (definition instanceof ValidatorsDefinition vd) {
            for (ValidatorDefinition v : vd.getValidators()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n");
                }
                sb.append(writer.writeValidator(v));
            }
        }

        String result = sb.toString();
        if (resolvePlaceholders) {
            result = resolvePlaceholders(context, result, properties);
        }

        return result;
    }

    private static void collectTemplateProperties(RouteTemplateDefinition template, Properties properties) {
        if (Boolean.TRUE.equals(template.getRoute().isTemplate())) {
            java.util.Map<String, Object> parameters = template.getRoute().getTemplateParameters();
            if (parameters != null) {
                properties.putAll(parameters);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void resolveEndpointDslUris(RouteDefinition route) {
        if (route == null) {
            return;
        }
        FromDefinition from = route.getInput();
        if (from != null && from.getEndpointConsumerBuilder() != null) {
            from.setUri(from.getEndpointConsumerBuilder().getRawUri());
        }
        Collection<SendDefinition> col = filterTypeInOutputs(route.getOutputs(), SendDefinition.class);
        for (SendDefinition<?> to : col) {
            if (to.getEndpointProducerBuilder() != null) {
                to.setUri(to.getEndpointProducerBuilder().getRawUri());
            }
        }
        Collection<ToDynamicDefinition> col2 = filterTypeInOutputs(route.getOutputs(), ToDynamicDefinition.class);
        for (ToDynamicDefinition to : col2) {
            if (to.getEndpointProducerBuilder() != null) {
                to.setUri(to.getEndpointProducerBuilder().getRawUri());
            }
        }
    }

    private static String resolvePlaceholders(CamelContext context, String text, Properties properties) {
        context.getPropertiesComponent().setLocalProperties(properties);
        try {
            return context.resolvePropertyPlaceholders(text);
        } catch (Exception e) {
            return text;
        } finally {
            context.getPropertiesComponent().setLocalProperties(null);
        }
    }
}
