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
package org.apache.camel.component.aws.xray.component;

import org.apache.camel.Exchange;
import org.apache.camel.component.aws.xray.XRayTracer;
import org.apache.camel.component.aws.xray.bean.SomeBackingService;
import org.apache.camel.support.DefaultProducer;

public class TestXRayProducer extends DefaultProducer {

    private final SomeBackingService backingService;
    private final String state;

    public TestXRayProducer(final TestXRayEndpoint endpoint, String state) {
        super(endpoint);

        this.state = state;
        backingService = new SomeBackingService(endpoint.getCamelContext());
    }

    @Override
    public void process(Exchange exchange) {

        byte[] body = exchange.getIn().getBody(byte[].class);

        if (trim(CommonEndpoints.RECEIVED).equals(this.state)
                || trim(CommonEndpoints.READY).equals(this.state)) {

            String traceId = exchange.getIn().getHeader(XRayTracer.XRAY_TRACE_ID, String.class);
            backingService.performMethod(body, state, traceId);
        }
    }

    private static String trim(String endpoint) {
        return endpoint.substring(endpoint.indexOf(":") + 1);
    }
}
