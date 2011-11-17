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
package org.apache.camel.component.quickfixj.converter;

import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.FieldNotFound;
import quickfix.InvalidMessage;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.field.MsgType;

import static org.apache.camel.component.quickfixj.QuickfixjEndpoint.EVENT_CATEGORY_KEY;
import static org.apache.camel.component.quickfixj.QuickfixjEndpoint.MESSAGE_TYPE_KEY;
import static org.apache.camel.component.quickfixj.QuickfixjEndpoint.SESSION_ID_KEY;

@Converter
public final class QuickfixjConverters {
    private static final Logger LOG = LoggerFactory.getLogger(QuickfixjConverters.class);

    private QuickfixjConverters() {
        //Utility class
    }

    @Converter
    public static SessionID toSessionID(String sessionID) {
        return new SessionID(sessionID);
    }
    
    @Converter
    public static Message toMessage(String value, Exchange exchange) throws InvalidMessage, ConfigError {
        DataDictionary dataDictionary = getDataDictionary(exchange);
        return new Message(value, dataDictionary);
    }

    private static DataDictionary getDataDictionary(Exchange exchange) throws ConfigError {
        Object dictionaryValue = exchange.getProperties().get(QuickfixjEndpoint.DATA_DICTIONARY_KEY);
        
        DataDictionary dataDictionary;
        if (dictionaryValue instanceof DataDictionary) {
            dataDictionary = (DataDictionary)dictionaryValue;
        } else if (dictionaryValue instanceof String) {
            dataDictionary = new DataDictionary((String) dictionaryValue);
        } else {
            SessionID sessionID = (SessionID) exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY);
            Session session = Session.lookupSession(sessionID);
            dataDictionary = session != null ? session.getDataDictionary() : null;
        }
        
        return dataDictionary;
    }
    
    public static Exchange toExchange(Endpoint endpoint, SessionID sessionID, Message message, QuickfixjEventCategory eventCategory) {
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOnly);
        
        org.apache.camel.Message camelMessage = exchange.getIn();
        camelMessage.setHeader(EVENT_CATEGORY_KEY, eventCategory);
        camelMessage.setHeader(SESSION_ID_KEY, sessionID);
        
        if (message != null) {
            try {
                camelMessage.setHeader(MESSAGE_TYPE_KEY, message.getHeader().getString(MsgType.FIELD));
            } catch (FieldNotFound fieldNotFoundEx) {
                LOG.error("Message type field not found in QFJ message, continuing...");
            }
        }
        camelMessage.setBody(message);
        
        return exchange;
    }

}
