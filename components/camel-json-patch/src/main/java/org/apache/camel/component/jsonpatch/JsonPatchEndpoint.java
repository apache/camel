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

package org.apache.camel.component.jsonpatch;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Transforms JSON using JSON patch (RFC 6902).
 */
@UriEndpoint(
        firstVersion = "3.12.0",
        scheme = "json-patch",
        title = "JsonPatch",
        syntax = "json-patch:resourceUri",
        remote = false,
        producerOnly = true,
        category = {Category.TRANSFORMATION},
        headersClass = JsonPatchConstants.class)
public class JsonPatchEndpoint extends ResourceEndpoint {

    @UriParam
    private boolean allowTemplateFromHeader;

    public JsonPatchEndpoint() {}

    public JsonPatchEndpoint(String uri, JsonPatchComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    public Producer createProducer() {
        return new JsonPatchProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
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
