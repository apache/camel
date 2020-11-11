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
package org.apache.camel.component.avro.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.generated.Value;

public class GetProcessor implements Processor {

    private KeyValueProtocol keyValue;

    public GetProcessor(KeyValueProtocol keyValue) {
        this.keyValue = keyValue;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof Key) {
            Value v = keyValue.get((Key) body);
            exchange.getMessage().setBody(v);
        }
        if (body instanceof Object[]) {
            Object[] args = (Object[]) body;
            if (args.length == 1 && args[0] instanceof Key) {
                Value v = keyValue.get((Key) args[0]);
                exchange.getMessage().setBody(v);
            }
        }
    }

    public KeyValueProtocol getKeyValue() {
        return keyValue;
    }

    public void setKeyValue(KeyValueProtocol keyValue) {
        this.keyValue = keyValue;
    }
}
