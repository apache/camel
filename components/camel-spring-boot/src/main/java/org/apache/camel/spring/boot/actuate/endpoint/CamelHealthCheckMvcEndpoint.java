/**
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
package org.apache.camel.spring.boot.actuate.endpoint;

import java.util.Collections;
import java.util.Map;

import org.apache.camel.util.ObjectHelper;
import org.springframework.boot.actuate.endpoint.mvc.ActuatorMediaTypes;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Adapter to expose {@link CamelHealthCheckEndpoint} as an {@link MvcEndpoint}.
 */
@ConfigurationProperties(prefix = "endpoints." + CamelHealthCheckEndpoint.ENDPOINT_ID)
public class CamelHealthCheckMvcEndpoint extends AbstractCamelMvcEndpoint<CamelHealthCheckEndpoint> {

    public CamelHealthCheckMvcEndpoint(CamelHealthCheckEndpoint delegate) {
        super("/camel/health/check", delegate);
    }

    // ********************************************
    // Endpoints
    // ********************************************

    @ResponseBody
    @GetMapping(
        value = "/{id}",
        produces = {
            ActuatorMediaTypes.APPLICATION_ACTUATOR_V1_JSON_VALUE,
            MediaType.APPLICATION_JSON_VALUE
        }
    )
    public Object query(
        @PathVariable String id,
        @RequestParam(required = false) Map<String, Object> options) {

        return doIfEnabled(
            delegate -> delegate.query(
                id,
                ObjectHelper.supplyIfEmpty(options, Collections::emptyMap)
            ).orElseThrow(
                () -> new NoSuchCheckException("No such check " + id)
            )
        );
    }

    @ResponseBody
    @GetMapping(
        value = "/{id}/invoke",
        produces = {
            ActuatorMediaTypes.APPLICATION_ACTUATOR_V1_JSON_VALUE,
            MediaType.APPLICATION_JSON_VALUE
        }
    )
    public Object invoke(
        @PathVariable String id,
        @RequestParam(required = false) Map<String, Object> options) {

        return doIfEnabled(
            delegate -> delegate.invoke(
                id,
                ObjectHelper.supplyIfEmpty(options, Collections::emptyMap)
            ).orElseThrow(
                () -> new NoSuchCheckException("No such check " + id)
            )
        );
    }

    // ********************************************
    // Exceptions
    // ********************************************

    @SuppressWarnings("serial")
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such check")
    public static class NoSuchCheckException extends RuntimeException {
        public NoSuchCheckException(String message) {
            super(message);
        }
    }
}
