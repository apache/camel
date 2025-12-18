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
package org.apache.camel.component.once;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;

@Component("once")
public class OnceComponent extends DefaultComponent {

    @Metadata(label = "advanced", defaultValue = "1000")
    private long delay = 1000;

    @Metadata(label = "advanced", defaultValue = "true")
    private boolean languages = true;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        OnceEndpoint answer = new OnceEndpoint(uri, this, remaining);
        answer.setDelay(delay);

        Map<String, String> headers = getAndRemoveOrResolveReferenceParameter(parameters, "headers", Map.class);
        Map<String, Object> map = PropertiesHelper.extractProperties(parameters, "header.");
        if (map != null && !map.isEmpty()) {
            if (headers == null) {
                headers = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Object> me : map.entrySet()) {
                headers.put(me.getKey(), me.getValue().toString());
            }
        }
        if (headers != null && !headers.isEmpty()) {
            answer.setHeaders(headers);
        }
        Map<String, String> variables = getAndRemoveOrResolveReferenceParameter(parameters, "variables", Map.class);
        map = PropertiesHelper.extractProperties(parameters, "variable.");
        if (map != null && !map.isEmpty()) {
            if (variables == null) {
                variables = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Object> me : map.entrySet()) {
                variables.put(me.getKey(), me.getValue().toString());
            }
        }
        if (variables != null && !variables.isEmpty()) {
            answer.setVariables(variables);
        }
        Map<String, String> properties = getAndRemoveOrResolveReferenceParameter(parameters, "exchangeProperties", Map.class);
        map = PropertiesHelper.extractProperties(parameters, "exchangeProperty.");
        if (map != null && !map.isEmpty()) {
            if (properties == null) {
                properties = new LinkedHashMap<>();
            }
            for (Map.Entry<String, Object> me : map.entrySet()) {
                properties.put(me.getKey(), me.getValue().toString());
            }
        }
        if (properties != null && !properties.isEmpty()) {
            answer.setExchangeProperties(properties);
        }
        setProperties(answer, parameters);
        return answer;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * The number of milliseconds to wait before triggering. Use 0 or negative to fire as soon as possible. The default
     * value is 1000.
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public boolean isLanguages() {
        return languages;
    }

    /**
     * Whether Camel languages are supported such as simple,groovy.
     */
    public void setLanguages(boolean languages) {
        this.languages = languages;
    }
}
