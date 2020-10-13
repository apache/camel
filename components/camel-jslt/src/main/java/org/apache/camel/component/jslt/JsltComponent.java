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
package org.apache.camel.component.jslt;

import java.util.Collection;
import java.util.Map;

import com.schibsted.spt.data.jslt.Function;
import com.schibsted.spt.data.jslt.filters.JsonFilter;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;

@Component("jslt")
public class JsltComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private Collection<Function> functions;
    @Metadata(label = "advanced")
    private JsonFilter objectFilter;
    @Metadata(defaultValue = "false")
    private boolean allowTemplateFromHeader;

    public JsltComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        boolean cache = getAndRemoveParameter(parameters, "contentCache", Boolean.class, Boolean.TRUE);

        JsltEndpoint answer = new JsltEndpoint(uri, this, remaining);
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

    public Collection<Function> getFunctions() {
        return functions;
    }

    /**
     * JSLT can be extended by plugging in functions written in Java.
     */
    public void setFunctions(Collection<Function> functions) {
        this.functions = functions;
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

    public JsonFilter getObjectFilter() {
        return objectFilter;
    }

    /**
     * JSLT can be extended by plugging in a custom jslt object filter
     */
    public void setObjectFilter(JsonFilter objectFilter) {
        this.objectFilter = objectFilter;
    }
}
