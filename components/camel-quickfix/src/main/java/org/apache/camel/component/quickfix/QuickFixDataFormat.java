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
package org.apache.camel.component.quickfix;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.component.quickfix.converter.QuickFixConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;

import quickfix.Message;

/**
 * 
 * Quickfix DataFormat.
 * <p/>
 * This data format supports two operations:
 * <ul>
 *   <li>marshal = from quickfix.Message to String</li>
 *   <li>unmarshal = from String to quickfix.Message</li>
 * </ul>
 * <p/>
 *  
 */
public class QuickFixDataFormat implements DataFormat {

    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        Message message = ExchangeHelper.convertToMandatoryType(exchange, Message.class, body);
        String fixMessage = QuickFixConverter.toString(message);
        outputStream.write(fixMessage.getBytes());
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        String body = ExchangeHelper.convertToMandatoryType(exchange, String.class, inputStream);
        Message message = QuickFixConverter.toMessage(body);
        return message;

    }

}
