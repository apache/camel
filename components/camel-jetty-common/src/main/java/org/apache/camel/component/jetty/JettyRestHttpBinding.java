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
package org.apache.camel.component.jetty;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.http.common.DefaultHttpBinding;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.camel.http.common.HttpMessage;

public class JettyRestHttpBinding extends DefaultHttpBinding {

    @Deprecated
    public JettyRestHttpBinding() {
    }

    public JettyRestHttpBinding(HttpCommonEndpoint ep) {
        super(ep);
        setHeaderFilterStrategy(ep.getHeaderFilterStrategy());
        setTransferException(ep.isTransferException());
        setEagerCheckContentAvailable(ep.isEagerCheckContentAvailable());
        setMapHttpMessageBody(ep.isMapHttpMessageBody());
        setMapHttpMessageHeaders(ep.isMapHttpMessageHeaders());
    }

    @Override
    protected void populateRequestParameters(HttpServletRequest request, HttpMessage message) throws Exception {
        super.populateRequestParameters(request, message);

        String path = request.getPathInfo();
        if (path == null) {
            return;
        }

        // in the endpoint the user may have defined rest {} placeholders
        // so we need to map those placeholders with data from the incoming request context path

        JettyHttpEndpoint endpoint = (JettyHttpEndpoint) message.getExchange().getFromEndpoint();
        String consumerPath = endpoint.getPath();

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
                        message.setHeader(key, value);
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
