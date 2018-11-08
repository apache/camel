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

import java.io.IOException;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v231.message.ACK;
import ca.uhn.hl7v2.model.v231.message.ADR_A19;
import ca.uhn.hl7v2.model.v231.message.ADT_A01;
import ca.uhn.hl7v2.model.v231.message.ADT_A02;
import ca.uhn.hl7v2.model.v231.message.ADT_A03;
import ca.uhn.hl7v2.model.v231.message.ADT_A04;
import ca.uhn.hl7v2.model.v231.message.ADT_A05;
import ca.uhn.hl7v2.model.v231.message.ADT_A06;
import ca.uhn.hl7v2.model.v231.message.ADT_A07;
import ca.uhn.hl7v2.model.v231.message.ADT_A08;
import ca.uhn.hl7v2.model.v231.message.ADT_A09;
import ca.uhn.hl7v2.model.v231.message.ADT_A10;
import ca.uhn.hl7v2.model.v231.message.ADT_A11;
import ca.uhn.hl7v2.model.v231.message.ADT_A12;
import ca.uhn.hl7v2.model.v231.message.ADT_A13;
import ca.uhn.hl7v2.model.v231.message.ADT_A14;
import ca.uhn.hl7v2.model.v231.message.ADT_A15;
import ca.uhn.hl7v2.model.v231.message.ADT_A16;
import ca.uhn.hl7v2.model.v231.message.ADT_A17;
import ca.uhn.hl7v2.model.v231.message.ADT_A18;
import ca.uhn.hl7v2.model.v231.message.ADT_A20;
import ca.uhn.hl7v2.model.v231.message.ADT_A21;
import ca.uhn.hl7v2.model.v231.message.ADT_A22;
import ca.uhn.hl7v2.model.v231.message.ADT_A23;
import ca.uhn.hl7v2.model.v231.message.ADT_A24;
import ca.uhn.hl7v2.model.v231.message.ADT_A25;
import ca.uhn.hl7v2.model.v231.message.ADT_A26;
import ca.uhn.hl7v2.model.v231.message.ADT_A27;
import ca.uhn.hl7v2.model.v231.message.ADT_A28;
import ca.uhn.hl7v2.model.v231.message.ADT_A29;
import ca.uhn.hl7v2.model.v231.message.ADT_A30;
import ca.uhn.hl7v2.model.v231.message.ADT_A31;
import ca.uhn.hl7v2.model.v231.message.ADT_A32;
import ca.uhn.hl7v2.model.v231.message.ADT_A33;
import ca.uhn.hl7v2.model.v231.message.ADT_A34;
import ca.uhn.hl7v2.model.v231.message.ADT_A35;
import ca.uhn.hl7v2.model.v231.message.ADT_A36;
import ca.uhn.hl7v2.model.v231.message.ADT_A37;
import ca.uhn.hl7v2.model.v231.message.ADT_A38;
import ca.uhn.hl7v2.model.v231.message.ADT_A39;
import ca.uhn.hl7v2.model.v231.message.ADT_A40;
import ca.uhn.hl7v2.model.v231.message.ADT_A41;
import ca.uhn.hl7v2.model.v231.message.ADT_A42;
import ca.uhn.hl7v2.model.v231.message.ADT_A43;
import ca.uhn.hl7v2.model.v231.message.ADT_A44;
import ca.uhn.hl7v2.model.v231.message.ADT_A45;
import ca.uhn.hl7v2.model.v231.message.ADT_A46;
import ca.uhn.hl7v2.model.v231.message.ADT_A47;
import ca.uhn.hl7v2.model.v231.message.ADT_A48;
import ca.uhn.hl7v2.model.v231.message.ADT_A49;
import ca.uhn.hl7v2.model.v231.message.ADT_A50;
import ca.uhn.hl7v2.model.v231.message.ADT_A51;
import ca.uhn.hl7v2.model.v231.message.ADT_AXX;
import ca.uhn.hl7v2.model.v231.message.BAR_P01;
import ca.uhn.hl7v2.model.v231.message.BAR_P02;
import ca.uhn.hl7v2.model.v231.message.BAR_P05;
import ca.uhn.hl7v2.model.v231.message.BAR_P06;
import ca.uhn.hl7v2.model.v231.message.CRM_C01;
import ca.uhn.hl7v2.model.v231.message.CSU_C09;
import ca.uhn.hl7v2.model.v231.message.DFT_P03;
import ca.uhn.hl7v2.model.v231.message.DOC_T12;
import ca.uhn.hl7v2.model.v231.message.DSR_Q01;
import ca.uhn.hl7v2.model.v231.message.DSR_Q03;
import ca.uhn.hl7v2.model.v231.message.EDR_R07;
import ca.uhn.hl7v2.model.v231.message.EQQ_Q04;
import ca.uhn.hl7v2.model.v231.message.ERP_R09;
import ca.uhn.hl7v2.model.v231.message.MDM_T01;
import ca.uhn.hl7v2.model.v231.message.MDM_T02;
import ca.uhn.hl7v2.model.v231.message.MDM_T03;
import ca.uhn.hl7v2.model.v231.message.MDM_T04;
import ca.uhn.hl7v2.model.v231.message.MDM_T05;
import ca.uhn.hl7v2.model.v231.message.MDM_T06;
import ca.uhn.hl7v2.model.v231.message.MDM_T07;
import ca.uhn.hl7v2.model.v231.message.MDM_T08;
import ca.uhn.hl7v2.model.v231.message.MDM_T09;
import ca.uhn.hl7v2.model.v231.message.MDM_T10;
import ca.uhn.hl7v2.model.v231.message.MDM_T11;
import ca.uhn.hl7v2.model.v231.message.MFK_M01;
import ca.uhn.hl7v2.model.v231.message.MFK_M04;
import ca.uhn.hl7v2.model.v231.message.MFK_M05;
import ca.uhn.hl7v2.model.v231.message.MFK_M06;
import ca.uhn.hl7v2.model.v231.message.MFN_M01;
import ca.uhn.hl7v2.model.v231.message.MFN_M02;
import ca.uhn.hl7v2.model.v231.message.MFN_M03;
import ca.uhn.hl7v2.model.v231.message.MFN_M04;
import ca.uhn.hl7v2.model.v231.message.MFN_M05;
import ca.uhn.hl7v2.model.v231.message.MFN_M06;
import ca.uhn.hl7v2.model.v231.message.MFN_M08;
import ca.uhn.hl7v2.model.v231.message.MFN_M09;
import ca.uhn.hl7v2.model.v231.message.MFN_M10;
import ca.uhn.hl7v2.model.v231.message.MFN_M11;
import ca.uhn.hl7v2.model.v231.message.MFQ_M01;
import ca.uhn.hl7v2.model.v231.message.MFR_M01;
import ca.uhn.hl7v2.model.v231.message.NMD_N02;
import ca.uhn.hl7v2.model.v231.message.NMQ_N01;
import ca.uhn.hl7v2.model.v231.message.NMR_N01;
import ca.uhn.hl7v2.model.v231.message.OMD_O01;
import ca.uhn.hl7v2.model.v231.message.OMN_O01;
import ca.uhn.hl7v2.model.v231.message.OMS_O01;
import ca.uhn.hl7v2.model.v231.message.ORD_O02;
import ca.uhn.hl7v2.model.v231.message.ORF_R04;
import ca.uhn.hl7v2.model.v231.message.ORM_O01;
import ca.uhn.hl7v2.model.v231.message.ORN_O02;
import ca.uhn.hl7v2.model.v231.message.ORR_O02;
import ca.uhn.hl7v2.model.v231.message.ORS_O02;
import ca.uhn.hl7v2.model.v231.message.ORU_R01;
import ca.uhn.hl7v2.model.v231.message.OSQ_Q06;
import ca.uhn.hl7v2.model.v231.message.OSR_Q06;
import ca.uhn.hl7v2.model.v231.message.PEX_P07;
import ca.uhn.hl7v2.model.v231.message.PGL_PC6;
import ca.uhn.hl7v2.model.v231.message.PIN_I07;
import ca.uhn.hl7v2.model.v231.message.PPG_PCG;
import ca.uhn.hl7v2.model.v231.message.PPP_PCB;
import ca.uhn.hl7v2.model.v231.message.PPR_PC1;
import ca.uhn.hl7v2.model.v231.message.PPT_PCL;
import ca.uhn.hl7v2.model.v231.message.PPV_PCA;
import ca.uhn.hl7v2.model.v231.message.PRR_PC5;
import ca.uhn.hl7v2.model.v231.message.PTR_PCF;
import ca.uhn.hl7v2.model.v231.message.QCK_Q02;
import ca.uhn.hl7v2.model.v231.message.QRY_A19;
import ca.uhn.hl7v2.model.v231.message.QRY_PC4;
import ca.uhn.hl7v2.model.v231.message.QRY_PC9;
import ca.uhn.hl7v2.model.v231.message.QRY_PCE;
import ca.uhn.hl7v2.model.v231.message.QRY_PCK;
import ca.uhn.hl7v2.model.v231.message.QRY_Q01;
import ca.uhn.hl7v2.model.v231.message.QRY_Q02;
import ca.uhn.hl7v2.model.v231.message.QRY_R02;
import ca.uhn.hl7v2.model.v231.message.QRY_T12;
import ca.uhn.hl7v2.model.v231.message.RAR_RAR;
import ca.uhn.hl7v2.model.v231.message.RAS_O01;
import ca.uhn.hl7v2.model.v231.message.RCI_I05;
import ca.uhn.hl7v2.model.v231.message.RCL_I06;
import ca.uhn.hl7v2.model.v231.message.RDE_O01;
import ca.uhn.hl7v2.model.v231.message.RDO_O01;
import ca.uhn.hl7v2.model.v231.message.RDR_RDR;
import ca.uhn.hl7v2.model.v231.message.RDS_O01;
import ca.uhn.hl7v2.model.v231.message.REF_I12;
import ca.uhn.hl7v2.model.v231.message.RER_RER;
import ca.uhn.hl7v2.model.v231.message.RGR_RGR;
import ca.uhn.hl7v2.model.v231.message.RGV_O01;
import ca.uhn.hl7v2.model.v231.message.ROR_R0R;
import ca.uhn.hl7v2.model.v231.message.RPA_I08;
import ca.uhn.hl7v2.model.v231.message.RPI_I01;
import ca.uhn.hl7v2.model.v231.message.RPI_I04;
import ca.uhn.hl7v2.model.v231.message.RPL_I02;
import ca.uhn.hl7v2.model.v231.message.RPR_I03;
import ca.uhn.hl7v2.model.v231.message.RQA_I08;
import ca.uhn.hl7v2.model.v231.message.RQC_I05;
import ca.uhn.hl7v2.model.v231.message.RQC_I06;
import ca.uhn.hl7v2.model.v231.message.RQI_I01;
import ca.uhn.hl7v2.model.v231.message.RQI_I02;
import ca.uhn.hl7v2.model.v231.message.RQI_I03;
import ca.uhn.hl7v2.model.v231.message.RQP_I04;
import ca.uhn.hl7v2.model.v231.message.RQQ_Q09;
import ca.uhn.hl7v2.model.v231.message.RRA_O02;
import ca.uhn.hl7v2.model.v231.message.RRD_O02;
import ca.uhn.hl7v2.model.v231.message.RRE_O02;
import ca.uhn.hl7v2.model.v231.message.RRG_O02;
import ca.uhn.hl7v2.model.v231.message.RRI_I12;
import ca.uhn.hl7v2.model.v231.message.RRO_O02;
import ca.uhn.hl7v2.model.v231.message.SIU_S12;
import ca.uhn.hl7v2.model.v231.message.SPQ_Q08;
import ca.uhn.hl7v2.model.v231.message.SQM_S25;
import ca.uhn.hl7v2.model.v231.message.SQR_S25;
import ca.uhn.hl7v2.model.v231.message.SRM_S01;
import ca.uhn.hl7v2.model.v231.message.SRR_S01;
import ca.uhn.hl7v2.model.v231.message.SUR_P09;
import ca.uhn.hl7v2.model.v231.message.TBR_R08;
import ca.uhn.hl7v2.model.v231.message.UDM_Q05;
import ca.uhn.hl7v2.model.v231.message.VQQ_Q07;
import ca.uhn.hl7v2.model.v231.message.VXQ_V01;
import ca.uhn.hl7v2.model.v231.message.VXR_V03;
import ca.uhn.hl7v2.model.v231.message.VXU_V04;
import ca.uhn.hl7v2.model.v231.message.VXX_V02;
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
@Converter(ignoreOnLoadError = true)
public final class HL7231Converter {

    private static final HapiContext DEFAULT_CONTEXT;

    static {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setDefaultObx2Type("ST");
        parserConfiguration.setInvalidObx2Type("ST");
        parserConfiguration.setUnexpectedSegmentBehaviour(UnexpectedSegmentBehaviourEnum.ADD_INLINE);

        DEFAULT_CONTEXT = new DefaultHapiContext(parserConfiguration, ValidationContextFactory.noValidation(), new DefaultModelClassFactory());
    }

    private HL7231Converter() {
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
    public static ADT_A25 toAdtA25(String body) throws HL7Exception {
        return toMessage(ADT_A25.class, body);
    }

    @Converter
    public static ADT_A25 toAdtA25(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A25.class, body, exchange);
    }

    @Converter
    public static ADT_A26 toAdtA26(String body) throws HL7Exception {
        return toMessage(ADT_A26.class, body);
    }

    @Converter
    public static ADT_A26 toAdtA26(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A26.class, body, exchange);
    }

    @Converter
    public static ADT_A27 toAdtA27(String body) throws HL7Exception {
        return toMessage(ADT_A27.class, body);
    }

    @Converter
    public static ADT_A27 toAdtA27(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A27.class, body, exchange);
    }

    @Converter
    public static ADT_A28 toAdtA28(String body) throws HL7Exception {
        return toMessage(ADT_A28.class, body);
    }

    @Converter
    public static ADT_A28 toAdtA28(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A28.class, body, exchange);
    }

    @Converter
    public static ADT_A29 toAdtA29(String body) throws HL7Exception {
        return toMessage(ADT_A29.class, body);
    }

    @Converter
    public static ADT_A29 toAdtA29(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A29.class, body, exchange);
    }

    @Converter
    public static ADT_A30 toAdtA30(String body) throws HL7Exception {
        return toMessage(ADT_A30.class, body);
    }

    @Converter
    public static ADT_A30 toAdtA30(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A30.class, body, exchange);
    }

    @Converter
    public static ADT_A31 toAdtA31(String body) throws HL7Exception {
        return toMessage(ADT_A31.class, body);
    }

    @Converter
    public static ADT_A31 toAdtA31(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A31.class, body, exchange);
    }

    @Converter
    public static ADT_A32 toAdtA32(String body) throws HL7Exception {
        return toMessage(ADT_A32.class, body);
    }

    @Converter
    public static ADT_A32 toAdtA32(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A32.class, body, exchange);
    }

    @Converter
    public static ADT_A33 toAdtA33(String body) throws HL7Exception {
        return toMessage(ADT_A33.class, body);
    }

    @Converter
    public static ADT_A33 toAdtA33(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A33.class, body, exchange);
    }

    @Converter
    public static ADT_A34 toAdtA34(String body) throws HL7Exception {
        return toMessage(ADT_A34.class, body);
    }

    @Converter
    public static ADT_A34 toAdtA34(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A34.class, body, exchange);
    }

    @Converter
    public static ADT_A35 toAdtA35(String body) throws HL7Exception {
        return toMessage(ADT_A35.class, body);
    }

    @Converter
    public static ADT_A35 toAdtA35(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A35.class, body, exchange);
    }

    @Converter
    public static ADT_A36 toAdtA36(String body) throws HL7Exception {
        return toMessage(ADT_A36.class, body);
    }

    @Converter
    public static ADT_A36 toAdtA36(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A36.class, body, exchange);
    }

    @Converter
    public static ADT_A37 toAdtA37(String body) throws HL7Exception {
        return toMessage(ADT_A37.class, body);
    }

    @Converter
    public static ADT_A37 toAdtA37(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A37.class, body, exchange);
    }

    @Converter
    public static ADT_A38 toAdtA38(String body) throws HL7Exception {
        return toMessage(ADT_A38.class, body);
    }

    @Converter
    public static ADT_A38 toAdtA38(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A38.class, body, exchange);
    }

    @Converter
    public static ADT_A39 toAdtA39(String body) throws HL7Exception {
        return toMessage(ADT_A39.class, body);
    }

    @Converter
    public static ADT_A39 toAdtA39(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A39.class, body, exchange);
    }

    @Converter
    public static ADT_A40 toAdtA40(String body) throws HL7Exception {
        return toMessage(ADT_A40.class, body);
    }

    @Converter
    public static ADT_A40 toAdtA40(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A40.class, body, exchange);
    }

    @Converter
    public static ADT_A41 toAdtA41(String body) throws HL7Exception {
        return toMessage(ADT_A41.class, body);
    }

    @Converter
    public static ADT_A41 toAdtA41(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A41.class, body, exchange);
    }

    @Converter
    public static ADT_A42 toAdtA42(String body) throws HL7Exception {
        return toMessage(ADT_A42.class, body);
    }

    @Converter
    public static ADT_A42 toAdtA42(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A42.class, body, exchange);
    }

    @Converter
    public static ADT_A43 toAdtA43(String body) throws HL7Exception {
        return toMessage(ADT_A43.class, body);
    }

    @Converter
    public static ADT_A43 toAdtA43(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A43.class, body, exchange);
    }

    @Converter
    public static ADT_A44 toAdtA44(String body) throws HL7Exception {
        return toMessage(ADT_A44.class, body);
    }

    @Converter
    public static ADT_A44 toAdtA44(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A44.class, body, exchange);
    }

    @Converter
    public static ADT_A45 toAdtA45(String body) throws HL7Exception {
        return toMessage(ADT_A45.class, body);
    }

    @Converter
    public static ADT_A45 toAdtA45(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A45.class, body, exchange);
    }

    @Converter
    public static ADT_A46 toAdtA66(String body) throws HL7Exception {
        return toMessage(ADT_A46.class, body);
    }

    @Converter
    public static ADT_A46 toAdtA46(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A46.class, body, exchange);
    }

    @Converter
    public static ADT_A47 toAdtA47(String body) throws HL7Exception {
        return toMessage(ADT_A47.class, body);
    }

    @Converter
    public static ADT_A47 toAdtA47(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A47.class, body, exchange);
    }

    @Converter
    public static ADT_A48 toAdtA48(String body) throws HL7Exception {
        return toMessage(ADT_A48.class, body);
    }

    @Converter
    public static ADT_A48 toAdtA48(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A48.class, body, exchange);
    }

    @Converter
    public static ADT_A49 toAdtA49(String body) throws HL7Exception {
        return toMessage(ADT_A49.class, body);
    }

    @Converter
    public static ADT_A49 toAdtA49(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A49.class, body, exchange);
    }

    @Converter
    public static ADT_A50 toAdtA50(String body) throws HL7Exception {
        return toMessage(ADT_A50.class, body);
    }

    @Converter
    public static ADT_A50 toAdtA50(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A50.class, body, exchange);
    }

    @Converter
    public static ADT_A51 toAdtA51(String body) throws HL7Exception {
        return toMessage(ADT_A51.class, body);
    }

    @Converter
    public static ADT_A51 toAdtA51(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A51.class, body, exchange);
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
    public static BAR_P05 toBarP05(String body) throws HL7Exception {
        return toMessage(BAR_P05.class, body);
    }

    @Converter
    public static BAR_P05 toBarP05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BAR_P05.class, body, exchange);
    }

    @Converter
    public static BAR_P06 toBarP06(String body) throws HL7Exception {
        return toMessage(BAR_P06.class, body);
    }

    @Converter
    public static BAR_P06 toBarP06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BAR_P06.class, body, exchange);
    }

    @Converter
    public static CRM_C01 toCrmC01(String body) throws HL7Exception {
        return toMessage(CRM_C01.class, body);
    }

    @Converter
    public static CRM_C01 toCrmC01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(CRM_C01.class, body, exchange);
    }

    @Converter
    public static CSU_C09 toCsuC09(String body) throws HL7Exception {
        return toMessage(CSU_C09.class, body);
    }

    @Converter
    public static CSU_C09 toCsuC09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(CSU_C09.class, body, exchange);
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
    public static DOC_T12 toDocT12(String body) throws HL7Exception {
        return toMessage(DOC_T12.class, body);
    }

    @Converter
    public static DOC_T12 toDocT12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(DOC_T12.class, body, exchange);
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
    public static EDR_R07 toEdrR07(String body) throws HL7Exception {
        return toMessage(EDR_R07.class, body);
    }

    @Converter
    public static EDR_R07 toEdrR07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(EDR_R07.class, body, exchange);
    }

    @Converter
    public static EQQ_Q04 toEqqQ04(String body) throws HL7Exception {
        return toMessage(EQQ_Q04.class, body);
    }

    @Converter
    public static EQQ_Q04 toEqqQ04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(EQQ_Q04.class, body, exchange);
    }

    @Converter
    public static ERP_R09 toErpR09(String body) throws HL7Exception {
        return toMessage(ERP_R09.class, body);
    }

    @Converter
    public static ERP_R09 toErpR09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ERP_R09.class, body, exchange);
    }

    @Converter
    public static MDM_T01 toMdmT01(String body) throws HL7Exception {
        return toMessage(MDM_T01.class, body);
    }

    @Converter
    public static MDM_T01 toMdmT01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T01.class, body, exchange);
    }

    @Converter
    public static MDM_T02 toMdmT02(String body) throws HL7Exception {
        return toMessage(MDM_T02.class, body);
    }

    @Converter
    public static MDM_T02 toMdmT02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T02.class, body, exchange);
    }

    @Converter
    public static MDM_T03 toMdmT03(String body) throws HL7Exception {
        return toMessage(MDM_T03.class, body);
    }

    @Converter
    public static MDM_T03 toMdmT03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T03.class, body, exchange);
    }

    @Converter
    public static MDM_T04 toMdmT04(String body) throws HL7Exception {
        return toMessage(MDM_T04.class, body);
    }

    @Converter
    public static MDM_T04 toMdmT04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T04.class, body, exchange);
    }

    @Converter
    public static MDM_T05 toMdmT05(String body) throws HL7Exception {
        return toMessage(MDM_T05.class, body);
    }

    @Converter
    public static MDM_T05 toMdmT05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T05.class, body, exchange);
    }

    @Converter
    public static MDM_T06 toMdmT06(String body) throws HL7Exception {
        return toMessage(MDM_T06.class, body);
    }

    @Converter
    public static MDM_T06 toMdmT06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T06.class, body, exchange);
    }

    @Converter
    public static MDM_T07 toMdmT07(String body) throws HL7Exception {
        return toMessage(MDM_T07.class, body);
    }

    @Converter
    public static MDM_T07 toMdmT07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T07.class, body, exchange);
    }

    @Converter
    public static MDM_T08 toMdmT08(String body) throws HL7Exception {
        return toMessage(MDM_T08.class, body);
    }

    @Converter
    public static MDM_T08 toMdmT08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T08.class, body, exchange);
    }

    @Converter
    public static MDM_T09 toMdmT09(String body) throws HL7Exception {
        return toMessage(MDM_T09.class, body);
    }

    @Converter
    public static MDM_T09 toMdmT09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T09.class, body, exchange);
    }

    @Converter
    public static MDM_T10 toMdmT10(String body) throws HL7Exception {
        return toMessage(MDM_T10.class, body);
    }

    @Converter
    public static MDM_T10 toMdmT10(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T10.class, body, exchange);
    }

    @Converter
    public static MDM_T11 toMdmT11(String body) throws HL7Exception {
        return toMessage(MDM_T11.class, body);
    }

    @Converter
    public static MDM_T11 toMdmT11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MDM_T11.class, body, exchange);
    }

    @Converter
    public static MFK_M01 toMfkM01(String body) throws HL7Exception {
        return toMessage(MFK_M01.class, body);
    }

    @Converter
    public static MFK_M01 toMfkM01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFK_M01.class, body, exchange);
    }

    @Converter
    public static MFK_M04 toMfkM04(String body) throws HL7Exception {
        return toMessage(MFK_M04.class, body);
    }

    @Converter
    public static MFK_M04 toMfkM04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFK_M04.class, body, exchange);
    }

    @Converter
    public static MFK_M05 toMfkM05(String body) throws HL7Exception {
        return toMessage(MFK_M05.class, body);
    }

    @Converter
    public static MFK_M05 toMfkM05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFK_M05.class, body, exchange);
    }

    @Converter
    public static MFK_M06 toMfkM06(String body) throws HL7Exception {
        return toMessage(MFK_M06.class, body);
    }

    @Converter
    public static MFK_M06 toMfkM06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFK_M06.class, body, exchange);
    }

    @Converter
    public static MFN_M01 toMfnM01(String body) throws HL7Exception {
        return toMessage(MFN_M01.class, body);
    }

    @Converter
    public static MFN_M01 toMfnM01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M01.class, body, exchange);
    }

    @Converter
    public static MFN_M02 toMfnM02(String body) throws HL7Exception {
        return toMessage(MFN_M02.class, body);
    }

    @Converter
    public static MFN_M02 toMfnM02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M02.class, body, exchange);
    }

    @Converter
    public static MFN_M03 toMfnM03(String body) throws HL7Exception {
        return toMessage(MFN_M03.class, body);
    }

    @Converter
    public static MFN_M03 toMfnM03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M03.class, body, exchange);
    }

    @Converter
    public static MFN_M04 toMfnM04(String body) throws HL7Exception {
        return toMessage(MFN_M04.class, body);
    }

    @Converter
    public static MFN_M04 toMfnM04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M04.class, body, exchange);
    }

    @Converter
    public static MFN_M05 toMfnM05(String body) throws HL7Exception {
        return toMessage(MFN_M05.class, body);
    }

    @Converter
    public static MFN_M05 toMfnM05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M05.class, body, exchange);
    }

    @Converter
    public static MFN_M06 toMfnM06(String body) throws HL7Exception {
        return toMessage(MFN_M06.class, body);
    }

    @Converter
    public static MFN_M06 toMfnM06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M06.class, body, exchange);
    }

    @Converter
    public static MFN_M08 toMfnM08(String body) throws HL7Exception {
        return toMessage(MFN_M08.class, body);
    }

    @Converter
    public static MFN_M08 toMfnM08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M08.class, body, exchange);
    }

    @Converter
    public static MFN_M09 toMfnM09(String body) throws HL7Exception {
        return toMessage(MFN_M09.class, body);
    }

    @Converter
    public static MFN_M09 toMfnM09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M09.class, body, exchange);
    }

    @Converter
    public static MFN_M10 toMfnM10(String body) throws HL7Exception {
        return toMessage(MFN_M10.class, body);
    }

    @Converter
    public static MFN_M10 toMfnM10(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M10.class, body, exchange);
    }

    @Converter
    public static MFN_M11 toMfnM11(String body) throws HL7Exception {
        return toMessage(MFN_M11.class, body);
    }

    @Converter
    public static MFN_M11 toMfnM11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M11.class, body, exchange);
    }

    @Converter
    public static MFQ_M01 toMfqM01(String body) throws HL7Exception {
        return toMessage(MFQ_M01.class, body);
    }

    @Converter
    public static MFQ_M01 toMfqM01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFQ_M01.class, body, exchange);
    }

    @Converter
    public static MFR_M01 toMfrM01(String body) throws HL7Exception {
        return toMessage(MFR_M01.class, body);
    }

    @Converter
    public static MFR_M01 toMfrM01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFR_M01.class, body, exchange);
    }

    @Converter
    public static NMD_N02 toNmdN02(String body) throws HL7Exception {
        return toMessage(NMD_N02.class, body);
    }

    @Converter
    public static NMD_N02 toNmdN02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(NMD_N02.class, body, exchange);
    }

    @Converter
    public static NMQ_N01 toNmqN01(String body) throws HL7Exception {
        return toMessage(NMQ_N01.class, body);
    }

    @Converter
    public static NMQ_N01 toNmqN01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(NMQ_N01.class, body, exchange);
    }

    @Converter
    public static NMR_N01 toNmrN01(String body) throws HL7Exception {
        return toMessage(NMR_N01.class, body);
    }

    @Converter
    public static NMR_N01 toNmrN01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(NMR_N01.class, body, exchange);
    }

    @Converter
    public static OMD_O01 toOmdO01(String body) throws HL7Exception {
        return toMessage(OMD_O01.class, body);
    }

    @Converter
    public static OMD_O01 toOmdO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMD_O01.class, body, exchange);
    }

    @Converter
    public static OMN_O01 toOmnO01(String body) throws HL7Exception {
        return toMessage(OMN_O01.class, body);
    }

    @Converter
    public static OMN_O01 toOmnO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMN_O01.class, body, exchange);
    }

    @Converter
    public static OMS_O01 toOmsO01(String body) throws HL7Exception {
        return toMessage(OMS_O01.class, body);
    }

    @Converter
    public static OMS_O01 toOmsO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMS_O01.class, body, exchange);
    }

    @Converter
    public static ORD_O02 toOrdO02(String body) throws HL7Exception {
        return toMessage(ORD_O02.class, body);
    }

    @Converter
    public static ORD_O02 toOrdO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORD_O02.class, body, exchange);
    }

    @Converter
    public static ORF_R04 toOrfR04(String body) throws HL7Exception {
        return toMessage(ORF_R04.class, body);
    }

    @Converter
    public static ORF_R04 toOrfR04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORF_R04.class, body, exchange);
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
    public static ORN_O02 toOrnO02(String body) throws HL7Exception {
        return toMessage(ORN_O02.class, body);
    }

    @Converter
    public static ORN_O02 toOrnO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORN_O02.class, body, exchange);
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
    public static ORS_O02 toOrsO02(String body) throws HL7Exception {
        return toMessage(ORS_O02.class, body);
    }

    @Converter
    public static ORS_O02 toOrsO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORS_O02.class, body, exchange);
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
    public static OSQ_Q06 toOsqQ06(String body) throws HL7Exception {
        return toMessage(OSQ_Q06.class, body);
    }

    @Converter
    public static OSQ_Q06 toOsqQ06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OSQ_Q06.class, body, exchange);
    }

    @Converter
    public static OSR_Q06 toOsrQ06(String body) throws HL7Exception {
        return toMessage(OSR_Q06.class, body);
    }

    @Converter
    public static OSR_Q06 toOsrQ06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OSR_Q06.class, body, exchange);
    }

    @Converter
    public static PEX_P07 toPexP07(String body) throws HL7Exception {
        return toMessage(PEX_P07.class, body);
    }

    @Converter
    public static PEX_P07 toPexP07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PEX_P07.class, body, exchange);
    }

    @Converter
    public static PGL_PC6 toPglPc6(String body) throws HL7Exception {
        return toMessage(PGL_PC6.class, body);
    }

    @Converter
    public static PGL_PC6 toPglPc6(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PGL_PC6.class, body, exchange);
    }

    @Converter
    public static PIN_I07 toPinI07(String body) throws HL7Exception {
        return toMessage(PIN_I07.class, body);
    }

    @Converter
    public static PIN_I07 toPinI07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PIN_I07.class, body, exchange);
    }

    @Converter
    public static PPG_PCG toPpgPcg(String body) throws HL7Exception {
        return toMessage(PPG_PCG.class, body);
    }

    @Converter
    public static PPG_PCG toPpgPcg(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PPG_PCG.class, body, exchange);
    }

    @Converter
    public static PPP_PCB toPppPcb(String body) throws HL7Exception {
        return toMessage(PPP_PCB.class, body);
    }

    @Converter
    public static PPP_PCB toPppPcb(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PPP_PCB.class, body, exchange);
    }

    @Converter
    public static PPR_PC1 toPprPc1(String body) throws HL7Exception {
        return toMessage(PPR_PC1.class, body);
    }

    @Converter
    public static PPR_PC1 toPprPc1(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PPR_PC1.class, body, exchange);
    }

    @Converter
    public static PPT_PCL toPptPcl(String body) throws HL7Exception {
        return toMessage(PPT_PCL.class, body);
    }

    @Converter
    public static PPT_PCL toPptPcl(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PPT_PCL.class, body, exchange);
    }

    @Converter
    public static PPV_PCA toPpvPca(String body) throws HL7Exception {
        return toMessage(PPV_PCA.class, body);
    }

    @Converter
    public static PPV_PCA toPpvPca(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PPV_PCA.class, body, exchange);
    }

    @Converter
    public static PRR_PC5 toPrrPc5(String body) throws HL7Exception {
        return toMessage(PRR_PC5.class, body);
    }

    @Converter
    public static PRR_PC5 toPrrPc5(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PRR_PC5.class, body, exchange);
    }

    @Converter
    public static PTR_PCF toPtrPcf(String body) throws HL7Exception {
        return toMessage(PTR_PCF.class, body);
    }

    @Converter
    public static PTR_PCF toPtrPcf(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PTR_PCF.class, body, exchange);
    }

    @Converter
    public static QCK_Q02 toQckQ02(String body) throws HL7Exception {
        return toMessage(QCK_Q02.class, body);
    }

    @Converter
    public static QCK_Q02 toQckQ02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QCK_Q02.class, body, exchange);
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
    public static QRY_PC4 toQryPC4(String body) throws HL7Exception {
        return toMessage(QRY_PC4.class, body);
    }

    @Converter
    public static QRY_PC4 toQryPC4(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_PC4.class, body, exchange);
    }

    @Converter
    public static QRY_PC9 toQryPC9(String body) throws HL7Exception {
        return toMessage(QRY_PC9.class, body);
    }

    @Converter
    public static QRY_PC9 toQryPC9(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_PC9.class, body, exchange);
    }

    @Converter
    public static QRY_PCE toQryPCE(String body) throws HL7Exception {
        return toMessage(QRY_PCE.class, body);
    }

    @Converter
    public static QRY_PCE toQryPCE(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_PCE.class, body, exchange);
    }

    @Converter
    public static QRY_PCK toQryPCK(String body) throws HL7Exception {
        return toMessage(QRY_PCK.class, body);
    }

    @Converter
    public static QRY_PCK toQryPCK(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_PCK.class, body, exchange);
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
    public static QRY_R02 toQryR02(String body) throws HL7Exception {
        return toMessage(QRY_R02.class, body);
    }

    @Converter
    public static QRY_R02 toQryR02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_R02.class, body, exchange);
    }

    @Converter
    public static QRY_T12 toQryT12(String body) throws HL7Exception {
        return toMessage(QRY_T12.class, body);
    }

    @Converter
    public static QRY_T12 toQryT12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY_T12.class, body, exchange);
    }

    @Converter
    public static RAR_RAR toRarRar(String body) throws HL7Exception {
        return toMessage(RAR_RAR.class, body);
    }

    @Converter
    public static RAR_RAR toRarRar(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RAR_RAR.class, body, exchange);
    }

    @Converter
    public static RAS_O01 toRasO01(String body) throws HL7Exception {
        return toMessage(RAS_O01.class, body);
    }

    @Converter
    public static RAS_O01 toRasO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RAS_O01.class, body, exchange);
    }

    @Converter
    public static RCI_I05 toRciI05(String body) throws HL7Exception {
        return toMessage(RCI_I05.class, body);
    }

    @Converter
    public static RCI_I05 toRciI05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RCI_I05.class, body, exchange);
    }

    @Converter
    public static RCL_I06 toRclI06(String body) throws HL7Exception {
        return toMessage(RCL_I06.class, body);
    }

    @Converter
    public static RCL_I06 toRclI06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RCL_I06.class, body, exchange);
    }

    @Converter
    public static RDE_O01 toRdeO01(String body) throws HL7Exception {
        return toMessage(RDE_O01.class, body);
    }

    @Converter
    public static RDE_O01 toRdeO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDE_O01.class, body, exchange);
    }

    @Converter
    public static RDO_O01 toRdoO01(String body) throws HL7Exception {
        return toMessage(RDO_O01.class, body);
    }

    @Converter
    public static RDO_O01 toRdoO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDO_O01.class, body, exchange);
    }

    @Converter
    public static RDR_RDR toRdrRdr(String body) throws HL7Exception {
        return toMessage(RDR_RDR.class, body);
    }

    @Converter
    public static RDR_RDR toRdrRdr(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDR_RDR.class, body, exchange);
    }

    @Converter
    public static RDS_O01 toRdsO01(String body) throws HL7Exception {
        return toMessage(RDS_O01.class, body);
    }

    @Converter
    public static RDS_O01 toRdsO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDS_O01.class, body, exchange);
    }

    @Converter
    public static REF_I12 toRefI12(String body) throws HL7Exception {
        return toMessage(REF_I12.class, body);
    }

    @Converter
    public static REF_I12 toRefI12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(REF_I12.class, body, exchange);
    }

    @Converter
    public static RER_RER toRerRer(String body) throws HL7Exception {
        return toMessage(RER_RER.class, body);
    }

    @Converter
    public static RER_RER toRerRer(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RER_RER.class, body, exchange);
    }

    @Converter
    public static RGR_RGR toRgrRgr(String body) throws HL7Exception {
        return toMessage(RGR_RGR.class, body);
    }

    @Converter
    public static RGR_RGR toRgrRgr(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RGR_RGR.class, body, exchange);
    }

    @Converter
    public static RGV_O01 toRgvO01(String body) throws HL7Exception {
        return toMessage(RGV_O01.class, body);
    }

    @Converter
    public static RGV_O01 toRgvO01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RGV_O01.class, body, exchange);
    }

    @Converter
    public static ROR_R0R toRorR0r(String body) throws HL7Exception {
        return toMessage(ROR_R0R.class, body);
    }

    @Converter
    public static ROR_R0R toRorR0r(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ROR_R0R.class, body, exchange);
    }

    @Converter
    public static RPA_I08 toRpaI08(String body) throws HL7Exception {
        return toMessage(RPA_I08.class, body);
    }

    @Converter
    public static RPA_I08 toRpaI08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RPA_I08.class, body, exchange);
    }

    @Converter
    public static RPI_I01 toRpiI01(String body) throws HL7Exception {
        return toMessage(RPI_I01.class, body);
    }

    @Converter
    public static RPI_I01 toRpiI01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RPI_I01.class, body, exchange);
    }

    @Converter
    public static RPI_I04 toRpiI04(String body) throws HL7Exception {
        return toMessage(RPI_I04.class, body);
    }

    @Converter
    public static RPI_I04 toRpiI04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RPI_I04.class, body, exchange);
    }

    @Converter
    public static RPL_I02 toRplI02(String body) throws HL7Exception {
        return toMessage(RPL_I02.class, body);
    }

    @Converter
    public static RPL_I02 toRplI02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RPL_I02.class, body, exchange);
    }

    @Converter
    public static RPR_I03 toRprI03(String body) throws HL7Exception {
        return toMessage(RPR_I03.class, body);
    }

    @Converter
    public static RPR_I03 toRprI03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RPR_I03.class, body, exchange);
    }

    @Converter
    public static RQA_I08 toRqaI08(String body) throws HL7Exception {
        return toMessage(RQA_I08.class, body);
    }

    @Converter
    public static RQA_I08 toRqaI08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQA_I08.class, body, exchange);
    }

    @Converter
    public static RQC_I05 toRqcI05(String body) throws HL7Exception {
        return toMessage(RQC_I05.class, body);
    }

    @Converter
    public static RQC_I05 toRqcI05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQC_I05.class, body, exchange);
    }

    @Converter
    public static RQC_I06 toRqcI06(String body) throws HL7Exception {
        return toMessage(RQC_I06.class, body);
    }

    @Converter
    public static RQC_I06 toRqcI06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQC_I06.class, body, exchange);
    }

    @Converter
    public static RQI_I01 toRqiI01(String body) throws HL7Exception {
        return toMessage(RQI_I01.class, body);
    }

    @Converter
    public static RQI_I01 toRqiI01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQI_I01.class, body, exchange);
    }

    @Converter
    public static RQI_I02 toRqiI02(String body) throws HL7Exception {
        return toMessage(RQI_I02.class, body);
    }

    @Converter
    public static RQI_I02 toRqiI02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQI_I02.class, body, exchange);
    }

    @Converter
    public static RQI_I03 toRqiI03(String body) throws HL7Exception {
        return toMessage(RQI_I03.class, body);
    }

    @Converter
    public static RQI_I03 toRqiI03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQI_I03.class, body, exchange);
    }

    @Converter
    public static RQP_I04 toRqpI04(String body) throws HL7Exception {
        return toMessage(RQP_I04.class, body);
    }

    @Converter
    public static RQP_I04 toRqpI04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQP_I04.class, body, exchange);
    }

    @Converter
    public static RQQ_Q09 toRqqQ09(String body) throws HL7Exception {
        return toMessage(RQQ_Q09.class, body);
    }

    @Converter
    public static RQQ_Q09 toRqqQ09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQQ_Q09.class, body, exchange);
    }

    @Converter
    public static RRA_O02 toRraO02(String body) throws HL7Exception {
        return toMessage(RRA_O02.class, body);
    }

    @Converter
    public static RRA_O02 toRraO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRA_O02.class, body, exchange);
    }

    @Converter
    public static RRD_O02 toRrdO02(String body) throws HL7Exception {
        return toMessage(RRD_O02.class, body);
    }

    @Converter
    public static RRD_O02 toRrdO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRD_O02.class, body, exchange);
    }

    @Converter
    public static RRE_O02 toRreO02(String body) throws HL7Exception {
        return toMessage(RRE_O02.class, body);
    }

    @Converter
    public static RRE_O02 toRreO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRE_O02.class, body, exchange);
    }

    @Converter
    public static RRG_O02 toRrgO02(String body) throws HL7Exception {
        return toMessage(RRG_O02.class, body);
    }

    @Converter
    public static RRG_O02 toRrgO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRG_O02.class, body, exchange);
    }

    @Converter
    public static RRI_I12 toRriI12(String body) throws HL7Exception {
        return toMessage(RRI_I12.class, body);
    }

    @Converter
    public static RRI_I12 toRriI12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRI_I12.class, body, exchange);
    }

    @Converter
    public static RRO_O02 toRroO02(String body) throws HL7Exception {
        return toMessage(RRO_O02.class, body);
    }

    @Converter
    public static RRO_O02 toRroO02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRO_O02.class, body, exchange);
    }

    @Converter
    public static SIU_S12 toSiuS12(String body) throws HL7Exception {
        return toMessage(SIU_S12.class, body);
    }

    @Converter
    public static SIU_S12 toSiuS12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SIU_S12.class, body, exchange);
    }

    @Converter
    public static SPQ_Q08 toSpqQ08(String body) throws HL7Exception {
        return toMessage(SPQ_Q08.class, body);
    }

    @Converter
    public static SPQ_Q08 toSpqQ08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SPQ_Q08.class, body, exchange);
    }

    @Converter
    public static SQM_S25 toSqmS25(String body) throws HL7Exception {
        return toMessage(SQM_S25.class, body);
    }

    @Converter
    public static SQM_S25 toSqmS25(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SQM_S25.class, body, exchange);
    }

    @Converter
    public static SQR_S25 toSqrS25(String body) throws HL7Exception {
        return toMessage(SQR_S25.class, body);
    }

    @Converter
    public static SQR_S25 toSqrS25(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SQR_S25.class, body, exchange);
    }

    @Converter
    public static SRM_S01 toSrmS01(String body) throws HL7Exception {
        return toMessage(SRM_S01.class, body);
    }

    @Converter
    public static SRM_S01 toSrmS01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SRM_S01.class, body, exchange);
    }

    @Converter
    public static SRR_S01 toSrrS01(String body) throws HL7Exception {
        return toMessage(SRR_S01.class, body);
    }

    @Converter
    public static SRR_S01 toSrrS01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SRR_S01.class, body, exchange);
    }

    @Converter
    public static SUR_P09 toSurP09(String body) throws HL7Exception {
        return toMessage(SUR_P09.class, body);
    }

    @Converter
    public static SUR_P09 toSurP09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SUR_P09.class, body, exchange);
    }
    
    @Converter
    public static TBR_R08 toTbrR08(String body) throws HL7Exception {
        return toMessage(TBR_R08.class, body);
    }

    @Converter
    public static TBR_R08 toTbrR08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(TBR_R08.class, body, exchange);
    }

    @Converter
    public static UDM_Q05 toUdmQ05(String body) throws HL7Exception {
        return toMessage(UDM_Q05.class, body);
    }

    @Converter
    public static UDM_Q05 toUdmQ05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(UDM_Q05.class, body, exchange);
    }

    @Converter
    public static VQQ_Q07 toVqqQ07(String body) throws HL7Exception {
        return toMessage(VQQ_Q07.class, body);
    }

    @Converter
    public static VQQ_Q07 toVqqQ07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(VQQ_Q07.class, body, exchange);
    }

    @Converter
    public static VXQ_V01 toVxqV01(String body) throws HL7Exception {
        return toMessage(VXQ_V01.class, body);
    }

    @Converter
    public static VXQ_V01 toVxqV01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(VXQ_V01.class, body, exchange);
    }

    @Converter
    public static VXR_V03 toVxrV03(String body) throws HL7Exception {
        return toMessage(VXR_V03.class, body);
    }

    @Converter
    public static VXR_V03 toVxrV03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(VXR_V03.class, body, exchange);
    }

    @Converter
    public static VXU_V04 toVxuV04(String body) throws HL7Exception {
        return toMessage(VXU_V04.class, body);
    }

    @Converter
    public static VXU_V04 toVxuV04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(VXU_V04.class, body, exchange);
    }

    @Converter
    public static VXX_V02 toVxxV02(String body) throws HL7Exception {
        return toMessage(VXX_V02.class, body);
    }

    @Converter
    public static VXX_V02 toVxxV02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(VXX_V02.class, body, exchange);
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
