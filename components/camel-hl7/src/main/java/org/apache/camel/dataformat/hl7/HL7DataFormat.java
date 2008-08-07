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
package org.apache.camel.dataformat.hl7;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;

import ca.uhn.hl7v2.model.Message;

/**
 * HL7 DataFormat (supports v2.x of the HL7 protocol).
 * <p/>
 * This data format supports two operations:
 * <ul>
 *   <li>marshal = from Message to String (used when returning as response using the HL7 MLLP codec).</li>
 *   <li>unmarshal = from String to Message (used when recieving streamed data from the HL7 MLLP codec)</li>
 * </ul>
 * <p/>
 * Uses the <a href="http://hl7api.sourceforge.net/index.html">HAPI (HL7 API)</a> for HL7 parsing.
 * <p/>
 * Uses the default PipeParser from the HAPI API. This DataFormat <b>only</b> supports the EDI based HL7
 * messages and not the XML based (their are not commonly used).
 *
 * @see org.apache.camel.component.mina.HL7MLLPCodec
 */
public class HL7DataFormat implements DataFormat {

    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        Message message = ExchangeHelper.convertToMandatoryType(exchange, Message.class, body);
        String encoded = HL7Converter.toString(message);
        outputStream.write(encoded.getBytes());
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        String body = ExchangeHelper.convertToMandatoryType(exchange, String.class, inputStream);
        Message message = HL7Converter.toMessage(body);
        return message;
    }

}

