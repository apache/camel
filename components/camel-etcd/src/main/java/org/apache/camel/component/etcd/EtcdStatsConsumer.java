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
package org.apache.camel.component.etcd;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class EtcdStatsConsumer extends AbstractEtcdPollingConsumer {
    public EtcdStatsConsumer(EtcdStatsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public EtcdStatsEndpoint getEndpoint() {
        return (EtcdStatsEndpoint)super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        EtcdStatsEndpoint endpoint = getEndpoint();
        Object answer = endpoint.getStats(getClient());

        if (answer != null) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(EtcdConstants.ETCD_NAMESPACE, endpoint.getNamespace());
            exchange.getIn().setHeader(EtcdConstants.ETCD_PATH, endpoint.getPath());
            exchange.getIn().setBody(answer);

            getProcessor().process(exchange);

            return 1;
        }

        return 0;
    }
}
