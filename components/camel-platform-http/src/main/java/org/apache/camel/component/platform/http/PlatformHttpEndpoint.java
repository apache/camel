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
package org.apache.camel.component.platform.http;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.platform.http.spi.PlatformHttpEngine;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.service.ServiceHelper;

@UriEndpoint(firstVersion = "3.0.0", scheme = "platform-http", title = "Platform HTTP", syntax = "platform-http:path", label = "http", consumerOnly = true)
public class PlatformHttpEndpoint extends DefaultEndpoint implements AsyncEndpoint, HeaderFilterStrategyAware {

    @UriPath(description = "The path under which this endpoint serves the HTTP requests")
    @Metadata(required = true)
    private final String path;

    @UriParam(label = "consumer", defaultValue = "false", description = "Whether or not the consumer should try to find a target consumer "
            + "by matching the URI prefix if no exact match is found.")
    private boolean matchOnUriPrefix;

    @UriParam(label = "consumer", description = "A comma separated list of HTTP methods to serve, e.g. GET,POST ."
            + " If no methods are specified, all methods will be served.")
    private String httpMethodRestrict;

    @UriParam(label = "consumer", description = "The content type this endpoint accepts as an input, such as"
            + " application/xml or application/json. <code>null</code> or <code>&#42;/&#42;</code> mean no restriction.")
    private String consumes;

    @UriParam(label = "consumer", description = "The content type this endpoint produces, such as"
            + " application/xml or application/json.")
    private String produces;

    @UriParam(label = "consumer,advanced", description = "A comma or whitespace separated list of file extensions."
            + " Uploads having these extensions will be stored locally."
            + " Null value or asterisk (*) will allow all files.")
    private String fileNameExtWhitelist;

    @UriParam(label = "advanced", description = "An HTTP Server engine implementation to serve the requests of this"
            + " endpoint.")
    private PlatformHttpEngine platformHttpEngine;

    @UriParam(label = "advanced", description = "To use a custom HeaderFilterStrategy to filter headers to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy = new PlatformHttpHeaderFilterStrategy();

    public PlatformHttpEndpoint(String uri, String remaining, Component component) {
        super(uri, component);
        path = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer is not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new DefaultConsumer(this, processor) {
            private Consumer delegatedConsumer;

            @Override
            public PlatformHttpEndpoint getEndpoint() {
                return (PlatformHttpEndpoint) super.getEndpoint();
            }

            @Override
            protected void doInit() throws Exception {
                super.doInit();
                delegatedConsumer = getEndpoint().getOrCreateEngine().createConsumer(getEndpoint(), getProcessor());
                configureConsumer(delegatedConsumer);
            }

            @Override
            protected void doStart() throws Exception {
                super.doStart();
                ServiceHelper.startService(delegatedConsumer);
            }

            @Override
            protected void doStop() throws Exception {
                super.doStop();
                ServiceHelper.stopAndShutdownServices(delegatedConsumer);
            }

            @Override
            protected void doResume() throws Exception {
                ServiceHelper.resumeService(delegatedConsumer);
                super.doResume();
            }

            @Override
            protected void doSuspend() throws Exception {
                ServiceHelper.suspendService(delegatedConsumer);
                super.doSuspend();
            }
        };
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public String getPath() {
        return path;
    }

    public PlatformHttpEngine getPlatformHttpEngine() {
        return platformHttpEngine;
    }

    public void setPlatformHttpEngine(PlatformHttpEngine platformHttpEngine) {
        this.platformHttpEngine = platformHttpEngine;
    }

    public boolean isMatchOnUriPrefix() {
        return matchOnUriPrefix;
    }

    public void setMatchOnUriPrefix(boolean matchOnUriPrefix) {
        this.matchOnUriPrefix = matchOnUriPrefix;
    }

    public String getHttpMethodRestrict() {
        return httpMethodRestrict;
    }

    public void setHttpMethodRestrict(String httpMethodRestrict) {
        this.httpMethodRestrict = httpMethodRestrict;
    }

    public String getFileNameExtWhitelist() {
        return fileNameExtWhitelist;
    }

    public void setFileNameExtWhitelist(String fileNameExtWhitelist) {
        this.fileNameExtWhitelist = fileNameExtWhitelist;
    }

    public String getConsumes() {
        return consumes;
    }

    public void setConsumes(String consumes) {
        this.consumes = consumes;
    }

    public String getProduces() {
        return produces;
    }

    public void setProduces(String produces) {
        this.produces = produces;
    }

    PlatformHttpEngine getOrCreateEngine() {
        return platformHttpEngine != null
                ? platformHttpEngine
                : ((PlatformHttpComponent) getComponent()).getOrCreateEngine();
    }
}
