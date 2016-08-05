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
package org.apache.camel.component.kubernetes.processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.remote.ServiceCallConstants;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubernetesDnsServiceCallExpression extends ExpressionAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesDnsServiceCallExpression.class);

    private final String name;
    private final String scheme;
    private final String contextPath;
    private final String uri;

    public KubernetesDnsServiceCallExpression(String name, String scheme, String contextPath, String uri) {
        this.name = name;
        this.scheme = scheme;
        this.contextPath = contextPath;
        this.uri = uri;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        try {
            return buildCamelEndpointUri(
                name,
                uri,
                contextPath,
                scheme,
                ObjectHelper.notNull(
                    exchange.getIn().getHeader(ServiceCallConstants.SERVER_IP, String.class),
                    ServiceCallConstants.SERVER_IP)
            );
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected static String buildCamelEndpointUri(String name, String uri, String contextPath, String scheme, String dnsServicePart) {
        // build basic uri if none provided
        String answer = uri;
        if (answer == null) {
            if (scheme == null) {
                // use http by default if no scheme has been configured
                scheme = "http";
            }
            answer = scheme + "://" + dnsServicePart;
            if (contextPath != null) {
                answer += "/" + contextPath;
            }
        } else {
            // we have existing uri, then replace the serviceName with name.namespace.svc.dnsDomain
            if (answer.contains(name)) {
                answer = answer.replaceFirst(name, dnsServicePart);
            }
        }

        LOG.debug("Camel endpoint uri: {} for calling service: {}", answer, name);
        return answer;
    }
}
