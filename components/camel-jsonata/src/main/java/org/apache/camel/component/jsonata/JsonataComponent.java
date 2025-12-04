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

package org.apache.camel.component.jsonata;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;

@Component("jsonata")
public class JsonataComponent extends DefaultComponent {

    @Metadata(defaultValue = "true", description = "Sets whether to use resource content cache or not")
    private boolean contentCache = true;

    @Metadata
    private boolean allowTemplateFromHeader;

    @Metadata(label = "advanced", description = "To configure custom frame bindings and inject user functions.")
    protected JsonataFrameBinding frameBinding;

    public JsonataComponent() {}

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, contentCache);

        JsonataFrameBinding frameBinding =
                resolveAndRemoveReferenceParameter(parameters, "frameBinding", JsonataFrameBinding.class);
        if (frameBinding == null) {
            // fallback to component configured
            frameBinding = getFrameBinding();
        }

        JsonataEndpoint answer = new JsonataEndpoint(uri, this, remaining, frameBinding);
        answer.setContentCache(cache);
        answer.setAllowTemplateFromHeader(allowTemplateFromHeader);

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

    /**
     * Sets whether to use resource content cache or not
     */
    public void setContentCache(boolean contentCache) {
        this.contentCache = contentCache;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    /**
     * To use the custom JsonataFrameBinding to perform configuration of custom functions that will be used.
     */
    public void setFrameBinding(JsonataFrameBinding frameBinding) {
        this.frameBinding = frameBinding;
    }

    public JsonataFrameBinding getFrameBinding() {
        return frameBinding;
    }
}
