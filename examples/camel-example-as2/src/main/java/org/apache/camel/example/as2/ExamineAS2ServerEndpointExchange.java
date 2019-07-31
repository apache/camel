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
package org.apache.camel.example.as2;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.as2.api.util.AS2Utils;
import org.apache.camel.component.as2.internal.AS2Constants;
import org.apache.http.HttpRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExamineAS2ServerEndpointExchange implements Processor {

    private final transient Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void process(Exchange exchange) throws Exception {
        
        HttpCoreContext context = exchange.getProperty(AS2Constants.AS2_INTERCHANGE, HttpCoreContext.class);
        String ediMessage = exchange.getIn().getBody(String.class);

        if (context != null) {
            HttpRequest request = context.getRequest();
            log.info("\n*******************************************************************************"
                   + "\n*******************************************************************************"
                   + "\n\n******************* AS2 Server Endpoint Received Request **********************"
                   + "\n\n" + AS2Utils.printMessage(request) + "\n"
                   + "\n************************** Containing EDI message *****************************"
                   + "\n\n" + ediMessage + "\n"
                   + "\n*******************************************************************************"
                   + "\n*******************************************************************************");
        } else {
            log.info("AS2 Interchange missing from context");
        }

    }

}
