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
package org.apache.camel.component.cxf.util;

import org.apache.cxf.endpoint.ConduitSelector;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Conduit;

public class NullConduitSelector implements ConduitSelector {
    private Endpoint endpoint;
    private NullConduit nullConduit;

    public NullConduitSelector() {
        nullConduit = new NullConduit();
    }
    public void complete(Exchange exchange) {
        //do nothing here

    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void prepare(Message message) {
        //do nothing here

    }

    public Conduit selectConduit(Message message) {
        return nullConduit;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;

    }

}
