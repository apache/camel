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
package org.apache.camel.component.netty.http;

import java.net.URI;
import java.util.Map;

import io.netty.handler.codec.http.HttpRequest;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.HeaderFilterStrategy;

import static org.apache.camel.http.base.HttpHelper.evalPlaceholders;

/**
 * A {@link org.apache.camel.component.netty.http.NettyHttpBinding} that supports the Rest DSL.
 */
public class RestNettyHttpBinding extends DefaultNettyHttpBinding {

    public RestNettyHttpBinding() {
    }

    public RestNettyHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        super(headerFilterStrategy);
    }

    @Override
    public RestNettyHttpBinding copy() {
        try {
            return (RestNettyHttpBinding) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void populateCamelHeaders(
            HttpRequest request, Map<String, Object> headers, Exchange exchange, NettyHttpConfiguration configuration)
            throws Exception {
        super.populateCamelHeaders(request, headers, exchange, configuration);

        String path = request.uri();
        if (path == null) {
            return;
        }

        // skip the scheme/host/port etc, as we only want the context-path
        URI uri = new URI(path);
        path = uri.getPath();

        // in the endpoint the user may have defined rest {} placeholders
        // so we need to map those placeholders with data from the incoming request context path

        String consumerPath = configuration.getPath();

        if (useRestMatching(consumerPath)) {
            evalPlaceholders((k, v) -> NettyHttpHelper.appendHeader(headers, k, v), path, consumerPath);
        }
    }

    private boolean useRestMatching(String path) {
        // only need to do rest matching if using { } placeholders
        return path.indexOf('{') > -1;
    }
}
