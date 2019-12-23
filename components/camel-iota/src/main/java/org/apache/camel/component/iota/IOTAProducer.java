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
package org.apache.camel.component.iota;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.commons.lang3.StringUtils;
import org.iota.jota.builder.AddressRequest;
import org.iota.jota.dto.response.GetNewAddressResponse;
import org.iota.jota.dto.response.GetTransferResponse;
import org.iota.jota.dto.response.SendTransferResponse;
import org.iota.jota.model.Transfer;
import org.iota.jota.utils.TrytesConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The IOTA producer.
 */
public class IOTAProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(IOTAProducer.class);
    private IOTAEndpoint endpoint;

    public IOTAProducer(IOTAEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String seed = exchange.getIn().getHeader(IOTAConstants.SEED_HEADER, String.class);

        if (endpoint.getOperation() == null) {
            throw new UnsupportedOperationException("IOTAProducer operation cannot be null!");
        }

        if (endpoint.getOperation().equals(IOTAConstants.SEND_TRANSFER_OPERATION)) {
            String address = exchange.getIn().getHeader(IOTAConstants.TO_ADDRESS_HEADER, String.class);
            Integer value = exchange.getIn().getHeader(IOTAConstants.VALUE_HEADER, Integer.class);
            value = value != null ? value : 0;

            String tag = StringUtils.rightPad(endpoint.getTag(), IOTAConstants.TAG_LENGTH, '9');
            String message = TrytesConverter.asciiToTrytes(exchange.getIn().getBody(String.class));

            if (LOG.isDebugEnabled()) {
                LOG.debug("endpoint: security level {} depth {} minWeightMagnitude {} tag {} ", endpoint.getSecurityLevel(), endpoint.getDepth(), endpoint.getMinWeightMagnitude(), tag);
                LOG.debug("Sending value {} with message {} to address {}", value, message, address);
            }

            List<Transfer> transfers = new ArrayList<>();
            transfers.add(new Transfer(address, value, message, tag));
            SendTransferResponse response = endpoint.getApiClient()
                                            .sendTransfer(seed, endpoint.getSecurityLevel(), endpoint.getDepth(), endpoint.getMinWeightMagnitude(), transfers, null, null, false, true, null);

            exchange.getIn().setBody(response.getTransactions());
        } else if (endpoint.getOperation().equals(IOTAConstants.GET_NEW_ADDRESS_OPERATION)) {
            Integer index = exchange.getIn().getHeader(IOTAConstants.ADDRESS_INDEX_HEADER, Integer.class);

            AddressRequest addressRequest = new AddressRequest.Builder(seed, endpoint.getSecurityLevel())
                .index(index)
                .checksum(true)
                .amount(1)
                .build();

            GetNewAddressResponse response = endpoint.getApiClient().generateNewAddresses(addressRequest);
            exchange.getIn().setBody(response.getAddresses());
        } else if (endpoint.getOperation().equals(IOTAConstants.GET_TRANSFERS_OPERATION)) {
            Integer startIdx = exchange.getIn().getHeader(IOTAConstants.ADDRESS_START_INDEX_HEADER, Integer.class);
            Integer endIdx = exchange.getIn().getHeader(IOTAConstants.ADDRESS_END_INDEX_HEADER, Integer.class);

            GetTransferResponse response = endpoint.getApiClient().getTransfers(seed, endpoint.getSecurityLevel(), startIdx, endIdx, true);

            exchange.getIn().setBody(response.getTransfers());
        }
    }

}
