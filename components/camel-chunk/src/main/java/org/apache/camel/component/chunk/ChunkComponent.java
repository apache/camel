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
package org.apache.camel.component.chunk;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("chunk")
public class ChunkComponent extends DefaultComponent {

    @Metadata(defaultValue = "true", description = "Sets whether to use resource content cache or not")
    private boolean contentCache = true;
    @Metadata(description = "Whether the producer uses the template of the CamelChunkResourceUri header or the template text of"
                            + " the CamelChunkTemplate header instead of the resourceUri. If false, the producer ignores both"
                            + " headers. Enable it only when the route sets these headers, not a message sender.")
    private boolean allowTemplateFromHeader;
    @Metadata
    private boolean allowContextMapAll;

    public ChunkComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ChunkEndpoint endpoint = new ChunkEndpoint(uri, this, remaining);
        endpoint.setAllowTemplateFromHeader(allowTemplateFromHeader);
        endpoint.setAllowContextMapAll(allowContextMapAll);
        endpoint.setContentCache(contentCache);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public boolean isAllowContextMapAll() {
        return allowContextMapAll;
    }

    /**
     * Sets whether the context map should allow access to all details. By default only the message body and headers can
     * be accessed. This option can be enabled for full access to the current Exchange and CamelContext. Doing so impose
     * a potential security risk as this opens access to the full power of CamelContext API.
     */
    public void setAllowContextMapAll(boolean allowContextMapAll) {
        this.allowContextMapAll = allowContextMapAll;
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
}
