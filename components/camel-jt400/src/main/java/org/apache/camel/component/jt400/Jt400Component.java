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
package org.apache.camel.component.jt400;

import java.util.Map;

import org.apache.camel.CamelException;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;

/**
 * {@link Component} to provide integration with AS/400 objects. 
 * 
 * Current implementation only supports working with data queues (*DTAQ)
 */
public class Jt400Component extends DefaultComponent<Exchange> {

    private static final String DATA_QUEUE = "DTAQ";

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map properties)
        throws Exception {
        String type = remaining.substring(remaining.lastIndexOf(".") + 1).toUpperCase();

        if (DATA_QUEUE.equals(type)) {
            return new Jt400DataQueueEndpoint(uri, this);
        }
        throw new CamelException(String.format("AS/400 Object type %s is not supported", type));
    }
}
