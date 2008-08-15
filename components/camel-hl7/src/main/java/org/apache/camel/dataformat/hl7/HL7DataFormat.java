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

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;



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
 * Uses the default PipeParser from the HAPI API. This DataFormat <b>only</b> supports the EDI based HL7
 * messages and not the XML based (their are not commonly used).
 * <p/>
 * The <tt>unmarshal</tt> operation adds these MSH fields as headers on the Camel message (key, MSH-field):
 * <ul>
 *   <li>hl7.msh.sendingApplication = MSH-3</li>
 *   <li>hl7.msh.sendingFacility = MSH-4</li>
 *   <li>hl7.msh.receivingApplication = MSH-5</li>
 *   <li>hl7.msh.receivingFacility = MSH-6</li>
 *   <li>hl7.msh.timestamp = MSH-7</li>
 *   <li>hl7.msh.security = MSH-8</li>
 *   <li>hl7.msh.messageType = MSH-9-1</li>
 *   <li>hl7.msh.triggerEvent = MSH-9-2</li>
 *   <li>hl7.msh.messageControl = MSH-10</li>
 *   <li>hl7.msh.processingId = MSH-11</li>
 *   <li>hl7.msh.versionId = MSH-12</li>
 * </ul>
 * All headers are String types.
 * <p/>
 * The <a href="http://www.hl7.org/Special/IG/final.pdf">HL7 spec</a> can be downloaded as a pdf at
 *
 * @see org.apache.camel.component.hl7.HL7MLLPCodec
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

        // add MSH fields as message out headers
        Terser terser = new Terser(message);
        exchange.getOut().setHeader("hl7.msh.sendingApplication", terser.get("MSH-3"));
        exchange.getOut().setHeader("hl7.msh.sendingFacility", terser.get("MSH-4"));
        exchange.getOut().setHeader("hl7.msh.receivingApplication", terser.get("MSH-5"));
        exchange.getOut().setHeader("hl7.msh.receivingFacility", terser.get("MSH-6"));
        exchange.getOut().setHeader("hl7.msh.timestamp", terser.get("MSH-7"));
        exchange.getOut().setHeader("hl7.msh.security", terser.get("MSH-8"));
        exchange.getOut().setHeader("hl7.msh.messageType", terser.get("MSH-9-1"));
        exchange.getOut().setHeader("hl7.msh.triggerEvent", terser.get("MSH-9-2"));
        exchange.getOut().setHeader("hl7.msh.messageControl", terser.get("MSH-10"));
        exchange.getOut().setHeader("hl7.msh.processingId", terser.get("MSH-11"));
        exchange.getOut().setHeader("hl7.msh.versionId", terser.get("MSH-12"));
        return message;
    }

}

