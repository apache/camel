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
package org.apache.camel.component.restbinding;

import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.RestBindingCapable;
import org.apache.camel.util.ObjectHelper;

public class RestBindingComponent extends UriEndpointComponent {

    public RestBindingComponent() {
        super(RestBindingEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        preCheckConditions();

        RestBindingEndpoint answer = new RestBindingEndpoint(uri, this);
        setProperties(answer, parameters);
        answer.setParameters(parameters);

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException("Invalid syntax. Must be rest-binding:verb:path");
        }

        String verb = ObjectHelper.before(remaining, ":");
        String path = ObjectHelper.after(remaining, ":");

        answer.setVerb(verb);
        answer.setPath(path);

        return answer;
    }


    protected void preCheckConditions() throws Exception {
        if (lookupRestBindingCapableComponent() == null) {
            throw new IllegalStateException("There are no registered components in CamelContext that is RestBindingCapable");
        }
    }

    public RestBindingCapable lookupRestBindingCapableComponent() {
        for (String id : getCamelContext().getComponentNames()) {
            Component component = getCamelContext().getComponent(id);
            if (component instanceof RestBindingCapable) {
                return (RestBindingCapable) component;
            }
        }
        return null;
    }

}
