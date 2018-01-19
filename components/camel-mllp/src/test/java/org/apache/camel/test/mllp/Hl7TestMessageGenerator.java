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

package org.apache.camel.test.mllp;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class Hl7TestMessageGenerator {
    static SimpleDateFormat timestampFormat = new SimpleDateFormat("YYYYMMddHHmmss");
    static String messageControlIdFormat = "%05d";

    static String hl7MessageTemplate =
        "MSH|^~\\&|ADT|EPIC|JCAPS|CC|<MESSAGE_TIMESTAMP>|RISTECH|ADT^A08|<MESSAGE_CONTROL_ID>|D|2.3^^|||||||" + '\r'
            + "EVN|A08|20150107161440||REG_UPDATE_SEND_VISIT_MESSAGES_ON_PATIENT_CHANGES|RISTECH^RADIOLOGY^TECHNOLOGIST^^^^^^UCLA^^^^^RRMC||" + '\r'
            + "PID|1|2100355^^^MRN^MRN|2100355^^^MRN^MRN||MDCLS9^MC9||19700109|F||U|111 HOVER STREET^^LOS ANGELES^CA^90032^USA^P^^LOS ANGELE|LOS ANGELE|(310)"
            + "725-6952^P^PH^^^310^7256952||ENGLISH|U||60000013647|565-33-2222|||U||||||||N||" + '\r'
            + "PD1|||UCLA HEALTH SYSTEM^^10|10002116^ADAMS^JOHN^D^^^^^EPIC^^^^PROVID||||||||||||||" + '\r'
            + "NK1|1|DOE^MC9^^|OTH|^^^^^USA|(310)888-9999^^^^^310^8889999|(310)999-2222^^^^^310^9992222|Emergency Contact 1|||||||||||||||||||||||||||" + '\r'
            + "PV1|1|OUTPATIENT|RR CT^^^1000^^^^^^^DEPID|EL|||017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID|017511^TOBIAS^JONATHAN^^^^^^EPIC^^^^PROVID||||||CLR|||||60000013647|SELF"
            + "|||||||||||||||||||||HOV_CONF|^^^1000^^^^^^^||20150107161438||||||||||" + '\r'
            + "PV2||||||||20150107161438||||CT BRAIN W WO CONTRAST||||||||||N|||||||||||||||||||||||||||" + '\r'
            + "ZPV||||||||||||20150107161438|||||||||" + '\r'
            + "AL1|1||33361^NO KNOWN ALLERGIES^^NOTCOMPUTRITION^NO KNOWN ALLERGIES^EXTELG||||||" + '\r'
            + "DG1|1|DX|784.0^Headache^DX|Headache||VISIT" + '\r'
            + "GT1|1|1000235129|MDCLS9^MC9^^||111 HOVER STREET^^LOS ANGELES^CA^90032^USA^^^LOS ANGELE|(310)"
            + "725-6952^^^^^310^7256952||19700109|F|P/F|SLF|565-33-2222|||||^^^^^USA|||UNKNOWN|||||||||||||||||||||||||||||" + '\r'
            + "UB2||||||||" + '\r'
            + '\n';

    private Hl7TestMessageGenerator() {
    }

    public static String generateMessage() {
        return generateMessage(new Date(), 1);
    }

    public static String generateMessage(int messageControlId) {
        return generateMessage(new Date(), messageControlId);
    }

    public static String generateMessage(Date timestamp, int messageControlId) {
        String tmpMessage = hl7MessageTemplate.replaceFirst("<MESSAGE_TIMESTAMP>", timestampFormat.format(timestamp));
        return tmpMessage.replaceFirst("<MESSAGE_CONTROL_ID>", String.format("%05d", messageControlId));
    }

    public static String getHl7MessageTemplate() {
        return hl7MessageTemplate;
    }

    public static void setHl7MessageTemplate(String hl7MessageTemplate) {
        Hl7TestMessageGenerator.hl7MessageTemplate = hl7MessageTemplate;
    }

    public static SimpleDateFormat getTimestampFormat() {
        return timestampFormat;
    }

    public static void setTimestampFormat(SimpleDateFormat timestampFormat) {
        Hl7TestMessageGenerator.timestampFormat = timestampFormat;
    }

    public static String getMessageControlIdFormat() {
        return messageControlIdFormat;
    }

    public static void setMessageControlIdFormat(String messageControlIdFormat) {
        Hl7TestMessageGenerator.messageControlIdFormat = messageControlIdFormat;
    }

}
