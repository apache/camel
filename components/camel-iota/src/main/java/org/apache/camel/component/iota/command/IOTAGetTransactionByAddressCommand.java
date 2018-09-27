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
package org.apache.camel.component.iota.command;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.component.iota.IOTAConstants;
import org.apache.camel.component.iota.utils.HttpRestUtils;
import org.apache.http.ParseException;

public class IOTAGetTransactionByAddressCommand implements IOTACommandInterface {

    private String url;

    /**
     * API REFERENCE: https://iota.readme.io/v1.5.0/reference#findtransactions
     * 
     * @throws IOException
     * @throws ParseException
     */
    @Override
    public String execute(Exchange exchange) throws ParseException, IOException {
        // addresses can be comma saparated value
        String addresses = exchange.getIn().getHeader(IOTAConstants.ADDRESS_HEADER, String.class);

        if (addresses == null) {
            throw new UnsupportedOperationException("IOTAProducer address cannot be null!");
        }

        String[] addressArray = addresses.split(",");
        for (int idx = 0; idx < addressArray.length; idx++) {
            if (addressArray[idx].length() > IOTAConstants.ADDRESS_LENGTH) {
                // remove checksum
                addressArray[idx] = addressArray[idx].substring(0, addressArray[idx].length() - IOTAConstants.CHECKSUM_LENGTH);
            }
        }

        return HttpRestUtils.getTransfer(url, addressArray, null);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
