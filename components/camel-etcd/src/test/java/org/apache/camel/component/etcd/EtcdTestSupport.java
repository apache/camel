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

import java.net.URI;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.test.junit4.CamelTestSupport;

public class EtcdTestSupport extends CamelTestSupport {
    protected static final Processor NODE_TO_VALUE_IN = new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
            EtcdKeysResponse response = exchange.getIn().getBody(EtcdKeysResponse.class);
            if (response != null) {
                exchange.getIn().setBody(response.node.key + "=" + response.node.value);
            }
        }
    };

    public boolean isCreateCamelContextPerClass() {
        return false;
    }

    protected EtcdClient getClient() {
        return new EtcdClient(URI.create("http://localhost:2379"));
    }
}
