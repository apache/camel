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
package org.apache.camel.component.ibatis;

import java.util.Iterator;

import org.apache.camel.Exchange;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.impl.DefaultProducer;

/**
 * @version $Revision$
 */
public class IBatisProducer extends DefaultProducer {
    private final IBatisEndpoint endpoint;

    public IBatisProducer(IBatisEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public IBatisEndpoint getEndpoint() {
        return (IBatisEndpoint) super.getEndpoint();
    }

    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            // must be a poll so lets do a query
            endpoint.query(exchange.getOut(true));
        } else {
            String operation = getOperationName(exchange);

            // lets handle arrays or collections of objects
            Iterator iter = ObjectConverter.iterator(body);
            while (iter.hasNext()) {
                endpoint.getSqlClient().insert(operation, iter.next());
            }
        }
    }

    /**
     * Returns the iBatis insert operation name
     */
    protected String getOperationName(Exchange exchange) {
        return endpoint.getEntityName();
    }
}
