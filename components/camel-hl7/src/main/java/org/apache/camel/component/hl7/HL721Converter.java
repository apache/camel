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
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v21.message.ACK;
import ca.uhn.hl7v2.model.v21.message.ADR_A19;
import ca.uhn.hl7v2.model.v21.message.ADT_A01;
import ca.uhn.hl7v2.model.v21.message.ADT_A02;
import ca.uhn.hl7v2.model.v21.message.ADT_A03;
import ca.uhn.hl7v2.model.v21.message.ADT_A04;
import ca.uhn.hl7v2.model.v21.message.ADT_A05;
import ca.uhn.hl7v2.model.v21.message.ADT_A06;
import ca.uhn.hl7v2.model.v21.message.ADT_A07;
import ca.uhn.hl7v2.model.v21.message.ADT_A08;
import ca.uhn.hl7v2.model.v21.message.ADT_A09;
import ca.uhn.hl7v2.model.v21.message.ADT_A10;
import ca.uhn.hl7v2.model.v21.message.ADT_A11;
import ca.uhn.hl7v2.model.v21.message.ADT_A12;
import ca.uhn.hl7v2.model.v21.message.ADT_A13;
import ca.uhn.hl7v2.model.v21.message.ADT_A14;
import ca.uhn.hl7v2.model.v21.message.ADT_A15;
import ca.uhn.hl7v2.model.v21.message.ADT_A16;
import ca.uhn.hl7v2.model.v21.message.ADT_A17;
import ca.uhn.hl7v2.model.v21.message.ADT_A18;
import ca.uhn.hl7v2.model.v21.message.ADT_A20;
import ca.uhn.hl7v2.model.v21.message.ADT_A21;
import ca.uhn.hl7v2.model.v21.message.ADT_A22;
import ca.uhn.hl7v2.model.v21.message.ADT_A23;
import ca.uhn.hl7v2.model.v21.message.ADT_A24;
import ca.uhn.hl7v2.model.v21.message.ADT_AXX;
import ca.uhn.hl7v2.model.v21.message.BAR_P01;
import ca.uhn.hl7v2.model.v21.message.BAR_P02;
import ca.uhn.hl7v2.model.v21.message.DFT_P03;
import ca.uhn.hl7v2.model.v21.message.DSR_Q01;
import ca.uhn.hl7v2.model.v21.message.DSR_Q03;
import ca.uhn.hl7v2.model.v21.message.MCF_Q02;
import ca.uhn.hl7v2.model.v21.message.ORM_O01;
import ca.uhn.hl7v2.model.v21.message.ORR_O02;
import ca.uhn.hl7v2.model.v21.message.ORU_R01;
import ca.uhn.hl7v2.model.v21.message.ORU_R03;
import ca.uhn.hl7v2.model.v21.message.QRY_A19;
import ca.uhn.hl7v2.model.v21.message.QRY_Q01;
import ca.uhn.hl7v2.model.v21.message.QRY_Q02;
import ca.uhn.hl7v2.model.v21.message.UDM_Q05;
import ca.uhn.hl7v2.parser.DefaultModelClassFactory;
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
@Converter(generateLoader = true, ignoreOnLoadError = true)
public final class HL721Converter {

    private static final HapiContext DEFAULT_CONTEXT;

    static {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setDefaultObx2Type("ST");
        parserConfiguration.setInvalidObx2Type("ST");
        parserConfiguration.setUnexpectedSegmentBehaviour(UnexpectedSegmentBehaviourEnum.ADD_INLINE);

        DEFAULT_CONTEXT = new DefaultHapiContext(parserConfiguration, ValidationContextFactory.noValidation(), new DefaultModelClassFactory());
    }

    private HL721Converter() {
        // Helper class
    }

    @Converter
    public static ACK toACK(String body) throws HL7Exception {
        return toMessage(ACK.class, body);
    }

    @Converter
    public static ACK toACK(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ACK.class, body, exchange);
    }

    @Converter
    public static ADR_A19 toAdrA19(String body) throws HL7Exception {
        return toMessage(ADR_A19.class, body);
    }

    @Converter
    public static ADR_A19 toAdrA19(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADR_A19.class, body, exchange);
    }

    @Converter
    public static ADT_A01 toAdtA01(String body) throws HL7Exception {
        return toMessage(ADT_A01.class, body);
    }

    @Converter
    public static ADT_A01 toAdtA01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A01.class, body, exchange);
    }

    @Converter
    public static ADT_A02 toAdtA02(String body) throws HL7Exception {
        return toMessage(ADT_A02.class, body);
    }

    @Converter
    public static ADT_A02 toAdtA02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A02.class, body, exchange);
    }

    @Converter
    public static ADT_A03 toAdtA03(String body) throws HL7Exception {
        return toMessage(ADT_A03.class, body);
    }

    @Converter
    public static ADT_A03 toAdtA03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A03.class, body, exchange);
    }

    @Converter
    public static ADT_A04 toAdtA04(String body) throws HL7Exception {
        return toMessage(ADT_A04.class, body);
    }

    @Converter
    public static ADT_A04 toAdtA04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A04.class, body, exchange);
    }

    @Converter
    public static ADT_A05 toAdtA05(String body) throws HL7Exception {
        return toMessage(ADT_A05.class, body);
    }

    @Converter
    public static ADT_A05 toAdtA05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A05.class, body, exchange);
    }

    @Converter
    public static ADT_A06 toAdtA06(String body) throws HL7Exception {
        return toMessage(ADT_A06.class, body);
    }

    @Converter
    public static ADT_A06 toAdtA06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A06.class, body, exchange);
    }
    @Converter
    public static ADT_A07 toAdtA07(String body) throws HL7Exception {
        return toMessage(ADT_A07.class, body);
    }

    @Converter
    public static ADT_A07 toAdtA07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A07.class, body, exchange);
    }

    @Converter
    public static ADT_A08 toAdtA08(String body) throws HL7Exception {
        return toMessage(ADT_A08.class, body);
    }

    @Converter
    public static ADT_A08 toAdtA08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A08.class, body, exchange);
    }

    @Converter
    public static ADT_A09 toAdtA09(String body) throws HL7Exception {
        return toMessage(ADT_A09.class, body);
    }

    @Converter
    public static ADT_A09 toAdtA09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A09.class, body, exchange);
    }

    @Converter
    public static ADT_A10 toAdtA10(String body) throws HL7Exception {
        return toMessage(ADT_A10.class, body);
    }

    @Converter
    public static ADT_A10 toAdtA10(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A10.class, body, exchange);
    }

    @Converter
    public static ADT_A11 toAdtA11(String body) throws HL7Exception {
        return toMessage(ADT_A11.class, body);
    }

    @Converter
    public static ADT_A11 toAdtA11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A11.class, body, exchange);
    }
    @Converter
    public static ADT_A12 toAdtA12(String body) throws HL7Exception {
        return toMessage(ADT_A12.class, body);
    }

    @Converter
    public static ADT_A12 toAdtA12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A12.class, body, exchange);
    }

    @Converter
    public static ADT_A13 toAdtA13(String body) throws HL7Exception {
        return toMessage(ADT_A13.class, body);
    }

    @Converter
    public static ADT_A13 toAdtA13(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A13.class, body, exchange);
    }

    @Converter
    public static ADT_A14 toAdtA14(String body) throws HL7Exception {
        return toMessage(ADT_A14.class, body);
    }

    @Converter
    public static ADT_A14 toAdtA14(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A14.class, body, exchange);
    }

    @Converter
    public static ADT_A15 toAdtA15(String body) throws HL7Exception {
        return toMessage(ADT_A15.class, body);
    }

    @Converter
    public static ADT_A15 toAdtA15(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A15.class, body, exchange);
    }

    @Converter
    public static ADT_A16 toAdtA16(String body) throws HL7Exception {
        return toMessage(ADT_A16.class, body);
    }

    @Converter
    public static ADT_A16 toAdtA16(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A16.class, body, exchange);
    }

    @Converter
    public static ADT_A17 toAdtA17(String body) throws HL7Exception {
        return toMessage(ADT_A17.class, body);
    }

    @Converter
    public static ADT_A17 toAdtA17(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A17.class, body, exchange);
    }
    @Converter
    public static ADT_A18 toAdtA18(String body) throws HL7Exception {
        return toMessage(ADT_A18.class, body);
    }

    @Converter
    public static ADT_A18 toAdtA18(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A18.class, body, exchange);
    }

    @Converter
    public static ADT_A20 toAdtA20(String body) throws HL7Exception {
        return toMessage(ADT_A20.class, body);
    }

    @Converter
    public static ADT_A20 toAdtA20(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A20.class, body, exchange);
    }

    @Converter
    public static ADT_A21 toAdtA21(String body) throws HL7Exception {
        return toMessage(ADT_A21.class, body);
    }

    @Converter
    public static ADT_A21 toAdtA21(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A21.class, body, exchange);
    }

    @Converter
    public static ADT_A22 toAdtA22(String body) throws HL7Exception {
        return toMessage(ADT_A22.class, body);
    }

    @Converter
    public static ADT_A22 toAdtA22(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A22.class, body, exchange);
    }

    @Converter
    public static ADT_A23 toAdtA23(String body) throws HL7Exception {
        return toMessage(ADT_A23.class, body);
    }

    @Converter
    public static ADT_A23 toAdtA23(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A23.class, body, exchange);
    }

    @Converter
    public static ADT_A24 toAdtA24(String body) throws HL7Exception {
        return toMessage(ADT_A24.class, body);
    }

    @Converter
    public static ADT_A24 toAdtA24(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A24.class, body, exchange);
    }

    @Converter
    public static ADT_AXX toAdtAXX(String body) throws HL7Exception {
        return toMessage(ADT_AXX.class, body);
    }

    @Converter
    public static ADT_AXX toAdtAXX(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_AXX.class, body, exchange);
    }

    @Converter
    public static BAR_P01 toBarP01(String body) throws HL7Exception {
        return toMessage(BAR_P01.class, body);
    }

    @Converter
    public static BAR_P01 toBarP01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BAR_P01.class, body, exchange);
    }

    @Converter
    public static BAR_P02 toBarP02(String body) throws HL7Exception {
        return toMessage(BAR_P02.class, body);
    }

    @Converter
    public static BAR_P02 toBarP02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BAR_P02.class, body, exchange);
    }

    @Converter
    public static DFT_P03 toDftP03(String body) throws HL7Exception {
        return toMessage(DFT_P03.class, body);
    }

    @Converter
    public static DFT_P03 toDftP03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(DFT_P03.class, body, exchange);
    }

    @Converter
    public static DSR_Q01 toDsrQ01(String body) throws HL7Exception {
        return toMessage(DSR_Q01.class, body);
    }

    @Converter
    public static DSR_Q01 toDsrQ01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(DSR_Q01.class, body, exchange);
    }

    @Converter
    public static DSR_Q03 toDsrQ03(String body) throws HL7Exception {
        return toMessage(DSR_Q03.class, body);
    }

    @Converter
    public static DSR_Q03 toDsrQ03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(DSR_Q03.class, body, exchange);
    }

    @Converter
    public static MCF_Q02 toMcfQ02(String body) throws HL7Exception {
        return toMessage(MCF_Q02.class, body);
    }

    @Converter
    public static MCF_Q02 toMcfQ02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MCF_Q02.class, body, exchange);
    }

    @Converter
    public static ORM_O01 toOrmO01(String body) throws HL7Exception {
        return toMessage(ORM_O01.class, body);
    }

    @Converter
    public static ORM_O01 toOrmO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORM_O01.class, body, exchange);
    }

    @Converter
    public static ORR_O02 toOrrO02(String body) throws HL7Exception {
        return toMessage(ORR_O02.class, body);
    }

    @Converter
    public static ORR_O02 toOrrO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORR_O02.class, body, exchange);
    }

    @Converter
    public static ORU_R01 toOruR01(String body) throws HL7Exception {
        return toMessage(ORU_R01.class, body);
    }

    @Converter
    public static ORU_R01 toOruR01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORU_R01.class, body, exchange);
    }

    @Converter
    public static ORU_R03 toOruR03(String body) throws HL7Exception {
        return toMessage(ORU_R03.class, body);
    }

    @Converter
    public static ORU_R03 toOruR03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORU_R03.class, body, exchange);
    }

    @Converter
    public static QRY_A19 toQryA19(String body) throws HL7Exception {
        return toMessage(QRY_A19.class, body);
    }

    @Converter
    public static QRY_A19 toQryA19(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_A19.class, body, exchange);
    }

    @Converter
    public static QRY_Q01 toQryQ01(String body) throws HL7Exception {
        return toMessage(QRY_Q01.class, body);
    }

    @Converter
    public static QRY_Q01 toQryQ01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_Q01.class, body, exchange);
    }

    @Converter
    public static QRY_Q02 toQryQ02(String body) throws HL7Exception {
        return toMessage(QRY_Q02.class, body);
    }

    @Converter
    public static QRY_Q02 toQryQ02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_Q02.class, body, exchange);
    }

    @Converter
    public static UDM_Q05 toUdmQ05(String body) throws HL7Exception {
        return toMessage(UDM_Q05.class, body);
    }

    @Converter
    public static UDM_Q05 toUdmQ05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(UDM_Q05.class, body, exchange);
    }

    static <T extends Message> T toMessage(Class<T> messageClass, String hl7String) {
        try {
            T genericMessage = DEFAULT_CONTEXT.newMessage(messageClass);

            genericMessage.parse(hl7String);

            return genericMessage;
        } catch (HL7Exception conversionEx) {
            throw new TypeConversionException(hl7String, String.class, conversionEx);

        }
    }


    static <T extends Message> T toMessage(Class<T> messageClass, byte[] hl7Bytes, Exchange exchange) {
        try {
            T genericMessage = DEFAULT_CONTEXT.newMessage(messageClass);

            genericMessage.parse(IOConverter.toString(hl7Bytes, exchange));

            return genericMessage;
        } catch (HL7Exception | IOException conversionEx) {
            throw new TypeConversionException(hl7Bytes, byte[].class, conversionEx);
        }
    }
}
