/*
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

import java.io.IOException;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.GenericMessage;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.GenericModelClassFactory;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.parser.ParserConfiguration;
import ca.uhn.hl7v2.parser.UnexpectedSegmentBehaviourEnum;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.TypeConversionException;
import org.apache.camel.converter.IOConverter;

/**
 * HL7 converters.
 */
@Converter(generateLoader = true)
public final class HL7GenericMessageConverter {

    private static final HapiContext GENERIC_MESSAGE_CONTEXT;

    static {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setDefaultObx2Type("ST");
        parserConfiguration.setInvalidObx2Type("ST");
        parserConfiguration.setUnexpectedSegmentBehaviour(UnexpectedSegmentBehaviourEnum.ADD_INLINE);

        GENERIC_MESSAGE_CONTEXT = new DefaultHapiContext(parserConfiguration, ValidationContextFactory.noValidation(), new GenericModelClassFactory());
    }

    private HL7GenericMessageConverter() {
        // Helper class
    }

    @Converter
    public static GenericMessage toGenericMessage(String body) throws HL7Exception {
        return (GenericMessage) GENERIC_MESSAGE_CONTEXT.getGenericParser().parse(body);
    }

    @Converter
    public static GenericMessage toGenericMessage(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return (GenericMessage) GENERIC_MESSAGE_CONTEXT.getGenericParser().parse(IOConverter.toString(body, exchange));
    }

    @Converter
    public static GenericMessage.V21 toV21GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V21.class, hl7String);
    }

    @Converter
    public static GenericMessage.V21 toV21GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V21.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V22 toV22GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V22.class, hl7String);
    }

    @Converter
    public static GenericMessage.V22 toV22GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V22.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V23 toV23GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V23.class, hl7String);
    }

    @Converter
    public static GenericMessage.V23 toV23GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V23.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V231 toV231GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V231.class, hl7String);
    }

    @Converter
    public static GenericMessage.V231 toV231GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V231.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V24 toV24GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V24.class, hl7String);
    }

    @Converter
    public static GenericMessage.V24 toV24GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V24.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V25 toV25GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V25.class, hl7String);
    }

    @Converter
    public static GenericMessage.V25 toV25GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V25.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V251 toV251GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V251.class, hl7String);
    }

    @Converter
    public static GenericMessage.V251 toV251GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V251.class, hl7Bytes, exchange);
    }

    @Converter
    public static GenericMessage.V26 toV26GenericMessage(String hl7String) throws HL7Exception {
        return toGenericMessage(GenericMessage.V26.class, hl7String);
    }

    @Converter
    public static GenericMessage.V26 toV26GenericMessage(byte[] hl7Bytes, Exchange exchange) {
        return toGenericMessage(GenericMessage.V26.class, hl7Bytes, exchange);
    }

    static Message parse(String body, Parser parser) throws HL7Exception {
        return parser.parse(body);
    }

    static String encode(Message message, Parser parser) throws HL7Exception {
        return parser.encode(message);
    }

    static <T extends Message> T toGenericMessage(Class<T> messageClass, String hl7String) {
        try {
            T genericMessage = GENERIC_MESSAGE_CONTEXT.newMessage(messageClass);

            genericMessage.parse(hl7String);

            return genericMessage;
        } catch (HL7Exception conversionEx) {
            throw new TypeConversionException(hl7String, String.class, conversionEx);

        }
    }


    static <T extends Message> T toGenericMessage(Class<T> messageClass, byte[] hl7Bytes, Exchange exchange) {
        try {
            T genericMessage = GENERIC_MESSAGE_CONTEXT.newMessage(messageClass);

            genericMessage.parse(IOConverter.toString(hl7Bytes, exchange));

            return genericMessage;
        } catch (HL7Exception | IOException conversionEx) {
            throw new TypeConversionException(hl7Bytes, byte[].class, conversionEx);
        }
    }
}
