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
package org.apache.camel.component.aws.xray.bean;

import java.util.UUID;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Entity;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.aws.xray.XRayTracer;

public class SomeBackingService {

    private final Endpoint targetEndpoint;
    private final ProducerTemplate template;

    public SomeBackingService(CamelContext context) {
        targetEndpoint = context.getEndpoint("seda:backingTask");
        template = context.createProducerTemplate();
    }

    public String performMethod(byte[] body, String state, String traceId) {

        String key = UUID.randomUUID().toString();

        Entity traceEntity = AWSXRay.getGlobalRecorder().getTraceEntity();
        traceEntity.putMetadata("state", state);

        Exchange newExchange = targetEndpoint.createExchange(ExchangePattern.InOnly);
        newExchange.getIn().setBody(body);
        newExchange.getIn().setHeader("KEY", key);
        newExchange.getIn().setHeader(XRayTracer.XRAY_TRACE_ID, traceId);
        newExchange.getIn().setHeader(XRayTracer.XRAY_TRACE_ENTITY, traceEntity);
        template.asyncSend(targetEndpoint, newExchange);

        return key;
    }
}
