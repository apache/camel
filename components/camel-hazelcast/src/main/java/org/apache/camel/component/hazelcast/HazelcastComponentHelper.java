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
package org.apache.camel.component.hazelcast;

import java.util.Date;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

public final class HazelcastComponentHelper {

    private HazelcastComponentHelper() {
    }

    public static void copyHeaders(Exchange ex) {
        // get in headers
        Map<String, Object> headers = ex.getIn().getHeaders();

        // DELETE item id
        if (headers.containsKey(HazelcastConstants.OBJECT_ID)) {
            headers.remove(HazelcastConstants.OBJECT_ID);
        }

        if (headers.containsKey(HazelcastConstants.OPERATION)) {
            headers.remove(HazelcastConstants.OPERATION);
        }

        // propagate headers if OUT message created
        if (ex.hasOut()) {
            ex.getOut().setHeaders(headers);
        }
    }

    public static void setListenerHeaders(Exchange ex, String listenerType, String listenerAction, String cacheName) {
        ex.getIn().setHeader(HazelcastConstants.CACHE_NAME, cacheName);
        HazelcastComponentHelper.setListenerHeaders(ex, listenerType, listenerAction);
    }

    public static void setListenerHeaders(Exchange ex, String listenerType, String listenerAction) {
        ex.getIn().setHeader(HazelcastConstants.LISTENER_ACTION, listenerAction);
        ex.getIn().setHeader(HazelcastConstants.LISTENER_TYPE, listenerType);
        ex.getIn().setHeader(HazelcastConstants.LISTENER_TIME, new Date().getTime());
    }

    public static HazelcastOperation lookupOperation(Exchange exchange, HazelcastOperation defaultOperation) {

        String operationName = exchange.getIn().getHeader(HazelcastConstants.OPERATION, String.class);
        return ObjectHelper.isEmpty(operationName) ? defaultOperation : HazelcastOperation.getHazelcastOperation(operationName);
    }


}
