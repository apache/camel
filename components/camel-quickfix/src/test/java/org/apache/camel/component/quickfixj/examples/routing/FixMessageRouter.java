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
package org.apache.camel.component.quickfixj.examples.routing;

import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.FieldMap;
import quickfix.Message;
import quickfix.Message.Header;
import quickfix.SessionID;
import quickfix.field.BeginString;
import quickfix.field.DeliverToCompID;
import quickfix.field.DeliverToLocationID;
import quickfix.field.DeliverToSubID;
import quickfix.field.OnBehalfOfCompID;
import quickfix.field.OnBehalfOfLocationID;
import quickfix.field.OnBehalfOfSubID;
import quickfix.field.SenderCompID;
import quickfix.field.SenderLocationID;
import quickfix.field.SenderSubID;
import quickfix.field.TargetCompID;
import quickfix.field.TargetLocationID;
import quickfix.field.TargetSubID;

/**
 * Routes exchanges based on FIX-specific routing fields in the message.
 */
public class FixMessageRouter {
    private static final Logger LOG = LoggerFactory.getLogger(FixMessageRouter.class);

    private final String engineUri;
    
    public FixMessageRouter(String engineUri) {
        this.engineUri = engineUri;
    }
    
    public String route(Exchange exchange) {
        Message message = exchange.getIn().getBody(Message.class);
        if (message != null) {
            SessionID destinationSession = getDestinationSessionID(message);
            if (destinationSession != null) {
                String destinationUri = String.format("%s?sessionID=%s", engineUri, destinationSession);
                LOG.debug("Routing destination: {}", destinationUri);
                return destinationUri;
            }
        }
        return null;
    }

    private SessionID getDestinationSessionID(Message message) {
        Header header = message.getHeader();
        String fixVersion = getField(header, BeginString.FIELD);
        String destinationCompId = getField(header, DeliverToCompID.FIELD);
        if (destinationCompId != null) {
            String destinationSubId = getField(header, DeliverToSubID.FIELD);
            String destinationLocationId = getField(header, DeliverToLocationID.FIELD);
            
            header.removeField(DeliverToCompID.FIELD);
            header.removeField(DeliverToSubID.FIELD);
            header.removeField(DeliverToLocationID.FIELD);
            
            String gatewayCompId = getField(header, TargetCompID.FIELD);
            String gatewaySubId = getField(header, TargetSubID.FIELD);
            String gatewayLocationId = getField(header, TargetLocationID.FIELD);
            
            header.setString(OnBehalfOfCompID.FIELD, getField(header, SenderCompID.FIELD));
            if (header.isSetField(SenderSubID.FIELD)) {
                header.setString(OnBehalfOfSubID.FIELD, getField(header, SenderSubID.FIELD));
            }
            if (header.isSetField(SenderLocationID.FIELD)) {
                header.setString(OnBehalfOfLocationID.FIELD, getField(header, SenderLocationID.FIELD));
            }
            
            return new SessionID(fixVersion, gatewayCompId, gatewaySubId, gatewayLocationId,
                destinationCompId, destinationSubId, destinationLocationId, null);
        }
        return null;
    }

    private String getField(FieldMap fieldMap, int tag) {
        if (fieldMap.isSetField(tag)) {
            try {
                return fieldMap.getString(tag);
            } catch (Exception e) {
                ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
        return null;
    }
}