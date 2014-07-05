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

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

import org.apache.camel.Converter;

/**
 * HL7 converters.
 */
@Converter
public final class HL7Converter {

    private HL7Converter() {
        // Helper class
    }

    private static Parser getParser() {
        PipeParser pipeParser = new PipeParser();
        pipeParser.setValidationContext(new NoValidation());
        return pipeParser;
    }

    @Converter
    public static String toString(Message message) throws HL7Exception {
        return encode(message, getParser());
    }

    @Converter
    public static Message toMessage(String body) throws HL7Exception {
        return parse(body, getParser());
    }

    static Message parse(String body, Parser parser) throws HL7Exception {
        return parser.parse(body);
    }

    static String encode(Message message, Parser parser) throws HL7Exception {
        return parser.encode(message);
    }

}
