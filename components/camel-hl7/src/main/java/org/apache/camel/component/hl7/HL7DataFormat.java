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

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;

import static org.apache.camel.component.hl7.HL7Constants.*;

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
 *   <li>CamelHL7Charset = MSH-18</li>
 * </ul>
 * All headers are String types.
 * <p/>
 *
 * @see org.apache.camel.component.hl7.HL7MLLPCodec
 */
public class HL7DataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private static final Map<String, String> HEADER_MAP = new HashMap<String, String>();

    private HapiContext hapiContext;
    private Parser parser;
    private boolean validate = true;
    
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
        HEADER_MAP.put(HL7_CHARSET, "MSH-18");
    }

    @Override
    public String getDataFormatName() {
        return "hl7";
    }

    public void marshal(Exchange exchange, Object body, OutputStream outputStream) throws Exception {
        Message message = ExchangeHelper.convertToMandatoryType(exchange, Message.class, body);
        String charsetName = HL7Charset.getCharsetName(message, exchange);
        String encoded = HL7Converter.encode(message, parser);
        outputStream.write(encoded.getBytes(charsetName));
    }

    public Object unmarshal(Exchange exchange, InputStream inputStream) throws Exception {
        byte[] body = ExchangeHelper.convertToMandatoryType(exchange, byte[].class, inputStream);
        String charsetName = HL7Charset.getCharsetName(body, guessCharsetName(body, exchange));
        String bodyAsString = new String(body, charsetName);
        Message message = HL7Converter.parse(bodyAsString, parser);

        // add MSH fields as message out headers
        Terser terser = new Terser(message);
        for (Map.Entry<String, String> entry : HEADER_MAP.entrySet()) {
            exchange.getOut().setHeader(entry.getKey(), terser.get(entry.getValue()));
        }
        exchange.getOut().setHeader(HL7_CONTEXT, hapiContext);
        exchange.getOut().setHeader(Exchange.CHARSET_NAME, charsetName);
        return message;
    }

    public boolean isValidate() {
        return validate;
    }

    public void setValidate(boolean validate) {
        this.validate = validate;
    }

    public HapiContext getHapiContext() {
        return hapiContext;
    }

    public void setHapiContext(HapiContext context) {
        this.hapiContext = context;
    }

    public Parser getParser() {
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    @Override
    protected void doStart() throws Exception {
        if (hapiContext == null) {
            hapiContext = new DefaultHapiContext();
        }
        if (parser == null) {
            parser = hapiContext.getGenericParser();
        }
        if (!validate) {
            parser.setValidationContext(ValidationContextFactory.noValidation());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    /**
     * In HL7 the charset of the message can be set in MSH-18,
     * but you need to decode the input stream in order to be able to read MSH-18.
     * This works well for differentiating e.g. between ASCII, UTF-8 and ISI-8859 charsets,
     * but not for multi-byte charsets like UTF-16, Big5 etc.
     *
     * This method is called to "guess" the initial encoding, and subclasses can overwrite it
     * using 3rd party libraries like ICU4J that provide a CharsetDetector.
     *
     * The implementation in this class just assumes the charset defined in the exchange property or header by
     * calling {@link org.apache.camel.util.IOHelper#getCharsetName(org.apache.camel.Exchange)}.
     *
     * @param b byte array
     * @param exchange the exchange
     * @return charset name
     */
    protected String guessCharsetName(byte[] b, Exchange exchange) {
        return IOHelper.getCharsetName(exchange);
    }

}

