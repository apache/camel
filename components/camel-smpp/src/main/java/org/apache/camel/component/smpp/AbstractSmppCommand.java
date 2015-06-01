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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.COctetString;
import org.jsmpp.bean.OptionalParameter.OctetString;
import org.jsmpp.bean.OptionalParameter.Tag;
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
            message = exchange.getOut();
        } else {
            message = exchange.getIn();
        }
        
        return message;
    }

    protected List<OptionalParameter> createOptionalParametersByCode(Map<Short, Object> optinalParamaters) {
        List<OptionalParameter> optParams = new ArrayList<OptionalParameter>();

        for (Entry<Short, Object> entry : optinalParamaters.entrySet()) {
            OptionalParameter optParam = null;
            Short key = entry.getKey();
            Object value = entry.getValue();

            try {
                if (value == null) {
                    optParam = new OptionalParameter.Null(key);
                } else if (value instanceof byte[]) {
                    optParam = new OptionalParameter.OctetString(key, (byte[]) value);
                } else if (value instanceof String) {
                    optParam = new OptionalParameter.COctetString(key, (String) value);
                } else if (value instanceof Byte) {
                    optParam = new OptionalParameter.Byte(key, (Byte) value);
                } else if (value instanceof Integer) {
                    optParam = new OptionalParameter.Int(key, (Integer) value);
                } else if (value instanceof Short) {
                    optParam = new OptionalParameter.Short(key, (Short) value);
                } else {
                    log.info("Couldn't determine optional parameter for value {} (type: {}). Skip this one.", value, value.getClass());
                    continue;
                }

                optParams.add(optParam);
            } catch (Exception e) {
                log.info("Couldn't determine optional parameter for key {} and value {}. Skip this one.", key, value);
            }
        }

        return optParams;
    }

    /**
     * @deprecated will be removed in Camel 2.13.0/3.0.0 - use createOptionalParametersByCode instead
     * @param optinalParamaters
     * @return
     */
    @Deprecated
    @SuppressWarnings("rawtypes")
    protected List<OptionalParameter> createOptionalParametersByName(Map<String, String> optinalParamaters) {
        List<OptionalParameter> optParams = new ArrayList<OptionalParameter>();

        for (Entry<String, String> entry : optinalParamaters.entrySet()) {
            OptionalParameter optParam = null;

            try {
                Tag tag = Tag.valueOf(entry.getKey());
                Class type = determineTypeClass(tag);

                if (OctetString.class.equals(type)) {
                    optParam = new OptionalParameter.OctetString(tag.code(), entry.getValue());
                } else if (COctetString.class.equals(type)) {
                    optParam = new OptionalParameter.COctetString(tag.code(), entry.getValue());
                } else if (org.jsmpp.bean.OptionalParameter.Byte.class.equals(type)) {
                    optParam = new OptionalParameter.Byte(tag.code(), Byte.valueOf(entry.getValue()));
                } else if (org.jsmpp.bean.OptionalParameter.Int.class.equals(type)) {
                    optParam = new OptionalParameter.Int(tag.code(), Integer.valueOf(entry.getValue()));
                } else if (org.jsmpp.bean.OptionalParameter.Short.class.equals(type)) {
                    optParam = new OptionalParameter.Short(tag.code(), Short.valueOf(entry.getValue()));
                } else if (org.jsmpp.bean.OptionalParameter.Null.class.equals(type)) {
                    optParam = new OptionalParameter.Null(tag);
                }

                optParams.add(optParam);
            } catch (Exception e) {
                log.info("Couldn't determine optional parameter for key {} and value {}. Skip this one.", entry.getKey(), entry.getValue());
            }
        }

        return optParams;
    }

    @SuppressWarnings("unchecked")
    protected Class<? extends OptionalParameter> determineTypeClass(Tag tag) throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        // we have to use reflection because the type field is private
        Field f = tag.getClass().getDeclaredField("type");
        f.setAccessible(true);
        return (Class<? extends OptionalParameter>) f.get(tag);
    }
}