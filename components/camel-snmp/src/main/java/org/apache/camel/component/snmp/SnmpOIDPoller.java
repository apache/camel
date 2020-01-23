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

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
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
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

public class SnmpOIDPoller extends ScheduledPollConsumer implements ResponseListener {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpOIDPoller.class);

    private Address targetAddress;
    private TransportMapping<? extends Address> transport;
    private Snmp snmp;
    
    private Target target;
    private PDU pdu;
    private SnmpEndpoint endpoint;

    public SnmpOIDPoller(SnmpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.targetAddress = GenericAddress.parse(this.endpoint.getAddress());

        // either tcp or udp
        if ("tcp".equals(endpoint.getProtocol())) {
            this.transport = new DefaultTcpTransportMapping();
        } else if ("udp".equals(endpoint.getProtocol())) {
            this.transport = new DefaultUdpTransportMapping();
        } else {
            throw new IllegalArgumentException("Unknown protocol: " + endpoint.getProtocol());
        }

        this.snmp = new Snmp(this.transport);

        if (SnmpConstants.version3 == endpoint.getSnmpVersion()) {
            UserTarget userTarget = new UserTarget();
            
            userTarget.setSecurityLevel(endpoint.getSecurityLevel());
            userTarget.setSecurityName(convertToOctetString(endpoint.getSecurityName()));
            userTarget.setAddress(targetAddress);
            userTarget.setRetries(endpoint.getRetries());
            userTarget.setTimeout(endpoint.getTimeout());
            userTarget.setVersion(endpoint.getSnmpVersion());
            
            this.target = userTarget;
            
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);
            
            OID authProtocol = convertAuthenticationProtocol(endpoint.getAuthenticationProtocol());
            
            OctetString authPwd = convertToOctetString(endpoint.getAuthenticationPassphrase());
            
            OID privProtocol = convertPrivacyProtocol(endpoint.getPrivacyProtocol());
            
            OctetString privPwd = convertToOctetString(endpoint.getPrivacyPassphrase());
            
            UsmUser user = new UsmUser(convertToOctetString(endpoint.getSecurityName()), authProtocol, authPwd, privProtocol, privPwd);
            
            usm.addUser(convertToOctetString(endpoint.getSecurityName()), user);
            
            ScopedPDU scopedPDU = new ScopedPDU();
            
            if (endpoint.getSnmpContextEngineId() != null) {
                scopedPDU.setContextEngineID(new OctetString(endpoint.getSnmpContextEngineId()));
            }
            
            if (endpoint.getSnmpContextName() != null) {
                scopedPDU.setContextName(new OctetString(endpoint.getSnmpContextName()));
            }
            
            this.pdu = scopedPDU;
        } else {
            CommunityTarget communityTarget = new CommunityTarget();
            
            communityTarget.setCommunity(convertToOctetString(endpoint.getSnmpCommunity()));
            communityTarget.setAddress(targetAddress);
            communityTarget.setRetries(endpoint.getRetries());
            communityTarget.setTimeout(endpoint.getTimeout());
            communityTarget.setVersion(endpoint.getSnmpVersion());
            
            this.target = communityTarget;
            
            this.pdu = new PDU();
        }

        // listen to the transport
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting OID poller on {} using {} protocol", endpoint.getAddress(), endpoint.getProtocol());
        }
        this.transport.listen();
        if (LOG.isInfoEnabled()) {
            LOG.info("Started OID poller on {} using {} protocol", endpoint.getAddress(), endpoint.getProtocol());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // stop listening to the transport
        if (this.transport != null && this.transport.isListening()) {
            LOG.info("Stopping OID poller on {}", targetAddress);
            this.transport.close();
            LOG.info("Stopped OID poller on {}", targetAddress);
        }

        super.doStop();
    }

    @Override
    protected int poll() throws Exception {
        this.pdu.clear();
        
        int type = this.getPduType(this.endpoint.getType());
        
        this.pdu.setType(type);

        if (!endpoint.isTreeList()) {
            // prepare the request items
            for (OID oid : this.endpoint.getOids()) {
                this.pdu.add(new VariableBinding(oid));
            }
        } else {
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());
            for (OID oid : this.endpoint.getOids()) {
                List events = treeUtils.getSubtree(target, new OID(oid));
                for (Object eventObj : events) {
                    TreeEvent event = (TreeEvent) eventObj;
                    if (event == null) {
                        LOG.warn("Event is null");
                        continue;
                    }
                    if (event.isError()) {
                        LOG.error("Error in event: {}", event.getErrorMessage());
                        continue;
                    }
                    VariableBinding[] varBindings = event.getVariableBindings();
                    if (varBindings == null || varBindings.length == 0) {
                        continue;
                    }
                    for (VariableBinding varBinding : varBindings) {
                        if (varBinding == null) {
                            continue;
                        }
                        this.pdu.add(varBinding);
                    }
                }
            }
        }

        // send the request
        snmp.send(pdu, target, null, this);

        return 1;
    }

    @Override
    public void onResponse(ResponseEvent event) {
        // Always cancel async request when response has been received
        // otherwise a memory leak is created! Not canceling a request
        // immediately can be useful when sending a request to a broadcast address.
        ((Snmp)event.getSource()).cancel(event.getRequest(), this);

        // check for valid response
        if (event.getRequest() == null || event.getResponse() == null) {
            // ignore null requests/responses
            LOG.debug("Received invalid SNMP event. Request: " + event.getRequest() + " / Response: " + event.getResponse());
            return;
        }
        
        PDU pdu = event.getResponse();
        processPDU(pdu);
    }

    /**
     * processes the pdu message
     * 
     * @param pdu the pdu
     */
    public void processPDU(PDU pdu) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received response event for {} : {}", this.endpoint.getAddress(), pdu);
        }
        Exchange exchange = endpoint.createExchange(pdu);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            getExceptionHandler().handleException(e);
        }
    }

    /** * @return Returns the target.
     */
    public Target getTarget() {
        return this.target;
    }

    /**
     * @param target The target to set.
     */
    public void setTarget(Target target) {
        this.target = target;
    }

    private OctetString convertToOctetString(String value) {
        if (value == null) {
            return null;
        }
        return new OctetString(value);
    }

    private OID convertAuthenticationProtocol(String authenticationProtocol) {
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

    private OID convertPrivacyProtocol(String privacyProtocol) {
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
    
    private int getPduType(SnmpActionType type) {
        if (SnmpActionType.GET_NEXT == type) {
            return PDU.GETNEXT;
        } else {
            return PDU.GET;
        }
    }
    
}
