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
package org.apache.camel.component.language;

import java.net.URLDecoder;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;

/**
 * The <a href="http://camel.apache.org/language-component.html">Language component</a> enables sending
 * {@link org.apache.camel.Exchange}s to a given language in order to have a script executed.
 *
 * @version 
 */
public class LanguageComponent extends UriEndpointComponent {

    public static final String RESOURCE = "resource:";

    public LanguageComponent() {
        super(LanguageEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String name = ObjectHelper.before(remaining, ":");
        String script = ObjectHelper.after(remaining, ":");
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
            if (resource.startsWith(RESOURCE)) {
                resource = resource.substring(RESOURCE.length());
            }
            if (ResourceHelper.hasScheme(resource)) {
                // the script is a uri for a resource
                resourceUri = resource;
                // then the script should be null
                script = null;
            } else {
                // the script is provided as text in the uri, so decode to utf-8
                script = URLDecoder.decode(script, "UTF-8");
                // then the resource should be null
                resourceUri = null;
            }
        }

        LanguageEndpoint endpoint = new LanguageEndpoint(uri, this, language, null, resourceUri);
        endpoint.setScript(script);
        setProperties(endpoint, parameters);
        return endpoint;
    }

}
