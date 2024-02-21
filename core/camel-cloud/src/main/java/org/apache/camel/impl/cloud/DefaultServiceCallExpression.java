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
package org.apache.camel.impl.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class for custom implementations of ServiceCall EIP components.
 * <p/>
 * Below are some examples how to call a service and what Camel endpoint URI is constructed based on the input:
 *
 * <pre>
 serviceCall("myService") -> http://hostname:port
 serviceCall("myService/foo") -> http://hostname:port/foo
 serviceCall("http:myService/foo") -> http:hostname:port/foo
 serviceCall("myService", "http:myService.host:myService.port/foo") -> http:hostname:port/foo
 serviceCall("myService", "netty:tcp:myService?connectTimeout=1000") -> netty:tcp:hostname:port?connectTimeout=1000
 * </pre>
 */
public class DefaultServiceCallExpression extends ServiceCallExpressionSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultServiceCallExpression.class);

    public DefaultServiceCallExpression() {
    }

    public DefaultServiceCallExpression(String hostHeader, String portHeader) {
        super(hostHeader, portHeader);
    }

    @Override
    protected String buildCamelEndpointUri(
            String name, String host, Integer port, String uri, String contextPath, String scheme) {
        // build basic uri if none provided
        String answer = uri;
        if (answer == null) {
            answer = doBuildCamelEndpointUri(host, port, contextPath, scheme);
        } else {
            // we have existing uri, then replace the serviceName with ip:port
            if (answer.contains(name + ".host")) {
                answer = answer.replaceFirst(name + "\\.host", host);
            }
            if (answer.contains(name + ".port") && port != null) {
                answer = answer.replaceFirst(name + "\\.port", Integer.toString(port));
            }
            if (answer.contains(name) && port != null) {
                answer = answer.replaceFirst(name, host + ":" + port);
            }
            if (answer.contains(name) && port == null) {
                answer = answer.replaceFirst(name, host);
            }
            // include scheme if not provided
            if (!answer.startsWith(scheme)) {
                answer = scheme + ":" + answer;
            }
        }

        LOGGER.debug("Camel endpoint uri: {} for calling service: {} on server {}:{}", answer, name, host, port);
        return answer;
    }

    protected String doBuildCamelEndpointUri(String host, Integer port, String contextPath, String scheme) {
        if (scheme == null) {
            // use http/https by default if no scheme or port have been configured
            if (port == null) {
                scheme = "http";
            } else if (port == 443) {
                scheme = "https";
            } else {
                scheme = "http";
            }
        }

        String answer = scheme + "://" + host;
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
