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
package org.apache.camel.component.file.remote;

import java.net.URI;

/**
 * FTP Secure (FTP over SSL/TLS) configuration
 * 
 * @version $Revision$
 */
public class FtpsConfiguration extends FtpConfiguration {

    private String securityProtocol = "TLS";
    private boolean isImplicit;
    private boolean useSecureDataChannel;
    private String execProt = "P";
    private Long execPbsz;

    public FtpsConfiguration() {
        setProtocol("ftps");
    }

    public FtpsConfiguration(URI uri) {
        super(uri);
    }

    @Override
    protected void setDefaultPort() {
        setPort(2222);
    }

    /**
     * Returns the underlying security protocol.
     */
    public String getSecurityProtocol() {
        return securityProtocol;
    }

    /**
     * Set the underlying security protocol.
     */
    public void setSecurityProtocol(String securityProtocol) {
        this.securityProtocol = securityProtocol;
    }

    /**
     * Returns the security mode(Implicit/Explicit).
     * true - Implicit Mode / False - Explicit Mode
     */
    public boolean isImplicit() {
        return isImplicit;
    }

    /**
     * Set the security mode(Implicit/Explicit).
     * true - Implicit Mode / False - Explicit Mode
     */
    public void setIsImplicit(boolean isImplicit) {
        this.isImplicit = isImplicit;
    }

    public boolean isUseSecureDataChannel() {
        return useSecureDataChannel;
    }

    /**
     * Sets whether to use secure data channel when transferring file content
     * <p/>
     * Default is <tt>false</tt>
     * @see #setExecPbsz(Long)
     * @see #setExecProt(String)
     */
    public void setUseSecureDataChannel(boolean useSecureDataChannel) {
        this.useSecureDataChannel = useSecureDataChannel;
    }

    public String getExecProt() {
        return execProt;
    }

    /**
     * When using secure data channel you can set the exec protection level
     * <p/>
     * PROT command. C - Clear S - Safe(SSL protocol only) E - Confidential(SSL protocol only) P - Private
     * <p/>
     * Default value is <tt>P</tt>
     * 
     * @param execProt either C, S, E or P
     */
    public void setExecProt(String execProt) {
        this.execProt = execProt;
    }

    public Long getExecPbsz() {
        return execPbsz;
    }

    /**
     * When using secure data channel you can set the exec protection buffer size
     * <p/>
     * Default value is <tt>0</tt>
     *
     * @param execPbsz the buffer size
     */
    public void setExecPbsz(Long execPbsz) {
        this.execPbsz = execPbsz;
    }
}