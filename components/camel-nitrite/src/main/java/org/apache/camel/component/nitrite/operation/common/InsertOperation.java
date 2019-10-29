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
package org.apache.camel.component.nitrite.operation.common;

import java.lang.reflect.Array;

import org.apache.camel.Exchange;
import org.apache.camel.component.nitrite.NitriteConstants;
import org.apache.camel.component.nitrite.NitriteEndpoint;
import org.apache.camel.component.nitrite.operation.AbstractPayloadAwareOperation;
import org.apache.camel.component.nitrite.operation.CommonOperation;

/**
 * Insert document to collection or object to ObjectRepository. If parameter not specified, inserts message body
 */
public class InsertOperation extends AbstractPayloadAwareOperation implements CommonOperation {
    public InsertOperation(Object payload) {
        super(payload);
    }

    public InsertOperation() {
    }

    @Override
    protected void execute(Exchange exchange, NitriteEndpoint endpoint) throws Exception {
        Object payload = getPayload(exchange, endpoint);
        Object[] payloadArray = (Object[])Array.newInstance(payload.getClass(), 1);
        payloadArray[0] = payload;
        exchange.getMessage().setHeader(
                NitriteConstants.WRITE_RESULT,
                endpoint.getNitriteCollection().insert(payloadArray)
        );
    }
}
