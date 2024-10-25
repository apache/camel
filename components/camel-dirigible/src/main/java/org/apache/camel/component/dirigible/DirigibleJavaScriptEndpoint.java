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
package org.apache.camel.component.dirigible;

import org.apache.camel.*;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Invoke JavaScript code.
 */
@UriEndpoint(firstVersion = "4.9.0", scheme = DirigibleJavaScriptEndpoint.SCHEME, title = "Dirigible JavaScript",
             syntax = DirigibleJavaScriptEndpoint.SCHEME + ":javaScriptPath", producerOnly = true, remote = false,
             category = { Category.CORE, Category.SCRIPT })
public class DirigibleJavaScriptEndpoint extends DefaultEndpoint {

    static final String SCHEME = "dirigible-java-script";

    private static final Logger LOGGER = LoggerFactory.getLogger(DirigibleJavaScriptEndpoint.class);

    @UriPath(label = "common", description = "Sets the path of the JavaScript file.")
    @Metadata(required = true)
    private String javaScriptPath;

    public DirigibleJavaScriptEndpoint() {
        LOGGER.debug("Creating [{}] without parameters", this);
        setExchangePattern(ExchangePattern.InOut);
    }

    public DirigibleJavaScriptEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
        LOGGER.debug("Creating [{}] for URI [{}]", this, endpointUri);
        setExchangePattern(ExchangePattern.InOut);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public Producer createProducer() {
        return new DirigibleJavaScriptProducer(this, javaScriptPath);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("You cannot consume from " + this.getClass());
    }

    @Override
    protected String createEndpointUri() {
        return DirigibleJavaScriptEndpoint.SCHEME + ":" + UnsafeUriCharactersEncoder.encode(getJavaScriptPath());
    }

    public String getJavaScriptPath() {
        return javaScriptPath;
    }

    public void setJavaScriptPath(String javaScriptPath) {
        this.javaScriptPath = javaScriptPath;
    }

}
