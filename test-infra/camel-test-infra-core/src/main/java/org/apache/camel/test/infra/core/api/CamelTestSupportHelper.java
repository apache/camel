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

package org.apache.camel.test.infra.core.api;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.MockUtils;
import org.apache.camel.util.ObjectHelper;

/**
 * Helper interface for simplifying the conversion from the older CamelTestSupport to the new extension
 */
public interface CamelTestSupportHelper {

    CamelContextExtension getCamelContextExtension();

    default <T extends Endpoint> T getMandatoryEndpoint(String uri, Class<T> type) {
        T endpoint = getCamelContextExtension().getContext().getEndpoint(uri, type);
        ObjectHelper.notNull(endpoint, "No endpoint found for uri: " + uri);

        return endpoint;
    }

    default MockEndpoint getMockEndpoint(String uri) {
        return getCamelContextExtension().getMockEndpoint(uri);
    }

    default <T extends Endpoint> T resolveMandatoryEndpoint(String endpointUri, Class<T> endpointType) {
        return MockUtils.resolveMandatoryEndpoint(getCamelContextExtension().getContext(), endpointUri, endpointType);
    }

    default Endpoint resolveMandatoryEndpoint(String endpointUri) {
        return MockUtils.resolveMandatoryEndpoint(getCamelContextExtension().getContext(), endpointUri);
    }

    default Exchange createExchangeWithBody(Object body) {
        Exchange exchange = new DefaultExchange(getCamelContextExtension().getContext());
        Message message = exchange.getIn();
        message.setBody(body);

        return exchange;
    }
}
