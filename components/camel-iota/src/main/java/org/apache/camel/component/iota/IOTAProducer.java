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
package org.apache.camel.component.iota;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.Exchange;
import org.apache.camel.component.iota.command.IOTACommandInterface;
import org.apache.camel.component.iota.command.IOTAGetTransactionByAddressCommand;
import org.apache.camel.component.iota.command.IOTAGetTransactionByTAGCommand;
import org.apache.camel.component.iota.command.IOTAGetTransactionDataCommand;
import org.apache.camel.component.iota.utils.TrytesConverter;
import org.apache.camel.impl.DefaultProducer;

/**
 * The IOTA producer.
 */
public class IOTAProducer extends DefaultProducer {
    private IOTAEndpoint endpoint;

    public IOTAProducer(IOTAEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void process(Exchange exchange) throws Exception {
        if (endpoint.getOperation() == null) {
            throw new UnsupportedOperationException("IOTAProducer operation cannot be null!");
        }

        IOTACommandInterface command = null;

        if (endpoint.getOperation().equals(IOTAOperation.FIND_TRANSACTION_DATA.toString())) {
            command = new IOTAGetTransactionDataCommand();
            ((IOTAGetTransactionDataCommand)command).setUrl(endpoint.getUrl());

            exchange.getIn().setBody(new ObjectMapper().readValue(command.execute(exchange), Map.class));

        } else if (endpoint.getOperation().equals(IOTAOperation.FIND_TRANSACTION_BY_ADDRESS.toString())) {
            command = new IOTAGetTransactionByAddressCommand();
            ((IOTAGetTransactionByAddressCommand)command).setUrl(endpoint.getUrl());

            Map<String, Object> response = new ObjectMapper().readValue(command.execute(exchange), Map.class);

            // convert output of transaction
            Map output = getTransactionData(exchange, response);
            exchange.getIn().setBody(output);

        } else if (endpoint.getOperation().equals(IOTAOperation.FIND_TRANSACTION_BY_TAG.toString())) {
            command = new IOTAGetTransactionByTAGCommand();
            ((IOTAGetTransactionByTAGCommand)command).setUrl(endpoint.getUrl());

            Map<String, Object> response = new ObjectMapper().readValue(command.execute(exchange), Map.class);

            // convert output of transaction
            Map output = getTransactionData(exchange, response);
            exchange.getIn().setBody(output);
        } else {
            throw new UnsupportedOperationException("IOTAProducer operation unknown!");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map getTransactionData(Exchange exchange, Map<String, Object> response) throws IOException {
        IOTACommandInterface command;
        command = new IOTAGetTransactionDataCommand();
        ((IOTAGetTransactionDataCommand)command).setUrl(endpoint.getUrl());

        Map output = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        Map getTrytesResp = null;
        for (String hash : (List<String>)response.get("hashes")) {
            ((IOTAGetTransactionDataCommand)command).setHash(hash);

            // parse string response to json
            getTrytesResp = mapper.readValue(command.execute(exchange), Map.class);
            if (getTrytesResp.containsKey("trytes")) {
                String trytes = (String)((List)getTrytesResp.get("trytes")).get(0);
                output.put(hash, TrytesConverter.transactionObject(trytes));
            }
        }
        return output;
    }

}
