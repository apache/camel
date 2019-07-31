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
package org.apache.camel.component.as2.api.protocol;

import java.io.IOException;

import org.apache.camel.component.as2.api.AS2AsynchronousMDNManager;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.util.AS2Utils;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

public class RequestAsynchronousMDN implements HttpRequestInterceptor {

    private final String as2Version;
    private final String senderFQDN;

    public RequestAsynchronousMDN(String as2Version, String senderFQDN) {
        this.as2Version = as2Version;
        this.senderFQDN = senderFQDN;
    }

    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {

        HttpCoreContext coreContext = HttpCoreContext.adapt(context);

        /* MIME header */
        request.addHeader(AS2Header.MIME_VERSION, AS2Constants.MIME_VERSION);

        /* AS2-Version header */
        request.addHeader(AS2Header.AS2_VERSION, as2Version);

        /* Message-Id header */
        // SHOULD be set to aid in message reconciliation
        request.addHeader(AS2Header.MESSAGE_ID, AS2Utils.createMessageId(senderFQDN));

        /* Recipient-Address header */
        String recipientAddress = coreContext.getAttribute(AS2AsynchronousMDNManager.RECIPIENT_ADDRESS, String.class);
        request.addHeader(AS2Header.RECIPIENT_ADDRESS, recipientAddress);
    }

}
