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
package org.apache.camel.component.hl7;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.component.hl7.HL7Constants.HL7_MESSAGE_CONTROL;
import static org.apache.camel.component.hl7.HL7Constants.HL7_MESSAGE_TYPE;
import static org.apache.camel.component.hl7.HL7Constants.HL7_PROCESSING_ID;
import static org.apache.camel.component.hl7.HL7Constants.HL7_RECEIVING_APPLICATION;
import static org.apache.camel.component.hl7.HL7Constants.HL7_RECEIVING_FACILITY;
import static org.apache.camel.component.hl7.HL7Constants.HL7_SECURITY;
import static org.apache.camel.component.hl7.HL7Constants.HL7_SENDING_APPLICATION;
import static org.apache.camel.component.hl7.HL7Constants.HL7_SENDING_FACILITY;
import static org.apache.camel.component.hl7.HL7Constants.HL7_TIMESTAMP;
import static org.apache.camel.component.hl7.HL7Constants.HL7_TRIGGER_EVENT;
import static org.apache.camel.component.hl7.HL7Constants.HL7_VERSION_ID;

/**
 * HL7 DataFormat (supports v2.x of the HL7 protocol).
 * <p/>
 * This data format supports two operations:
 * <ul>
 *   <li>marshal = from Message to String (can be used when returning as response using the HL7 MLLP codec)</li>
 *   <li>unmarshal = from String to Message (can be used when receiving streamed data from the HL7 MLLP codec).
 *   This operation will also enrich the message by adding the MSH fields (MSH-3 to MSH-12) as headers on the message.</li>
 * </ul>
 * <p/>
 * Uses the <a href="http://hl7api.sourceforge.net/index.html">HAPI (HL7 API)</a> for HL7 parsing.
 * <p/>
 * Uses the default GenericParser from the HAPI API. This DataFormat <b>only</b> supports both the EDI based HL7
 * messages and the XML based messages.
 * <p/>
 * The <tt>unmarshal</tt> operation adds these MSH fields as headers on the Camel message (key, MSH-field):
 * <ul>
 *   <li>CamelHL7SendingApplication = MSH-3</li>
 *   <li>CamelHL7SendingFacility = MSH-4</li>
 *   <li>CamelHL7ReceivingApplication = MSH-5</li>
 *   <li>CamelHL7ReceivingFacility = MSH-6</li>
 *   <li>CamelHL7Timestamp = MSH-7</li>
 *   <li>CamelHL7Security = MSH-8</li>
 *   <li>CamelHL7MessageType = MSH-9-1</li>
 *   <li>CamelHL7TriggerEvent = MSH-9-2</li>
 *   <li>CamelHL7MessageControl = MSH-10</li>
 *   <li>CamelHL7ProcessingId = MSH-11</li>
 *   <li>CamelHL7VersionId = MSH-12</li>
 * </ul>
 * All headers are String types.
 * <p/>
 *
 * @see org.apache.camel.component.hl7.HL7MLLPCodec
 */
public class HL7DataFormat implements DataFormat {

    private static final Map<String, String> HEADER_MAP = new HashMap<String, String>();

    private Parser parser = new GenericParser();
    
    static {
        HEADER_MAP.put(HL7_SENDING_APPLICATION, "MSH-3");
        HEADER_MAP.put(HL7_SENDING_FACILITY, "MSH-4");
        HEADER_MAP.put(HL7_RECEIVING_APPLICATION, "MSH-5");
        HEADER_MAP.put(HL7_RECEIVING_FACILITY, "MSH-6");
        HEADER_MAP.put(HL7_TIMESTAMP, "MSH-7");
        HEADER_MAP.put(HL7_SECURITY, "MSH-8");
        HEADER_MAP.put(HL7_MESSAGE_TYPE, "MSH-9-1");
        HEADER_MAP.put(HL7_TRIGGER_EVENT, "MSH-9-2");
        HEADER_MAP.put(HL7_MESSAGE_CONTROL, "MSH-10");
        HEADER_MAP.put(HL7_PROCESSING_ID, "MSH-11");
        HEADER_MAP.put(HL7_VERSION_ID, "MSH-12");
    }

    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        Message message = ExchangeHelper.convertToMandatoryType(exchange, Message.class, body);
        String encoded = HL7Converter.encode(message, parser);
        String charsetName = IOHelper.getCharsetName(exchange);
        outputStream.write(encoded.getBytes(charsetName));
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        String body = ExchangeHelper.convertToMandatoryType(exchange, String.class, inputStream);
        Message message = HL7Converter.parse(body, parser);

        // add MSH fields as message out headers
        Terser terser = new Terser(message);
        for (Map.Entry<String, String> entry : HEADER_MAP.entrySet()) {
            exchange.getOut().setHeader(entry.getKey(), terser.get(entry.getValue()));
        }
        return message;
    }

    public boolean isValidate() {
        return parser.getValidationContext() instanceof NoValidation;
    }

    public void setValidate(boolean validate) {
        if (!validate) {
            parser.setValidationContext(new NoValidation());
        }
    }

    public Parser getParser() {
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }
    
    
}

