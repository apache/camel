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
package org.apache.camel.component.seda;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;

/**
 * An implementation of the <a href="http://activemq.apache.org/camel/seda.html">SEDA components</a>
 * for asynchronous SEDA exchanges on a {@link BlockingQueue} within a CamelContext
 *
 * @version $Revision$
 */
public class SedaComponent extends DefaultComponent<Exchange> {

    public BlockingQueue<Exchange> createQueue(String uri, Map parameters) {
        int size = getAndRemoveParameter(parameters, "size", Integer.class, 1000);
        return new LinkedBlockingQueue<Exchange>(size);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        return new SedaEndpoint(uri, this, parameters);
    }
}
