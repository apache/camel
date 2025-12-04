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

package org.apache.camel.component.language;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * The <a href="http://camel.apache.org/language-component.html">Language component</a> enables sending
 * {@link org.apache.camel.Exchange}s to a given language in order to have a script executed.
 */
@org.apache.camel.spi.annotations.Component("language")
public class LanguageComponent extends DefaultComponent {

    public static final String RESOURCE = "resource:";

    @Metadata(defaultValue = "true", description = "Sets whether to use resource content cache or not")
    private boolean contentCache = true;

    @Metadata
    private boolean allowTemplateFromHeader;

    public LanguageComponent() {}

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String name = StringHelper.before(remaining, ":");
        String script = StringHelper.after(remaining, ":");
        // no script then remaining is the language name
        if (name == null && script == null) {
            name = remaining;
        }

        if (ObjectHelper.isEmpty(name)) {
            throw new IllegalArgumentException("Illegal syntax. Name of language not given in uri: " + uri);
        }
        Language language = getCamelContext().resolveLanguage(name);

        String resourceUri = null;
        String resource = script;
        if (resource != null) {
            boolean resourcePrefix = false;
            if (resource.startsWith(RESOURCE)) {
                resourcePrefix = true;
                resource = resource.substring(RESOURCE.length());
            }
            if (resourcePrefix) {
                // the script is a uri for a resource
                resourceUri = resource;
                // then the script should be null
                script = null;
            } else {
                // the script is provided as text in the uri, so decode to utf-8
                script = URLDecoder.decode(script, StandardCharsets.UTF_8);
                // then the resource should be null
                resourceUri = null;
            }
        }

        LanguageEndpoint endpoint = new LanguageEndpoint(uri, this, language, null, resourceUri);
        endpoint.setScript(script);
        endpoint.setAllowTemplateFromHeader(allowTemplateFromHeader);
        endpoint.setContentCache(contentCache);
        setProperties(endpoint, parameters);
        return endpoint;
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
}
