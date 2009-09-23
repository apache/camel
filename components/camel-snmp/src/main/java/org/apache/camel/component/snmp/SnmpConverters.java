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
package org.apache.camel.component.snmp;

import org.apache.camel.Converter;
import org.snmp4j.PDU;
import org.snmp4j.smi.VariableBinding;

@Converter
public final class SnmpConverters {
    public static final String SNMP_TAG = "snmp";
    public static final String ENTRY_TAG = "entry";
    public static final String OID_TAG = "oid";
    public static final String VALUE_TAG = "value";
    
    private static final String SNMP_TAG_OPEN  = '<' + SNMP_TAG + '>';
    private static final String SNMP_TAG_CLOSE = "</" + SNMP_TAG + '>';
    private static final String ENTRY_TAG_OPEN  = '<' + ENTRY_TAG + '>';
    private static final String ENTRY_TAG_CLOSE = "</" + ENTRY_TAG + '>';
    private static final String OID_TAG_OPEN  = '<' + OID_TAG + '>';
    private static final String OID_TAG_CLOSE = "</" + OID_TAG + '>';
    private static final String VALUE_TAG_OPEN  = '<' + VALUE_TAG + '>';
    private static final String VALUE_TAG_CLOSE = "</" + VALUE_TAG + '>';
    
    private SnmpConverters() {
        //Utility Class
    }

    /**
     * Converts the given snmp pdu to a String body.
     *
     * @param pdu       the snmp pdu
     * @return  the text content
     */
    @Converter
    public static String toString(PDU pdu) {
     // the output buffer
        StringBuffer sb = new StringBuffer();
        
        // prepare the header
        sb.append(SNMP_TAG_OPEN);
                
        // now loop all variables of the response
        for (Object o : pdu.getVariableBindings()) {
            VariableBinding b = (VariableBinding)o;

            sb.append(ENTRY_TAG_OPEN);
            sb.append(OID_TAG_OPEN);
            sb.append(b.getOid().toString());
            sb.append(OID_TAG_CLOSE);
            sb.append(VALUE_TAG_OPEN);
            sb.append(b.getVariable().toString());
            sb.append(VALUE_TAG_CLOSE);
            sb.append(ENTRY_TAG_CLOSE);
        }
        
        // prepare the footer
        sb.append(SNMP_TAG_CLOSE);
        
        return sb.toString();
    }
}
