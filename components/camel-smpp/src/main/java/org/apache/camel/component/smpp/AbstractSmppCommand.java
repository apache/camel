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
package org.apache.camel.component.smpp;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.session.SMPPSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSmppCommand implements SmppCommand {
    
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    protected SMPPSession session;
    protected SmppConfiguration config;

    public AbstractSmppCommand(SMPPSession session, SmppConfiguration config) {
        this.session = session;
        this.config = config;
    }
    
    protected Message getResponseMessage(Exchange exchange) {
        Message message;
        if (exchange.getPattern().isOutCapable()) {
            log.debug("Exchange is out capable, setting headers on out exchange...");
            message = exchange.getOut();
        } else {
            log.debug("Exchange is not out capable, setting headers on in exchange...");
            message = exchange.getIn();
        }
        
        return message;
    }
}