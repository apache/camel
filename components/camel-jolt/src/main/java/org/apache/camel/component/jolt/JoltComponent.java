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
package org.apache.camel.component.jolt;

import java.util.Map;

import io.joltcommunity.jolt.Transform;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;

@Component("jolt")
public class JoltComponent extends DefaultComponent {

    @Metadata(defaultValue = "true",
              description = "Whether the endpoints keep the content of their spec resource in memory after the first read."
                            + " It is the default of the contentCache option of each endpoint. Each endpoint builds its JOLT"
                            + " transform from the spec once, at the first exchange, and keeps it, so false does not make it"
                            + " read a changed spec again.")
    private boolean contentCache = true;
    @Metadata(description = "Whether the endpoints read the CamelJoltResourceUri and CamelJoltContext headers. It is the"
                            + " default of the allowTemplateFromHeader option of each endpoint. Enable it only when the headers"
                            + " come from a trusted source.")
    private boolean allowTemplateFromHeader;
    @Metadata(label = "advanced",
              description = "A JOLT Transform that all endpoints of the component use in place of the transform that they"
                            + " build from their spec. When set, the endpoints do not read their spec resource, and"
                            + " transformDsl and the CamelJoltResourceUri header have no effect. To use the CamelJoltContext"
                            + " header, it must also be a ContextualTransform, such as a Chainr.")
    private Transform transform;

    public JoltComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, contentCache);

        JoltEndpoint answer = new JoltEndpoint(uri, this, remaining);
        answer.setAllowTemplateFromHeader(allowTemplateFromHeader);
        answer.setContentCache(cache);
        answer.setTransform(transform);
        setProperties(answer, parameters);

        // if its a http resource then append any remaining parameters and update the resource uri
        if (ResourceHelper.isHttpUri(remaining)) {
            remaining = ResourceHelper.appendParameters(remaining, parameters);
            answer.setResourceUri(remaining);
        }

        return answer;
    }

    public boolean isContentCache() {
        return contentCache;
    }

    public void setContentCache(boolean contentCache) {
        this.contentCache = contentCache;
    }

    public Transform getTransform() {
        return transform;
    }

    public void setTransform(Transform transform) {
        this.transform = transform;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

}
