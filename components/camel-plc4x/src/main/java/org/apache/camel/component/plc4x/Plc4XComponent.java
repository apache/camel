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
package org.apache.camel.component.plc4x;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("plc4x")
public class Plc4XComponent extends DefaultComponent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Plc4XComponent.class);

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Plc4XEndpoint endpoint = new Plc4XEndpoint(uri, this);

        Map<String, String> tags = getAndRemoveOrResolveReferenceParameter(parameters, "tags", Map.class);
        Map<String, Object> map = PropertiesHelper.extractProperties(parameters, "tag.");
        if (map != null) {
            if (tags == null) {
                tags = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Object> me : map.entrySet()) {
                tags.put(me.getKey(), me.getValue().toString());
            }
        }
        if (tags != null) {
            endpoint.setTags(tags);
        }

        String trigger = getAndRemoveOrResolveReferenceParameter(parameters, "trigger", String.class);
        if (trigger != null) {
            endpoint.setTrigger(trigger);
        }
        Integer period = getAndRemoveOrResolveReferenceParameter(parameters, "period", Integer.class);
        if (period != null) {
            endpoint.setPeriod(period);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void afterConfiguration(String uri, String remaining, Endpoint endpoint, Map<String, Object> parameters) {
        Plc4XEndpoint plc4XEndpoint = (Plc4XEndpoint) endpoint;
        plc4XEndpoint.setDriver(remaining.split(":")[0]);
    }

    @Override
    protected void validateParameters(String uri, Map<String, Object> parameters, String optionPrefix) {
        if (parameters != null && !parameters.isEmpty()) {
            Map<String, Object> param = parameters;
            if (optionPrefix != null) {
                param = PropertiesHelper.extractProperties(parameters, optionPrefix);
            }

            if (parameters.size() > 0) {
                LOGGER.info("{} parameters will be passed to the PLC Driver", param);
            }
        }
    }

}
