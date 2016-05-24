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
package org.apache.camel.support;

import org.apache.camel.Exchange;
import org.apache.camel.model.remote.ServiceCallDefinition;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for custom implementations of {@link ServiceCallDefinition ServiceCall EIP} components.
 * <p/>
 * Below are some examples how to call a service and what Camel endpoint URI is constructed based on the input:
 * <pre>
     serviceCall("myService") -> http://hostname:port
     serviceCall("myService/foo") -> http://hostname:port/foo
     serviceCall("http:myService/foo") -> http:hostname:port/foo
     serviceCall("myService", "http:myService.host:myService.port/foo") -> http:hostname:port/foo
     serviceCall("myService", "netty4:tcp:myService?connectTimeout=1000") -> netty:tcp:hostname:port?connectTimeout=1000
 * </pre>
 */
public abstract class ServiceCallExpressionSupport extends ExpressionAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceCallExpressionSupport.class);

    private final String name;
    private final String scheme;
    private final String contextPath;
    private final String uri;

    public ServiceCallExpressionSupport(String name, String scheme, String contextPath, String uri) {
        this.name = name;
        this.scheme = scheme;
        this.contextPath = contextPath;
        this.uri = uri;
    }

    public abstract String getIp(Exchange exchange) throws Exception;

    public abstract int getPort(Exchange exchange) throws Exception;

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            String ip = getIp(exchange);
            int port = getPort(exchange);
            return buildCamelEndpointUri(ip, port, name, uri, contextPath, scheme);
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected static String buildCamelEndpointUri(String ip, int port, String name, String uri, String contextPath, String scheme) {
        // build basic uri if none provided
        String answer = uri;
        if (answer == null) {
            if (scheme == null) {
                // use http/https by default if no scheme has been configured
                if (port == 443) {
                    scheme = "https";
                } else {
                    scheme = "http";
                }
            }
            answer = scheme + "://" + ip + ":" + port;
            if (contextPath != null) {
                answer += "" + contextPath;
            }
        } else {
            // we have existing uri, then replace the serviceName with ip:port
            if (answer.contains(name + ".host")) {
                answer = answer.replaceFirst(name + "\\.host", ip);
            }
            if (answer.contains(name + ".port")) {
                answer = answer.replaceFirst(name + "\\.port", "" + port);
            }
            if (answer.contains(name)) {
                answer = answer.replaceFirst(name, ip + ":" + port);
            }
        }

        LOG.debug("Camel endpoint uri: {} for calling service: {} on server {}:{}", answer, name, ip, port);
        return answer;
    }

}