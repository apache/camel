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

    @Metadata(defaultValue = "true",
              description = "Whether the endpoints keep the content of their resource in memory after the first load,"
                            + " instead of reading it again for each exchange. It is the default of the contentCache option"
                            + " of each endpoint.")
    private boolean contentCache = true;
    @Metadata(description = "Whether the endpoints take the JSONata expression from the CamelJsonataResourceUri header, when"
                            + " the header is set, instead of from their resource. It is the default of the"
                            + " allowTemplateFromHeader option of each endpoint. Enable it only when the header comes from a"
                            + " trusted source.")
    private boolean allowTemplateFromHeader;
    @Metadata(label = "advanced",
              description = "A JsonataFrameBinding that binds custom functions and variables to the Jsonata.Frame of each"
                            + " evaluation. It is used by the endpoints that do not set their own frameBinding option.")
    protected JsonataFrameBinding frameBinding;

    public JsonataComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, contentCache);

        JsonataFrameBinding frameBinding
                = resolveAndRemoveReferenceParameter(parameters, "frameBinding", JsonataFrameBinding.class);
        if (frameBinding == null) {
            // fallback to component configured
            frameBinding = getFrameBinding();
        }

        JsonataEndpoint answer = new JsonataEndpoint(uri, this, remaining, frameBinding);
        answer.setContentCache(cache);
        answer.setAllowTemplateFromHeader(allowTemplateFromHeader);
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

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public void setFrameBinding(JsonataFrameBinding frameBinding) {
        this.frameBinding = frameBinding;
    }

    public JsonataFrameBinding getFrameBinding() {
        return frameBinding;
    }
}
