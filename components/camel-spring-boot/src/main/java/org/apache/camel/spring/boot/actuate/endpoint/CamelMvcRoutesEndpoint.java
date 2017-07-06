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

import org.springframework.boot.actuate.endpoint.mvc.ActuatorMediaTypes;
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Adapter to expose {@link CamelRoutesEndpoint} as an {@link MvcEndpoint}.
 *
 * @author Ben Hale
 * @author Kazuki Shimizu
 * @author Eddú Meléndez
 * @since 1.5.0
 */
@ConfigurationProperties(prefix = "endpoints." + CamelRoutesEndpoint.ENDPOINT_ID)
public class CamelMvcRoutesEndpoint extends EndpointMvcAdapter {
    private static final ResponseEntity<?> NOT_FOUND = ResponseEntity.notFound().build();
    private final CamelRoutesEndpoint delegate;

    public CamelMvcRoutesEndpoint(CamelRoutesEndpoint delegate) {
        super(delegate);

        this.delegate = delegate;
    }

    @RequestMapping(
        method = RequestMethod.GET,
        value = "/{id}",
        produces = {
            ActuatorMediaTypes.APPLICATION_ACTUATOR_V1_JSON_VALUE,
            MediaType.APPLICATION_JSON_VALUE
        }
    )
    @ResponseBody
    public Object get(@PathVariable String id) {
        if (!delegate.isEnabled()) {
            return getDisabledResponse();
        }

        Object result = delegate.getRouteInfo(id);
        if (result == null) {
            result = NOT_FOUND;
        }

        return result;
    }
}
