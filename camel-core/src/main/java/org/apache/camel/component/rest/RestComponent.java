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
package org.apache.camel.component.rest;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.ObjectHelper;

/**
 * REST-DSL component.
 */
public class RestComponent extends UriEndpointComponent {

    public RestComponent() {
        super(RestEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        RestEndpoint answer = new RestEndpoint(uri, this);
        setProperties(answer, parameters);
        answer.setParameters(parameters);

        if (!remaining.contains(":")) {
            throw new IllegalArgumentException("Invalid syntax. Must be rest:method:path[:uriTemplate] where uriTemplate is optional");
        }

        String method = ObjectHelper.before(remaining, ":");
        String s = ObjectHelper.after(remaining, ":");

        String path;
        String uriTemplate;
        if (s != null && s.contains(":")) {
            path = ObjectHelper.before(s, ":");
            uriTemplate = ObjectHelper.after(s, ":");
        } else {
            path = s;
            uriTemplate = null;
        }

        // remove trailing slashes
        path = FileUtil.stripTrailingSeparator(path);
        uriTemplate = FileUtil.stripTrailingSeparator(uriTemplate);

        answer.setMethod(method);
        answer.setPath(path);
        answer.setUriTemplate(uriTemplate);

        // if no explicit component name was given, then fallback and use default configured component name
        if (answer.getComponentName() == null && getCamelContext().getRestConfiguration() != null) {
            answer.setComponentName(getCamelContext().getRestConfiguration().getComponent());
        }

        return answer;
    }

}
