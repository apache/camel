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
package org.apache.camel.component.avro.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.avro.generated.Key;
import org.apache.camel.avro.generated.KeyValueProtocol;
import org.apache.camel.avro.generated.Value;

public class PutProcessor implements Processor {

    private KeyValueProtocol keyValue;

    public PutProcessor(KeyValueProtocol keyValue) {
        this.keyValue = keyValue;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof Object[]) {
            Object[] args = (Object[]) body;
            if (args.length == 2 && args[0] instanceof Key && args[1] instanceof Value) {
                keyValue.put((Key) args[0], (Value) args[1]);
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
