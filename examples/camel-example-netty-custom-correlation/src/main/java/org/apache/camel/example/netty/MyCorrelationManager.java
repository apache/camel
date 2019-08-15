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
package org.apache.camel.example.netty;

import org.apache.camel.component.netty.TimeoutCorrelationManagerSupport;
import org.apache.camel.util.StringHelper;

/**
 * Implement a timeout aware {@link org.apache.camel.component.netty.NettyCamelStateCorrelationManager}
 * that handles all the complexities for us, so we only need to implement how to extract the correlation id.
 */
public class MyCorrelationManager extends TimeoutCorrelationManagerSupport {

    @Override
    public String getRequestCorrelationId(Object request) {
        // correlation id is before the first colon
        return StringHelper.before(request.toString(), ":");
    }

    @Override
    public String getResponseCorrelationId(Object response) {
        // correlation id is before the first colon
        return StringHelper.before(response.toString(), ":");
    }

    @Override
    public String getTimeoutResponse(String correlationId, Object request) {
        // here we can build a custom response message on timeout, instead
        // of having an exception being thrown, however we only have access
        // to the correlation id and the request message that was sent over the wire
        return null;
    }
}
