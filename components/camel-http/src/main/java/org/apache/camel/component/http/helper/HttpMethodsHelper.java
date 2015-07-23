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
package org.apache.camel.component.http.helper;

import java.net.URISyntaxException;

import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.http.HttpMethods;

public final class HttpMethodsHelper {

    /**
     * Creates the HttpMethod to use to call the remote server, often either its GET or POST.
     *
     * @param exchange  the exchange
     * @return the created method
     * @throws URISyntaxException
     */
    public static HttpMethods createMethod(Exchange exchange, HttpEndpoint endpoint, boolean hasPayload) throws URISyntaxException {
        // compute what method to use either GET or POST
        HttpMethods answer;
        HttpMethods m = exchange.getIn().getHeader(Exchange.HTTP_METHOD, HttpMethods.class);
        if (m != null) {
            // always use what end-user provides in a header
            answer = m;
        } else if (hasPayload) {
            // use POST if we have payload
            answer = HttpMethods.POST;
        } else {
            // fallback to GET
            answer = HttpMethods.GET;
        }

        return answer;
    }

}
