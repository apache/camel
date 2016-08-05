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
package org.apache.camel.component.netty4.http;

import java.net.URI;
import java.util.Map;

import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.HeaderFilterStrategy;

/**
 * A {@link org.apache.camel.component.netty4.http.NettyHttpBinding} that supports the Rest DSL.
 */
public class RestNettyHttpBinding extends DefaultNettyHttpBinding {

    public RestNettyHttpBinding() {
    }

    public RestNettyHttpBinding(HeaderFilterStrategy headerFilterStrategy) {
        super(headerFilterStrategy);
    }

    public RestNettyHttpBinding copy() {
        try {
            return (RestNettyHttpBinding)this.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public void populateCamelHeaders(FullHttpRequest request, Map<String, Object> headers, Exchange exchange, NettyHttpConfiguration configuration) throws Exception {
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

            // split using single char / is optimized in the jdk
            String[] paths = path.split("/");
            String[] consumerPaths = consumerPath.split("/");

            for (int i = 0; i < consumerPaths.length; i++) {
                if (paths.length < i) {
                    break;
                }
                String p1 = consumerPaths[i];
                if (p1.startsWith("{") && p1.endsWith("}")) {
                    String key = p1.substring(1, p1.length() - 1);
                    String value = paths[i];
                    if (value != null) {
                        NettyHttpHelper.appendHeader(headers, key, value);
                    }
                }
            }
        }
    }

    private boolean useRestMatching(String path) {
        // only need to do rest matching if using { } placeholders
        return path.indexOf('{') > -1;
    }
}
