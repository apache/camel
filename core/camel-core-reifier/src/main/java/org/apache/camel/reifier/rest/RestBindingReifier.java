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
package org.apache.camel.reifier.rest;

import org.apache.camel.Route;
import org.apache.camel.model.rest.RestBindingDefinition;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.reifier.AbstractReifier;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.processor.RestBindingAdvice;
import org.apache.camel.support.processor.RestBindingAdviceFactory;
import org.apache.camel.support.processor.RestBindingConfiguration;

public class RestBindingReifier extends AbstractReifier {

    private final RestBindingDefinition definition;

    public RestBindingReifier(Route route, RestBindingDefinition definition) {
        super(route);
        this.definition = definition;
    }

    public RestBindingAdvice createRestBindingAdvice() throws Exception {
        RestConfiguration config = CamelContextHelper.getRestConfiguration(camelContext, definition.getComponent());
        RestBindingConfiguration rbc = new RestBindingConfiguration();

        // these options can be overridden per rest verb
        String mode = config.getBindingMode().name();
        if (definition.getBindingMode() != null) {
            mode = parse(RestBindingMode.class, definition.getBindingMode()).name();
        }
        rbc.setBindingMode(mode);
        rbc.setBindingPackageScan(config.getBindingPackageScan());
        boolean cors = config.isEnableCORS();
        if (definition.getEnableCORS() != null) {
            cors = parseBoolean(definition.getEnableCORS(), false);
        }
        rbc.setEnableCORS(cors);
        boolean noContentResponse = config.isEnableNoContentResponse();
        if (definition.getEnableNoContentResponse() != null) {
            noContentResponse = parseBoolean(definition.getEnableNoContentResponse(), false);
        }
        rbc.setEnableNoContentResponse(noContentResponse);
        boolean skip = config.isSkipBindingOnErrorCode();
        if (definition.getSkipBindingOnErrorCode() != null) {
            skip = parseBoolean(definition.getSkipBindingOnErrorCode(), false);
        }
        rbc.setSkipBindingOnErrorCode(skip);
        boolean validation = config.isClientRequestValidation();
        if (definition.getClientRequestValidation() != null) {
            validation = parseBoolean(definition.getClientRequestValidation(), false);
        }
        rbc.setClientRequestValidation(validation);
        validation = config.isClientResponseValidation();
        if (definition.getClientResponseValidation() != null) {
            validation = parseBoolean(definition.getClientResponseValidation(), false);
        }
        rbc.setClientResponseValidation(validation);
        rbc.setConsumes(parseString(definition.getConsumes()));
        rbc.setProduces(parseString(definition.getProduces()));
        rbc.setCorsHeaders(parseMap(config.getCorsHeaders()));
        rbc.setQueryDefaultValues(parseMap(definition.getDefaultValues()));
        rbc.setQueryAllowedValues(parseMap(definition.getAllowedValues()));
        rbc.setRequiredBody(definition.getRequiredBody() != null && definition.getRequiredBody());
        rbc.setRequiredQueryParameters(parseSet(definition.getRequiredQueryParameters()));
        rbc.setRequiredHeaders(parseSet(definition.getRequiredHeaders()));
        rbc.setType(parseString(definition.getType()));
        rbc.setTypeClass(definition.getTypeClass());
        rbc.setOutType(parseString(definition.getOutType()));
        rbc.setOutTypeClass(definition.getOutTypeClass());
        rbc.setResponseCodes(parseMap(definition.getResponseCodes()));
        rbc.setResponseHeaders(parseSet(definition.getResponseHeaders()));

        // use factory to create advice
        return RestBindingAdviceFactory.build(camelContext, rbc);
    }

}
