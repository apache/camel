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
package org.apache.camel.component.netty4.http.cloud;

import org.apache.camel.impl.cloud.DefaultServiceCallExpression;
import org.apache.camel.util.ObjectHelper;

public final class Netty4HttpServiceExpression extends DefaultServiceCallExpression {
    public Netty4HttpServiceExpression() {
    }

    public Netty4HttpServiceExpression(String hostHeader, String portHeader) {
        super(hostHeader, portHeader);
    }

    @Override
    protected String doBuildCamelEndpointUri(String host, Integer port, String contextPath, String scheme) {
        if (!ObjectHelper.equal(scheme, "netty4-http")) {
            return super.doBuildCamelEndpointUri(host, port, contextPath, scheme);
        }

        String answer = scheme + ":http://" + host;
        if (port != null) {
            answer = answer + ":" + port;
        }

        if (contextPath != null) {
            if (!contextPath.startsWith("/")) {
                contextPath = "/" + contextPath;
            }

            answer += contextPath;
        }

        return answer;
    }
}
