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

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.PDUv1;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.AuthSHA;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.PrivAES192;
import org.snmp4j.security.PrivAES256;
import org.snmp4j.security.PrivDES;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;

final class SnmpHelper {

    private SnmpHelper() {
    }

    static PDU createPDU(SnmpEndpoint endpoint) {
        return switch (endpoint.getSnmpVersion()) {
            case SnmpConstants.version1, SnmpConstants.version2c -> createPDU(endpoint.getSnmpVersion());
            case SnmpConstants.version3 -> createScopedPDU(endpoint);
            default -> null;
        };
    }

    static PDU createPDU(int version) {
        return switch (version) {
            case SnmpConstants.version1 -> new PDUv1();
            case SnmpConstants.version2c -> new PDU();
            case SnmpConstants.version3 -> createScopedPDU(null);
            default -> null;
        };
    }

    static Target createTarget(SnmpEndpoint endpoint) {
        return switch (endpoint.getSnmpVersion()) {
            case SnmpConstants.version1, SnmpConstants.version2c -> createCommunityTarget(endpoint);
            case SnmpConstants.version3 -> createUserTarget(endpoint);
            default -> null;
        };
    }

    static USM createAndSetUSM(SnmpEndpoint endpoint) {
        return switch (endpoint.getSnmpVersion()) {
            case SnmpConstants.version3 -> internalCreateAndSetUSM(endpoint);
            default -> null;
        };
    }

    private static USM internalCreateAndSetUSM(SnmpEndpoint endpoint) {
        if (endpoint.getSecurityName() == null) {
            throw new IllegalArgumentException("SecurityNme is required for SNMP v3");
        }

        USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        SecurityModels.getInstance().addSecurityModel(usm);

        OID authProtocol = convertAuthenticationProtocol(endpoint.getAuthenticationProtocol());

        OctetString authPwd = convertToOctetString(endpoint.getAuthenticationPassphrase());

        OID privProtocol = convertPrivacyProtocol(endpoint.getPrivacyProtocol());

        OctetString privPwd = convertToOctetString(endpoint.getPrivacyPassphrase());

        UsmUser user = new UsmUser(
                convertToOctetString(endpoint.getSecurityName()), authProtocol, authPwd, privProtocol, privPwd);

        usm.addUser(convertToOctetString(endpoint.getSecurityName()), user);

        return usm;
    }

    private static ScopedPDU createScopedPDU(SnmpEndpoint endpoint) {
        ScopedPDU scopedPDU = new ScopedPDU();
        if (endpoint != null && endpoint.getSnmpContextEngineId() != null) {
            scopedPDU.setContextEngineID(convertToOctetString(endpoint.getSnmpContextEngineId()));
        }
        if (endpoint != null && endpoint.getSnmpContextName() != null) {
            scopedPDU.setContextName(convertToOctetString(endpoint.getSnmpContextName()));
        }

        return scopedPDU;
    }

    private static CommunityTarget createCommunityTarget(SnmpEndpoint endpoint) {
        CommunityTarget communityTarget = new CommunityTarget();

        communityTarget.setCommunity(convertToOctetString(endpoint.getSnmpCommunity()));
        communityTarget.setAddress(GenericAddress.parse(endpoint.getAddress()));
        communityTarget.setRetries(endpoint.getRetries());
        communityTarget.setTimeout(endpoint.getTimeout());
        communityTarget.setVersion(endpoint.getSnmpVersion());

        return communityTarget;
    }

    private static UserTarget createUserTarget(SnmpEndpoint endpoint) {
        UserTarget userTarget = new UserTarget();

        userTarget.setSecurityLevel(endpoint.getSecurityLevel());
        userTarget.setSecurityName(convertToOctetString(endpoint.getSecurityName()));
        userTarget.setAddress(GenericAddress.parse(endpoint.getAddress()));
        userTarget.setRetries(endpoint.getRetries());
        userTarget.setTimeout(endpoint.getTimeout());
        userTarget.setVersion(endpoint.getSnmpVersion());

        return userTarget;
    }

    private static OctetString convertToOctetString(String value) {
        if (value == null) {
            return null;
        }
        return new OctetString(value);
    }

    private static OID convertAuthenticationProtocol(String authenticationProtocol) {
        if (authenticationProtocol == null) {
            return null;
        }
        if ("MD5".equals(authenticationProtocol)) {
            return AuthMD5.ID;
        } else if ("SHA1".equals(authenticationProtocol)) {
            return AuthSHA.ID;
        } else {
            throw new IllegalArgumentException("Unknown authentication protocol: " + authenticationProtocol);
        }
    }

    private static OID convertPrivacyProtocol(String privacyProtocol) {
        if (privacyProtocol == null) {
            return null;
        }
        if ("DES".equals(privacyProtocol)) {
            return PrivDES.ID;
        } else if ("TRIDES".equals(privacyProtocol)) {
            return Priv3DES.ID;
        } else if ("AES128".equals(privacyProtocol)) {
            return PrivAES128.ID;
        } else if ("AES192".equals(privacyProtocol)) {
            return PrivAES192.ID;
        } else if ("AES256".equals(privacyProtocol)) {
            return PrivAES256.ID;
        } else {
            throw new IllegalArgumentException("Unknown privacy protocol: " + privacyProtocol);
        }
    }
}
