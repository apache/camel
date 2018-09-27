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
import org.apache.commons.lang3.StringUtils;
import org.apache.http.ParseException;

public class IOTAGetTransactionByTAGCommand implements IOTACommandInterface {

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
        String tags = exchange.getIn().getHeader(IOTAConstants.TAG_HEADER, String.class);

        if (tags == null) {
            throw new UnsupportedOperationException("IOTAProducer tags cannot be null!");
        }

        String[] tagsArray = tags.split(",");
        for (int idx = 0; idx < tagsArray.length; idx++) {
            // convert into trytes
            tagsArray[idx] = StringUtils.rightPad(tagsArray[idx], IOTAConstants.TAG_LENGTH, '9');
        }

        return HttpRestUtils.getTransfer(url, null, tagsArray);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
