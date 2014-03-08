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
package org.apache.camel.component.xquery;

import java.net.URL;
import java.util.Map;

import net.sf.saxon.lib.ModuleURIResolver;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An <a href="http://camel.apache.org/xquery.html">XQuery Component</a>
 * for performing transforming messages
 */
public class XQueryComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(XQueryComponent.class);
    private ModuleURIResolver moduleURIResolver = new XQueryModuleURIResolver(this);

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String resourceUri = remaining;
        URL url = resolveModuleResource(resourceUri);
        LOG.debug("{} using schema resource: {}", this, resourceUri);

        XQueryBuilder xslt = XQueryBuilder.xquery(url);
        xslt.setModuleURIResolver(getModuleURIResolver());
        configureXslt(xslt, uri, remaining, parameters);
        return new ProcessorEndpoint(uri, this, xslt);
    }

    protected void configureXslt(XQueryBuilder xQueryBuilder, String uri, String remaining, Map<String, Object> parameters) throws Exception {
        setProperties(xQueryBuilder, parameters);
    }

    public URL resolveModuleResource(String uri) throws Exception {
        return ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), uri);
    }

    public ModuleURIResolver getModuleURIResolver() {
        return moduleURIResolver;
    }

    public void setModuleURIResolver(ModuleURIResolver moduleURIResolver) {
        this.moduleURIResolver = moduleURIResolver;
    }
}
