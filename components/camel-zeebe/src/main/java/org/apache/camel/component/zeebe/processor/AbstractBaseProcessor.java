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

package org.apache.camel.component.zeebe.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.component.zeebe.ZeebeConstants;
import org.apache.camel.component.zeebe.ZeebeEndpoint;
import org.apache.camel.component.zeebe.model.ZeebeMessage;
import org.apache.camel.support.service.BaseService;

public abstract class AbstractBaseProcessor extends BaseService implements ZeebeProcessor {
    protected final ZeebeEndpoint endpoint;
    ObjectMapper objectMapper = new ObjectMapper();

    public AbstractBaseProcessor(ZeebeEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    protected void setBody(Exchange exchange, ZeebeMessage message, boolean formatJSON) {
        if (endpoint.isFormatJSON()) {
            try {
                exchange.getMessage().setBody(objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException jsonProcessingException) {
                throw new IllegalArgumentException("Cannot convert result", jsonProcessingException);
            }
        } else {
            exchange.getMessage().setBody(message);
        }
    }

    protected void removeHeaders(Exchange exchange) {
        exchange.getMessage().removeHeaders(ZeebeConstants.HEADER_PREFIX + ".*");
    }
}
