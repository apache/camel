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
package org.apache.camel.component.snmp;

import java.util.StringTokenizer;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.util.StringHelper;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;

@Converter(generateLoader = true)
public final class SnmpConverters {
    public static final String SNMP_TAG = "snmp";
    public static final String ENTRY_TAG = "entry";
    public static final String OID_TAG = "oid";
    public static final String VALUE_TAG = "value";

    private static final String SNMP_TAG_OPEN = '<' + SNMP_TAG + '>';
    private static final String SNMP_TAG_CLOSE = "</" + SNMP_TAG + '>';
    private static final String ENTRY_TAG_OPEN = '<' + ENTRY_TAG + '>';
    private static final String ENTRY_TAG_CLOSE = "</" + ENTRY_TAG + '>';
    private static final String OID_TAG_OPEN = '<' + OID_TAG + '>';
    private static final String OID_TAG_CLOSE = "</" + OID_TAG + '>';
    private static final String VALUE_TAG_OPEN = '<' + VALUE_TAG + '>';
    private static final String VALUE_TAG_CLOSE = "</" + VALUE_TAG + '>';

    private SnmpConverters() {
        //Utility Class
    }

    @Converter
    // Camel could use this method to convert the String into a List
    public static OIDList toOIDList(String s, Exchange exchange) {
        try {
            OIDList list = new OIDList();

            if (s != null && s.contains(",")) {
                // seems to be a comma separated oid list
                StringTokenizer strTok = new StringTokenizer(s, ",");
                while (strTok.hasMoreTokens()) {
                    String tok = strTok.nextToken();
                    if (tok != null && !tok.isBlank()) {
                        list.add(new OID(tok.trim()));
                    } else {
                        // empty token - skip
                    }
                }
            } else if (s != null) {
                // maybe a single oid
                list.add(new OID(s.trim()));
            }

            return list;
        } catch (Exception e) {
            // return null if we can't convert without an error
            // and it could let camel to choice the other converter to do the job
            // new OID(...) will throw NumberFormatException if it's not a valid OID
            return null;
        }
    }

    private static void entryAppend(StringBuilder sb, String tag, String value) {
        sb.append(ENTRY_TAG_OPEN);
        sb.append('<').append(tag).append('>');
        sb.append(value);
        sb.append("</").append(tag).append('>');
        sb.append(ENTRY_TAG_CLOSE);
    }

    /**
     * Converts the given snmp pdu to a String body.
     *
     * @param  pdu the snmp pdu
     * @return     the text content
     */
    @Converter
    public static String toString(PDU pdu) {
        // the output buffer
        StringBuilder sb = new StringBuilder();

        // prepare the header
        if (pdu.getType() == PDU.V1TRAP) {
            sb.append("<" + SNMP_TAG + " messageType=\"v1\">");
        } else {
            sb.append(SNMP_TAG_OPEN);
        }

        // Extract SNMPv1 specific variables
        if (pdu.getType() == PDU.V1TRAP) {
            PDUv1 v1pdu = (PDUv1) pdu;
            entryAppend(sb, "enterprise", v1pdu.getEnterprise().toString());
            entryAppend(sb, "agent-addr", v1pdu.getAgentAddress().toString());
            entryAppend(sb, "generic-trap", Integer.toString(v1pdu.getGenericTrap()));
            entryAppend(sb, "specific-trap", Integer.toString(v1pdu.getSpecificTrap()));
            entryAppend(sb, "time-stamp", Long.toString(v1pdu.getTimestamp()));
        }

        // now loop all variables of the response
        for (Object o : pdu.getVariableBindings()) {
            VariableBinding b = (VariableBinding) o;

            sb.append(ENTRY_TAG_OPEN);
            sb.append(OID_TAG_OPEN);
            sb.append(b.getOid().toString());
            sb.append(OID_TAG_CLOSE);
            sb.append(VALUE_TAG_OPEN);
            sb.append(StringHelper.xmlEncode(b.getVariable().toString()));
            sb.append(VALUE_TAG_CLOSE);
            sb.append(ENTRY_TAG_CLOSE);
        }

        // prepare the footer
        sb.append(SNMP_TAG_CLOSE);

        return sb.toString();
    }

}
