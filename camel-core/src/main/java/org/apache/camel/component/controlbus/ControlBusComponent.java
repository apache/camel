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
package org.apache.camel.component.controlbus;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * The <a href="http://camel.apache.org/controlbus.html">control bus</a> component.
 */
public class ControlBusComponent extends DefaultComponent {

    // TODO: allow to use a thread pool for tasks so you dont have to wait
    // TODO: management command, to use the JMX mbeans easier
    // TODO: Bulk status in POJO / JSON format
    // TODO: a header with the action to do instead of uri, as we may want to be lenient

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ControlBusEndpoint answer = new ControlBusEndpoint(uri, this);

        // does the control bus use a language
        if (remaining != null && remaining.startsWith("language:")) {
            String lan = remaining.substring(9);
            if (lan != null) {
                answer.setLanguage(getCamelContext().resolveLanguage(lan));
            } else {
                throw new IllegalArgumentException("Language must be configured in endpoint uri: " + uri);
            }
        }

        setProperties(answer, parameters);
        return answer;
    }
}
