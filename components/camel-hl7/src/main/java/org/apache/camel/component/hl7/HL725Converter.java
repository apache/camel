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
import ca.uhn.hl7v2.model.v25.message.ACK;
import ca.uhn.hl7v2.model.v25.message.ADR_A19;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.message.ADT_A02;
import ca.uhn.hl7v2.model.v25.message.ADT_A03;
import ca.uhn.hl7v2.model.v25.message.ADT_A05;
import ca.uhn.hl7v2.model.v25.message.ADT_A06;
import ca.uhn.hl7v2.model.v25.message.ADT_A09;
import ca.uhn.hl7v2.model.v25.message.ADT_A12;
import ca.uhn.hl7v2.model.v25.message.ADT_A15;
import ca.uhn.hl7v2.model.v25.message.ADT_A16;
import ca.uhn.hl7v2.model.v25.message.ADT_A17;
import ca.uhn.hl7v2.model.v25.message.ADT_A18;
import ca.uhn.hl7v2.model.v25.message.ADT_A20;
import ca.uhn.hl7v2.model.v25.message.ADT_A21;
import ca.uhn.hl7v2.model.v25.message.ADT_A24;
import ca.uhn.hl7v2.model.v25.message.ADT_A30;
import ca.uhn.hl7v2.model.v25.message.ADT_A37;
import ca.uhn.hl7v2.model.v25.message.ADT_A38;
import ca.uhn.hl7v2.model.v25.message.ADT_A39;
import ca.uhn.hl7v2.model.v25.message.ADT_A43;
import ca.uhn.hl7v2.model.v25.message.ADT_A45;
import ca.uhn.hl7v2.model.v25.message.ADT_A50;
import ca.uhn.hl7v2.model.v25.message.ADT_A52;
import ca.uhn.hl7v2.model.v25.message.ADT_A54;
import ca.uhn.hl7v2.model.v25.message.ADT_A60;
import ca.uhn.hl7v2.model.v25.message.ADT_A61;
import ca.uhn.hl7v2.model.v25.message.ADT_AXX;
import ca.uhn.hl7v2.model.v25.message.BAR_P01;
import ca.uhn.hl7v2.model.v25.message.BAR_P02;
import ca.uhn.hl7v2.model.v25.message.BAR_P05;
import ca.uhn.hl7v2.model.v25.message.BAR_P06;
import ca.uhn.hl7v2.model.v25.message.BAR_P10;
import ca.uhn.hl7v2.model.v25.message.BAR_P12;
import ca.uhn.hl7v2.model.v25.message.BPS_O29;
import ca.uhn.hl7v2.model.v25.message.BRP_O30;
import ca.uhn.hl7v2.model.v25.message.BRT_O32;
import ca.uhn.hl7v2.model.v25.message.BTS_O31;
import ca.uhn.hl7v2.model.v25.message.CRM_C01;
import ca.uhn.hl7v2.model.v25.message.CSU_C09;
import ca.uhn.hl7v2.model.v25.message.DFT_P03;
import ca.uhn.hl7v2.model.v25.message.DFT_P11;
import ca.uhn.hl7v2.model.v25.message.DOC_T12;
import ca.uhn.hl7v2.model.v25.message.DSR_Q01;
import ca.uhn.hl7v2.model.v25.message.DSR_Q03;
import ca.uhn.hl7v2.model.v25.message.EAC_U07;
import ca.uhn.hl7v2.model.v25.message.EAN_U09;
import ca.uhn.hl7v2.model.v25.message.EAR_U08;
import ca.uhn.hl7v2.model.v25.message.EDR_R07;
import ca.uhn.hl7v2.model.v25.message.EQQ_Q04;
import ca.uhn.hl7v2.model.v25.message.ERP_R09;
import ca.uhn.hl7v2.model.v25.message.ESR_U02;
import ca.uhn.hl7v2.model.v25.message.ESU_U01;
import ca.uhn.hl7v2.model.v25.message.INR_U06;
import ca.uhn.hl7v2.model.v25.message.INU_U05;
import ca.uhn.hl7v2.model.v25.message.LSU_U12;
import ca.uhn.hl7v2.model.v25.message.MDM_T01;
import ca.uhn.hl7v2.model.v25.message.MDM_T02;
import ca.uhn.hl7v2.model.v25.message.MFK_M01;
import ca.uhn.hl7v2.model.v25.message.MFN_M01;
import ca.uhn.hl7v2.model.v25.message.MFN_M02;
import ca.uhn.hl7v2.model.v25.message.MFN_M03;
import ca.uhn.hl7v2.model.v25.message.MFN_M04;
import ca.uhn.hl7v2.model.v25.message.MFN_M05;
import ca.uhn.hl7v2.model.v25.message.MFN_M06;
import ca.uhn.hl7v2.model.v25.message.MFN_M07;
import ca.uhn.hl7v2.model.v25.message.MFN_M08;
import ca.uhn.hl7v2.model.v25.message.MFN_M09;
import ca.uhn.hl7v2.model.v25.message.MFN_M10;
import ca.uhn.hl7v2.model.v25.message.MFN_M11;
import ca.uhn.hl7v2.model.v25.message.MFN_M12;
import ca.uhn.hl7v2.model.v25.message.MFN_M13;
import ca.uhn.hl7v2.model.v25.message.MFN_M15;
import ca.uhn.hl7v2.model.v25.message.MFN_Znn;
import ca.uhn.hl7v2.model.v25.message.MFQ_M01;
import ca.uhn.hl7v2.model.v25.message.MFR_M01;
import ca.uhn.hl7v2.model.v25.message.MFR_M04;
import ca.uhn.hl7v2.model.v25.message.MFR_M05;
import ca.uhn.hl7v2.model.v25.message.MFR_M06;
import ca.uhn.hl7v2.model.v25.message.MFR_M07;
import ca.uhn.hl7v2.model.v25.message.NMD_N02;
import ca.uhn.hl7v2.model.v25.message.NMQ_N01;
import ca.uhn.hl7v2.model.v25.message.NMR_N01;
import ca.uhn.hl7v2.model.v25.message.OMB_O27;
import ca.uhn.hl7v2.model.v25.message.OMD_O03;
import ca.uhn.hl7v2.model.v25.message.OMG_O19;
import ca.uhn.hl7v2.model.v25.message.OMI_O23;
import ca.uhn.hl7v2.model.v25.message.OML_O21;
import ca.uhn.hl7v2.model.v25.message.OML_O33;
import ca.uhn.hl7v2.model.v25.message.OML_O35;
import ca.uhn.hl7v2.model.v25.message.OMN_O07;
import ca.uhn.hl7v2.model.v25.message.OMP_O09;
import ca.uhn.hl7v2.model.v25.message.OMS_O05;
import ca.uhn.hl7v2.model.v25.message.ORB_O28;
import ca.uhn.hl7v2.model.v25.message.ORD_O04;
import ca.uhn.hl7v2.model.v25.message.ORF_R04;
import ca.uhn.hl7v2.model.v25.message.ORG_O20;
import ca.uhn.hl7v2.model.v25.message.ORI_O24;
import ca.uhn.hl7v2.model.v25.message.ORL_O22;
import ca.uhn.hl7v2.model.v25.message.ORL_O34;
import ca.uhn.hl7v2.model.v25.message.ORL_O36;
import ca.uhn.hl7v2.model.v25.message.ORM_O01;
import ca.uhn.hl7v2.model.v25.message.ORN_O08;
import ca.uhn.hl7v2.model.v25.message.ORP_O10;
import ca.uhn.hl7v2.model.v25.message.ORR_O02;
import ca.uhn.hl7v2.model.v25.message.ORS_O06;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.model.v25.message.ORU_R30;
import ca.uhn.hl7v2.model.v25.message.OSQ_Q06;
import ca.uhn.hl7v2.model.v25.message.OSR_Q06;
import ca.uhn.hl7v2.model.v25.message.OUL_R21;
import ca.uhn.hl7v2.model.v25.message.OUL_R22;
import ca.uhn.hl7v2.model.v25.message.OUL_R23;
import ca.uhn.hl7v2.model.v25.message.OUL_R24;
import ca.uhn.hl7v2.model.v25.message.PEX_P07;
import ca.uhn.hl7v2.model.v25.message.PGL_PC6;
import ca.uhn.hl7v2.model.v25.message.PMU_B01;
import ca.uhn.hl7v2.model.v25.message.PMU_B03;
import ca.uhn.hl7v2.model.v25.message.PMU_B04;
import ca.uhn.hl7v2.model.v25.message.PMU_B07;
import ca.uhn.hl7v2.model.v25.message.PMU_B08;
import ca.uhn.hl7v2.model.v25.message.PPG_PCG;
import ca.uhn.hl7v2.model.v25.message.PPP_PCB;
import ca.uhn.hl7v2.model.v25.message.PPR_PC1;
import ca.uhn.hl7v2.model.v25.message.PPT_PCL;
import ca.uhn.hl7v2.model.v25.message.PPV_PCA;
import ca.uhn.hl7v2.model.v25.message.PRR_PC5;
import ca.uhn.hl7v2.model.v25.message.PTR_PCF;
import ca.uhn.hl7v2.model.v25.message.QBP_K13;
import ca.uhn.hl7v2.model.v25.message.QBP_Q11;
import ca.uhn.hl7v2.model.v25.message.QBP_Q13;
import ca.uhn.hl7v2.model.v25.message.QBP_Q15;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.model.v25.message.QBP_Qnn;
import ca.uhn.hl7v2.model.v25.message.QBP_Z73;
import ca.uhn.hl7v2.model.v25.message.QCK_Q02;
import ca.uhn.hl7v2.model.v25.message.QCN_J01;
import ca.uhn.hl7v2.model.v25.message.QRY;
import ca.uhn.hl7v2.model.v25.message.QRY_A19;
import ca.uhn.hl7v2.model.v25.message.QRY_PC4;
import ca.uhn.hl7v2.model.v25.message.QRY_Q01;
import ca.uhn.hl7v2.model.v25.message.QRY_Q02;
import ca.uhn.hl7v2.model.v25.message.QRY_R02;
import ca.uhn.hl7v2.model.v25.message.QSB_Q16;
import ca.uhn.hl7v2.model.v25.message.QVR_Q17;
import ca.uhn.hl7v2.model.v25.message.RAR_RAR;
import ca.uhn.hl7v2.model.v25.message.RAS_O17;
import ca.uhn.hl7v2.model.v25.message.RCI_I05;
import ca.uhn.hl7v2.model.v25.message.RCL_I06;
import ca.uhn.hl7v2.model.v25.message.RDE_O11;
import ca.uhn.hl7v2.model.v25.message.RDR_RDR;
import ca.uhn.hl7v2.model.v25.message.RDS_O13;
import ca.uhn.hl7v2.model.v25.message.RDY_K15;
import ca.uhn.hl7v2.model.v25.message.REF_I12;
import ca.uhn.hl7v2.model.v25.message.RER_RER;
import ca.uhn.hl7v2.model.v25.message.RGR_RGR;
import ca.uhn.hl7v2.model.v25.message.RGV_O15;
import ca.uhn.hl7v2.model.v25.message.ROR_ROR;
import ca.uhn.hl7v2.model.v25.message.RPA_I08;
import ca.uhn.hl7v2.model.v25.message.RPI_I01;
import ca.uhn.hl7v2.model.v25.message.RPI_I04;
import ca.uhn.hl7v2.model.v25.message.RPL_I02;
import ca.uhn.hl7v2.model.v25.message.RPR_I03;
import ca.uhn.hl7v2.model.v25.message.RQA_I08;
import ca.uhn.hl7v2.model.v25.message.RQC_I05;
import ca.uhn.hl7v2.model.v25.message.RQI_I01;
import ca.uhn.hl7v2.model.v25.message.RQP_I04;
import ca.uhn.hl7v2.model.v25.message.RQQ_Q09;
import ca.uhn.hl7v2.model.v25.message.RRA_O18;
import ca.uhn.hl7v2.model.v25.message.RRD_O14;
import ca.uhn.hl7v2.model.v25.message.RRE_O12;
import ca.uhn.hl7v2.model.v25.message.RRG_O16;
import ca.uhn.hl7v2.model.v25.message.RRI_I12;
import ca.uhn.hl7v2.model.v25.message.RSP_K11;
import ca.uhn.hl7v2.model.v25.message.RSP_K21;
import ca.uhn.hl7v2.model.v25.message.RSP_K23;
import ca.uhn.hl7v2.model.v25.message.RSP_K25;
import ca.uhn.hl7v2.model.v25.message.RSP_K31;
import ca.uhn.hl7v2.model.v25.message.RSP_Q11;
import ca.uhn.hl7v2.model.v25.message.RSP_Z82;
import ca.uhn.hl7v2.model.v25.message.RSP_Z86;
import ca.uhn.hl7v2.model.v25.message.RSP_Z88;
import ca.uhn.hl7v2.model.v25.message.RSP_Z90;
import ca.uhn.hl7v2.model.v25.message.RTB_K13;
import ca.uhn.hl7v2.model.v25.message.RTB_Knn;
import ca.uhn.hl7v2.model.v25.message.RTB_Z74;
import ca.uhn.hl7v2.model.v25.message.SIU_S12;
import ca.uhn.hl7v2.model.v25.message.SPQ_Q08;
import ca.uhn.hl7v2.model.v25.message.SQM_S25;
import ca.uhn.hl7v2.model.v25.message.SQR_S25;
import ca.uhn.hl7v2.model.v25.message.SRM_S01;
import ca.uhn.hl7v2.model.v25.message.SRR_S01;
import ca.uhn.hl7v2.model.v25.message.SSR_U04;
import ca.uhn.hl7v2.model.v25.message.SSU_U03;
import ca.uhn.hl7v2.model.v25.message.SUR_P09;
import ca.uhn.hl7v2.model.v25.message.TBR_R08;
import ca.uhn.hl7v2.model.v25.message.TCU_U10;
import ca.uhn.hl7v2.model.v25.message.UDM_Q05;
import ca.uhn.hl7v2.model.v25.message.VQQ_Q07;
import ca.uhn.hl7v2.model.v25.message.VXQ_V01;
import ca.uhn.hl7v2.model.v25.message.VXR_V03;
import ca.uhn.hl7v2.model.v25.message.VXU_V04;
import ca.uhn.hl7v2.model.v25.message.VXX_V02;
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
public final class HL725Converter {

    private static final HapiContext DEFAULT_CONTEXT;

    static {
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setDefaultObx2Type("ST");
        parserConfiguration.setInvalidObx2Type("ST");
        parserConfiguration.setUnexpectedSegmentBehaviour(UnexpectedSegmentBehaviourEnum.ADD_INLINE);

        DEFAULT_CONTEXT = new DefaultHapiContext(parserConfiguration, ValidationContextFactory.noValidation(), new DefaultModelClassFactory());
    }

    private HL725Converter() {
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
    public static ADT_A09 toAdtA09(String body) throws HL7Exception {
        return toMessage(ADT_A09.class, body);
    }

    @Converter
    public static ADT_A09 toAdtA09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A09.class, body, exchange);
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
    public static ADT_A24 toAdtA24(String body) throws HL7Exception {
        return toMessage(ADT_A24.class, body);
    }

    @Converter
    public static ADT_A24 toAdtA24(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A24.class, body, exchange);
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
    public static ADT_A43 toAdtA43(String body) throws HL7Exception {
        return toMessage(ADT_A43.class, body);
    }

    @Converter
    public static ADT_A43 toAdtA43(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A43.class, body, exchange);
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
    public static ADT_A50 toAdtA50(String body) throws HL7Exception {
        return toMessage(ADT_A50.class, body);
    }

    @Converter
    public static ADT_A50 toAdtA50(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A50.class, body, exchange);
    }

    @Converter
    public static ADT_A52 toAdtA52(String body) throws HL7Exception {
        return toMessage(ADT_A52.class, body);
    }

    @Converter
    public static ADT_A52 toAdtA52(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A52.class, body, exchange);
    }

    @Converter
    public static ADT_A54 toAdtA54(String body) throws HL7Exception {
        return toMessage(ADT_A54.class, body);
    }

    @Converter
    public static ADT_A54 toAdtA54(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A54.class, body, exchange);
    }

    @Converter
    public static ADT_A60 toAdtA60(String body) throws HL7Exception {
        return toMessage(ADT_A60.class, body);
    }

    @Converter
    public static ADT_A60 toAdtA60(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A60.class, body, exchange);
    }

    @Converter
    public static ADT_A61 toAdtA61(String body) throws HL7Exception {
        return toMessage(ADT_A61.class, body);
    }

    @Converter
    public static ADT_A61 toAdtA61(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ADT_A61.class, body, exchange);
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
    public static BAR_P10 toBarP10(String body) throws HL7Exception {
        return toMessage(BAR_P10.class, body);
    }

    @Converter
    public static BAR_P10 toBarP10(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BAR_P10.class, body, exchange);
    }

    @Converter
    public static BAR_P12 toBarP12(String body) throws HL7Exception {
        return toMessage(BAR_P12.class, body);
    }

    @Converter
    public static BAR_P12 toBarP12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BAR_P12.class, body, exchange);
    }

    @Converter
    public static BPS_O29 toBpsO29(String body) throws HL7Exception {
        return toMessage(BPS_O29.class, body);
    }

    @Converter
    public static BPS_O29 toBpsO29(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BPS_O29.class, body, exchange);
    }

    @Converter
    public static BRP_O30 toBrpO30(String body) throws HL7Exception {
        return toMessage(BRP_O30.class, body);
    }

    @Converter
    public static BRP_O30 toBrpO30(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BRP_O30.class, body, exchange);
    }

    @Converter
    public static BRT_O32 toBrtO32(String body) throws HL7Exception {
        return toMessage(BRT_O32.class, body);
    }

    @Converter
    public static BRT_O32 toBrtO32(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BRT_O32.class, body, exchange);
    }

    @Converter
    public static BTS_O31 toBtsO31(String body) throws HL7Exception {
        return toMessage(BTS_O31.class, body);
    }

    @Converter
    public static BTS_O31 toBtsO31(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(BTS_O31.class, body, exchange);
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
    public static DFT_P11 toDftP11(String body) throws HL7Exception {
        return toMessage(DFT_P11.class, body);
    }

    @Converter
    public static DFT_P11 toDftP11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(DFT_P11.class, body, exchange);
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
    public static EAC_U07 toEacU07(String body) throws HL7Exception {
        return toMessage(EAC_U07.class, body);
    }

    @Converter
    public static EAC_U07 toEacU07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(EAC_U07.class, body, exchange);
    }

    @Converter
    public static EAN_U09 toEanU09(String body) throws HL7Exception {
        return toMessage(EAN_U09.class, body);
    }

    @Converter
    public static EAN_U09 toEanU09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(EAN_U09.class, body, exchange);
    }

    @Converter
    public static EAR_U08 toEarU08(String body) throws HL7Exception {
        return toMessage(EAR_U08.class, body);
    }

    @Converter
    public static EAR_U08 toEarU08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(EAR_U08.class, body, exchange);
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
    public static ESR_U02 toEsrU02(String body) throws HL7Exception {
        return toMessage(ESR_U02.class, body);
    }

    @Converter
    public static ESR_U02 toEsrU02(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ESR_U02.class, body, exchange);
    }

    @Converter
    public static ESU_U01 toEsuU01(String body) throws HL7Exception {
        return toMessage(ESU_U01.class, body);
    }

    @Converter
    public static ESU_U01 toEsuU01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ESU_U01.class, body, exchange);
    }

    @Converter
    public static INR_U06 toInrU06(String body) throws HL7Exception {
        return toMessage(INR_U06.class, body);
    }

    @Converter
    public static INR_U06 toInrU06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(INR_U06.class, body, exchange);
    }

    @Converter
    public static INU_U05 toInuU05(String body) throws HL7Exception {
        return toMessage(INU_U05.class, body);
    }

    @Converter
    public static INU_U05 toInuU05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(INU_U05.class, body, exchange);
    }

    @Converter
    public static LSU_U12 toLsuU12(String body) throws HL7Exception {
        return toMessage(LSU_U12.class, body);
    }

    @Converter
    public static LSU_U12 toLsuU12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(LSU_U12.class, body, exchange);
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
    public static MFK_M01 toMfkM01(String body) throws HL7Exception {
        return toMessage(MFK_M01.class, body);
    }

    @Converter
    public static MFK_M01 toMfkM01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFK_M01.class, body, exchange);
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
    public static MFN_M07 toMfnM07(String body) throws HL7Exception {
        return toMessage(MFN_M07.class, body);
    }

    @Converter
    public static MFN_M07 toMfnM07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M07.class, body, exchange);
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
    public static MFN_M12 toMfnM12(String body) throws HL7Exception {
        return toMessage(MFN_M12.class, body);
    }

    @Converter
    public static MFN_M12 toMfnM12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M12.class, body, exchange);
    }

    @Converter
    public static MFN_M13 toMfnM13(String body) throws HL7Exception {
        return toMessage(MFN_M13.class, body);
    }

    @Converter
    public static MFN_M13 toMfnM13(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M13.class, body, exchange);
    }

    @Converter
    public static MFN_M15 toMfnM15(String body) throws HL7Exception {
        return toMessage(MFN_M15.class, body);
    }

    @Converter
    public static MFN_M15 toMfnM15(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_M15.class, body, exchange);
    }

    @Converter
    public static MFN_Znn toMfnZnn(String body) throws HL7Exception {
        return toMessage(MFN_Znn.class, body);
    }

    @Converter
    public static MFN_Znn toMfnZnn(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFN_Znn.class, body, exchange);
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
    public static MFR_M04 toMfrM04(String body) throws HL7Exception {
        return toMessage(MFR_M04.class, body);
    }

    @Converter
    public static MFR_M04 toMfrM04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFR_M04.class, body, exchange);
    }

    @Converter
    public static MFR_M05 toMfrM05(String body) throws HL7Exception {
        return toMessage(MFR_M05.class, body);
    }

    @Converter
    public static MFR_M05 toMfrM05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFR_M05.class, body, exchange);
    }

    @Converter
    public static MFR_M06 toMfrM06(String body) throws HL7Exception {
        return toMessage(MFR_M06.class, body);
    }

    @Converter
    public static MFR_M06 toMfrM06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFR_M06.class, body, exchange);
    }

    @Converter
    public static MFR_M07 toMfrM07(String body) throws HL7Exception {
        return toMessage(MFR_M07.class, body);
    }

    @Converter
    public static MFR_M07 toMfrM07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(MFR_M07.class, body, exchange);
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
    public static OMB_O27 toOmbO27(String body) throws HL7Exception {
        return toMessage(OMB_O27.class, body);
    }

    @Converter
    public static OMB_O27 toOmbO27(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMB_O27.class, body, exchange);
    }

    @Converter
    public static OMD_O03 toOmdO03(String body) throws HL7Exception {
        return toMessage(OMD_O03.class, body);
    }

    @Converter
    public static OMD_O03 toOmdO03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMD_O03.class, body, exchange);
    }

    @Converter
    public static OMG_O19 toOmgO19(String body) throws HL7Exception {
        return toMessage(OMG_O19.class, body);
    }

    @Converter
    public static OMG_O19 toOmgO19(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMG_O19.class, body, exchange);
    }

    @Converter
    public static OMI_O23 toOmiO23(String body) throws HL7Exception {
        return toMessage(OMI_O23.class, body);
    }

    @Converter
    public static OMI_O23 toOmiO23(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMI_O23.class, body, exchange);
    }

    @Converter
    public static OML_O21 toOmlO21(String body) throws HL7Exception {
        return toMessage(OML_O21.class, body);
    }

    @Converter
    public static OML_O21 toOmlO21(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OML_O21.class, body, exchange);
    }

    @Converter
    public static OML_O33 toOmlO33(String body) throws HL7Exception {
        return toMessage(OML_O33.class, body);
    }

    @Converter
    public static OML_O33 toOmlO33(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OML_O33.class, body, exchange);
    }

    @Converter
    public static OML_O35 toOmlO35(String body) throws HL7Exception {
        return toMessage(OML_O35.class, body);
    }

    @Converter
    public static OML_O35 toOmlO35(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OML_O35.class, body, exchange);
    }

    @Converter
    public static OMN_O07 toOmnO07(String body) throws HL7Exception {
        return toMessage(OMN_O07.class, body);
    }

    @Converter
    public static OMN_O07 toOmnO07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMN_O07.class, body, exchange);
    }

    @Converter
    public static OMP_O09 toOmpO09(String body) throws HL7Exception {
        return toMessage(OMP_O09.class, body);
    }

    @Converter
    public static OMP_O09 toOmpO09(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMP_O09.class, body, exchange);
    }

    @Converter
    public static OMS_O05 toOmsO05(String body) throws HL7Exception {
        return toMessage(OMS_O05.class, body);
    }

    @Converter
    public static OMS_O05 toOmsO05(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OMS_O05.class, body, exchange);
    }

    @Converter
    public static ORB_O28 toOrbO28(String body) throws HL7Exception {
        return toMessage(ORB_O28.class, body);
    }

    @Converter
    public static ORB_O28 toOrbO28(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORB_O28.class, body, exchange);
    }

    @Converter
    public static ORD_O04 toOrdO04(String body) throws HL7Exception {
        return toMessage(ORD_O04.class, body);
    }

    @Converter
    public static ORD_O04 toOrdO04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORD_O04.class, body, exchange);
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
    public static ORG_O20 toOrgO20(String body) throws HL7Exception {
        return toMessage(ORG_O20.class, body);
    }

    @Converter
    public static ORG_O20 toOrgO20(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORG_O20.class, body, exchange);
    }

    @Converter
    public static ORI_O24 toOriO24(String body) throws HL7Exception {
        return toMessage(ORI_O24.class, body);
    }

    @Converter
    public static ORI_O24 toOriO24(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORI_O24.class, body, exchange);
    }

    @Converter
    public static ORL_O22 toOrlO22(String body) throws HL7Exception {
        return toMessage(ORL_O22.class, body);
    }

    @Converter
    public static ORL_O22 toOrlO22(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORL_O22.class, body, exchange);
    }

    @Converter
    public static ORL_O34 toOrlO34(String body) throws HL7Exception {
        return toMessage(ORL_O34.class, body);
    }

    @Converter
    public static ORL_O34 toOrlO34(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORL_O34.class, body, exchange);
    }

    @Converter
    public static ORL_O36 toOrlO36(String body) throws HL7Exception {
        return toMessage(ORL_O36.class, body);
    }

    @Converter
    public static ORL_O36 toOrlO36(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORL_O36.class, body, exchange);
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
    public static ORN_O08 toOrnO08(String body) throws HL7Exception {
        return toMessage(ORN_O08.class, body);
    }

    @Converter
    public static ORN_O08 toOrnO08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORN_O08.class, body, exchange);
    }

    @Converter
    public static ORP_O10 toOrpO10(String body) throws HL7Exception {
        return toMessage(ORP_O10.class, body);
    }

    @Converter
    public static ORP_O10 toOrpO10(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORP_O10.class, body, exchange);
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
    public static ORS_O06 toOrsO06(String body) throws HL7Exception {
        return toMessage(ORS_O06.class, body);
    }

    @Converter
    public static ORS_O06 toOrsO06(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORS_O06.class, body, exchange);
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
    public static ORU_R30 toOruR30(String body) throws HL7Exception {
        return toMessage(ORU_R30.class, body);
    }

    @Converter
    public static ORU_R30 toOruR30(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ORU_R30.class, body, exchange);
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
    public static OUL_R21 toOulR21(String body) throws HL7Exception {
        return toMessage(OUL_R21.class, body);
    }

    @Converter
    public static OUL_R21 toOulR21(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OUL_R21.class, body, exchange);
    }

    @Converter
    public static OUL_R22 toOulR22(String body) throws HL7Exception {
        return toMessage(OUL_R22.class, body);
    }

    @Converter
    public static OUL_R22 toOulR22(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OUL_R22.class, body, exchange);
    }

    @Converter
    public static OUL_R23 toOulR23(String body) throws HL7Exception {
        return toMessage(OUL_R23.class, body);
    }

    @Converter
    public static OUL_R23 toOulR23(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OUL_R23.class, body, exchange);
    }

    @Converter
    public static OUL_R24 toOulR24(String body) throws HL7Exception {
        return toMessage(OUL_R24.class, body);
    }

    @Converter
    public static OUL_R24 toOulR24(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(OUL_R24.class, body, exchange);
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
    public static PMU_B01 toPmuB01(String body) throws HL7Exception {
        return toMessage(PMU_B01.class, body);
    }

    @Converter
    public static PMU_B01 toPmuB01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PMU_B01.class, body, exchange);
    }

    @Converter
    public static PMU_B03 toPmuB03(String body) throws HL7Exception {
        return toMessage(PMU_B03.class, body);
    }

    @Converter
    public static PMU_B03 toPmuB03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PMU_B03.class, body, exchange);
    }

    @Converter
    public static PMU_B04 toPmuB04(String body) throws HL7Exception {
        return toMessage(PMU_B04.class, body);
    }

    @Converter
    public static PMU_B04 toPmuB04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PMU_B04.class, body, exchange);
    }

    @Converter
    public static PMU_B07 toPmuB07(String body) throws HL7Exception {
        return toMessage(PMU_B07.class, body);
    }

    @Converter
    public static PMU_B07 toPmuB07(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PMU_B07.class, body, exchange);
    }

    @Converter
    public static PMU_B08 toPmuB08(String body) throws HL7Exception {
        return toMessage(PMU_B08.class, body);
    }

    @Converter
    public static PMU_B08 toPmuB08(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(PMU_B08.class, body, exchange);
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
    public static QBP_K13 toQbpK13(String body) throws HL7Exception {
        return toMessage(QBP_K13.class, body);
    }

    @Converter
    public static QBP_K13 toQbpK13(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_K13.class, body, exchange);
    }

    @Converter
    public static QBP_Q11 toQbpQ11(String body) throws HL7Exception {
        return toMessage(QBP_Q11.class, body);
    }

    @Converter
    public static QBP_Q11 toQbpQ11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_Q11.class, body, exchange);
    }

    @Converter
    public static QBP_Q13 toQbpQ13(String body) throws HL7Exception {
        return toMessage(QBP_Q13.class, body);
    }

    @Converter
    public static QBP_Q13 toQbpQ13(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_Q13.class, body, exchange);
    }

    @Converter
    public static QBP_Q15 toQbpQ15(String body) throws HL7Exception {
        return toMessage(QBP_Q15.class, body);
    }

    @Converter
    public static QBP_Q15 toQbpQ15(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_Q15.class, body, exchange);
    }

    @Converter
    public static QBP_Q21 toQbpQ21(String body) throws HL7Exception {
        return toMessage(QBP_Q21.class, body);
    }

    @Converter
    public static QBP_Q21 toQbpQ21(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_Q21.class, body, exchange);
    }

    @Converter
    public static QBP_Qnn toQbpQnn(String body) throws HL7Exception {
        return toMessage(QBP_Qnn.class, body);
    }

    @Converter
    public static QBP_Qnn toQbpQnn(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_Qnn.class, body, exchange);
    }

    @Converter
    public static QBP_Z73 toQbpZ73(String body) throws HL7Exception {
        return toMessage(QBP_Z73.class, body);
    }

    @Converter
    public static QBP_Z73 toQbpZ73(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QBP_Z73.class, body, exchange);
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
    public static QCN_J01 toQcnJ01(String body) throws HL7Exception {
        return toMessage(QCN_J01.class, body);
    }

    @Converter
    public static QCN_J01 toQcnJ01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QCN_J01.class, body, exchange);
    }

    @Converter
    public static QRY toQry(String body) throws HL7Exception {
        return toMessage(QRY.class, body);
    }

    @Converter
    public static QRY toQry(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QRY.class, body, exchange);
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
    public static QSB_Q16 toQsbQ16(String body) throws HL7Exception {
        return toMessage(QSB_Q16.class, body);
    }

    @Converter
    public static QSB_Q16 toQsbQ16(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QSB_Q16.class, body, exchange);
    }

    @Converter
    public static QVR_Q17 toQvrQ17(String body) throws HL7Exception {
        return toMessage(QVR_Q17.class, body);
    }

    @Converter
    public static QVR_Q17 toQvrQ17(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(QVR_Q17.class, body, exchange);
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
    public static RAS_O17 toRasO17(String body) throws HL7Exception {
        return toMessage(RAS_O17.class, body);
    }

    @Converter
    public static RAS_O17 toRasO17(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RAS_O17.class, body, exchange);
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
    public static RDE_O11 toRdeO11(String body) throws HL7Exception {
        return toMessage(RDE_O11.class, body);
    }

    @Converter
    public static RDE_O11 toRdeO11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDE_O11.class, body, exchange);
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
    public static RDS_O13 toRdsO13(String body) throws HL7Exception {
        return toMessage(RDS_O13.class, body);
    }

    @Converter
    public static RDS_O13 toRdsO13(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDS_O13.class, body, exchange);
    }

    @Converter
    public static RDY_K15 toRdyK15(String body) throws HL7Exception {
        return toMessage(RDY_K15.class, body);
    }

    @Converter
    public static RDY_K15 toRdyK15(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RDY_K15.class, body, exchange);
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
    public static RGV_O15 toRgvO15(String body) throws HL7Exception {
        return toMessage(RGV_O15.class, body);
    }

    @Converter
    public static RGV_O15 toRgvO15(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RGV_O15.class, body, exchange);
    }

    @Converter
    public static ROR_ROR toRorRor(String body) throws HL7Exception {
        return toMessage(ROR_ROR.class, body);
    }

    @Converter
    public static ROR_ROR toRorRor(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(ROR_ROR.class, body, exchange);
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
    public static RQI_I01 toRqiI01(String body) throws HL7Exception {
        return toMessage(RQI_I01.class, body);
    }

    @Converter
    public static RQI_I01 toRqiI01(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RQI_I01.class, body, exchange);
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
    public static RRA_O18 toRraO18(String body) throws HL7Exception {
        return toMessage(RRA_O18.class, body);
    }

    @Converter
    public static RRA_O18 toRraO18(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRA_O18.class, body, exchange);
    }

    @Converter
    public static RRD_O14 toRrdO14(String body) throws HL7Exception {
        return toMessage(RRD_O14.class, body);
    }

    @Converter
    public static RRD_O14 toRrdO14(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRD_O14.class, body, exchange);
    }

    @Converter
    public static RRE_O12 toRreO12(String body) throws HL7Exception {
        return toMessage(RRE_O12.class, body);
    }

    @Converter
    public static RRE_O12 toRreO12(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRE_O12.class, body, exchange);
    }

    @Converter
    public static RRG_O16 toRrgO16(String body) throws HL7Exception {
        return toMessage(RRG_O16.class, body);
    }

    @Converter
    public static RRG_O16 toRrgO16(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RRG_O16.class, body, exchange);
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
    public static RSP_K11 toRspK11(String body) throws HL7Exception {
        return toMessage(RSP_K11.class, body);
    }

    @Converter
    public static RSP_K11 toRspK11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_K11.class, body, exchange);
    }

    @Converter
    public static RSP_K21 toRspK21(String body) throws HL7Exception {
        return toMessage(RSP_K21.class, body);
    }

    @Converter
    public static RSP_K21 toRspK21(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_K21.class, body, exchange);
    }

    @Converter
    public static RSP_K23 toRspK23(String body) throws HL7Exception {
        return toMessage(RSP_K23.class, body);
    }

    @Converter
    public static RSP_K23 toRspK23(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_K23.class, body, exchange);
    }

    @Converter
    public static RSP_K25 toRspK25(String body) throws HL7Exception {
        return toMessage(RSP_K25.class, body);
    }

    @Converter
    public static RSP_K25 toRspK25(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_K25.class, body, exchange);
    }

    @Converter
    public static RSP_K31 toRspK31(String body) throws HL7Exception {
        return toMessage(RSP_K31.class, body);
    }

    @Converter
    public static RSP_K31 toRspK31(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_K31.class, body, exchange);
    }

    @Converter
    public static RSP_Q11 toRspQ11(String body) throws HL7Exception {
        return toMessage(RSP_Q11.class, body);
    }

    @Converter
    public static RSP_Q11 toRspQ11(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_Q11.class, body, exchange);
    }

    @Converter
    public static RSP_Z82 toRspZ82(String body) throws HL7Exception {
        return toMessage(RSP_Z82.class, body);
    }

    @Converter
    public static RSP_Z82 toRspZ82(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_Z82.class, body, exchange);
    }

    @Converter
    public static RSP_Z86 toRspZ86(String body) throws HL7Exception {
        return toMessage(RSP_Z86.class, body);
    }

    @Converter
    public static RSP_Z86 toRspZ86(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_Z86.class, body, exchange);
    }

    @Converter
    public static RSP_Z88 toRspZ88(String body) throws HL7Exception {
        return toMessage(RSP_Z88.class, body);
    }

    @Converter
    public static RSP_Z88 toRspZ88(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_Z88.class, body, exchange);
    }

    @Converter
    public static RSP_Z90 toRspZ90(String body) throws HL7Exception {
        return toMessage(RSP_Z90.class, body);
    }

    @Converter
    public static RSP_Z90 toRspZ90(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RSP_Z90.class, body, exchange);
    }

    @Converter
    public static RTB_K13 toRtbK13(String body) throws HL7Exception {
        return toMessage(RTB_K13.class, body);
    }

    @Converter
    public static RTB_K13 toRtbK13(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RTB_K13.class, body, exchange);
    }

    @Converter
    public static RTB_Knn toRtbKnn(String body) throws HL7Exception {
        return toMessage(RTB_Knn.class, body);
    }

    @Converter
    public static RTB_Knn toRtbKnn(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RTB_Knn.class, body, exchange);
    }

    @Converter
    public static RTB_Z74 toRtbZ74(String body) throws HL7Exception {
        return toMessage(RTB_Z74.class, body);
    }

    @Converter
    public static RTB_Z74 toRtbZ74(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(RTB_Z74.class, body, exchange);
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
    public static SSR_U04 toSsrU04(String body) throws HL7Exception {
        return toMessage(SSR_U04.class, body);
    }

    @Converter
    public static SSR_U04 toSsrU04(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SSR_U04.class, body, exchange);
    }

    @Converter
    public static SSU_U03 toSsuU03(String body) throws HL7Exception {
        return toMessage(SSU_U03.class, body);
    }

    @Converter
    public static SSU_U03 toSsuU03(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(SSU_U03.class, body, exchange);
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
    public static TCU_U10 toTcuU10(String body) throws HL7Exception {
        return toMessage(TCU_U10.class, body);
    }

    @Converter
    public static TCU_U10 toTcuU10(byte[] body, Exchange exchange) throws HL7Exception, IOException {
        return toMessage(TCU_U10.class, body, exchange);
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
