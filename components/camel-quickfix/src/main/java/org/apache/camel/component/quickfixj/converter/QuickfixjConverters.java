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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.apache.camel.Converter;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.apache.camel.util.IOHelper;
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

    @Converter
    public static Message toMessage(byte[] value, Exchange exchange) throws InvalidMessage, ConfigError, UnsupportedEncodingException {
        DataDictionary dataDictionary = getDataDictionary(exchange);
        String charsetName = IOHelper.getCharsetName(exchange);

        String message;
        if (charsetName != null) {
            message = new String(value, charsetName);
        } else {
            message = new String(value);
        }

        // if message ends with any sort of newline trim it so QuickfixJ's doesn't fail while parsing the string
        if (message.endsWith("\r\n")) {
            message = message.substring(0, message.length() - 2);
        } else if (message.endsWith("\r") || message.endsWith("\n")) {
            message = message.substring(0, message.length() - 1);
        }

        return new Message(message, dataDictionary, false);
    }

    @Converter
    public static InputStream toInputStream(Message value, Exchange exchange) throws InvalidMessage, ConfigError, UnsupportedEncodingException {
        if (exchange != null) {
            String charsetName = IOHelper.getCharsetName(exchange);
            if (charsetName != null) {
                return new ByteArrayInputStream(value.toString().getBytes(charsetName));
            } else {
                return new ByteArrayInputStream(value.toString().getBytes());
            }
        }
        return null;
    }

    private static DataDictionary getDataDictionary(Exchange exchange) throws ConfigError {
        Object dictionaryValue = exchange.getProperties().get(QuickfixjEndpoint.DATA_DICTIONARY_KEY);

        DataDictionary dataDictionary = null;
        if (dictionaryValue instanceof DataDictionary) {
            dataDictionary = (DataDictionary) dictionaryValue;
        } else if (dictionaryValue instanceof String) {
            dataDictionary = new DataDictionary((String) dictionaryValue);
        } else {
            SessionID sessionID = exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY, SessionID.class);
            if (sessionID != null) {
                Session session = Session.lookupSession(sessionID);
                dataDictionary = session != null ? session.getDataDictionary() : null;
            }
        }

        return dataDictionary;
    }

    public static Exchange toExchange(Endpoint endpoint, SessionID sessionID, Message message, QuickfixjEventCategory eventCategory) {
        return toExchange(endpoint, sessionID, message, eventCategory, ExchangePattern.InOnly);
    }

    public static Exchange toExchange(Endpoint endpoint, SessionID sessionID, Message message, QuickfixjEventCategory eventCategory, ExchangePattern exchangePattern) {
        Exchange exchange = endpoint.createExchange(exchangePattern);

        org.apache.camel.Message camelMessage = exchange.getIn();
        camelMessage.setHeader(EVENT_CATEGORY_KEY, eventCategory);
        camelMessage.setHeader(SESSION_ID_KEY, sessionID);

        if (message != null) {
            try {
                camelMessage.setHeader(MESSAGE_TYPE_KEY, message.getHeader().getString(MsgType.FIELD));
            } catch (FieldNotFound e) {
                LOG.warn("Message type field not found in QFJ message: {}, continuing...", message);
            }
        }
        camelMessage.setBody(message);

        return exchange;
    }

}
