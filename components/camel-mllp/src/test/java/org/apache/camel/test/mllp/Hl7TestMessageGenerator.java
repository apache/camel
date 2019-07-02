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
package org.apache.camel.test.mllp;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class Hl7TestMessageGenerator {
    static SimpleDateFormat timestampFormat = new SimpleDateFormat("YYYYMMddHHmmss");
    static String messageControlIdFormat = "%05d";

    static String hl7MessageTemplate =
        "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|<MESSAGE_TIMESTAMP>||ORM^O01|<MESSAGE_CONTROL_ID>|D|2.3|||AL|NE|||" + '\r'
            + "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||" + '\r'
            + "NTE|1||Free text for entering clinical details|" + '\r'
            + "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|" + '\r'
            + "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||" + '\r'
            + "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
            + "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||" + '\r'
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
