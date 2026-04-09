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
package org.apache.camel.component.file.remote;

import java.net.URI;

import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Secure FTP configuration using JSch.
 */
@UriParams
public class SftpConfiguration extends BaseSftpConfiguration {

    // comma separated list of ciphers.
    // null means default jsch list will be used
    @UriParam(label = "security")
    private String ciphers;
    @UriParam(defaultValue = "WARN", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private LoggingLevel jschLoggingLevel = LoggingLevel.WARN;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean existDirCheckUsingLs = true;
    @UriParam(label = "security")
    private String keyExchangeProtocols;
    @UriParam(label = "security")
    private String serverHostKeys;
    @UriParam(label = "advanced", defaultValue = "DEBUG", enums = "TRACE,DEBUG,INFO,WARN,ERROR,OFF")
    private LoggingLevel serverMessageLoggingLevel = LoggingLevel.DEBUG;
    @UriParam(label = "security",
              description = "Set a comma separated list of CA signature algorithms accepted for host certificate verification."
                            + " If not specified the default list from JSch will be used (matches OpenSSH 8.2+ defaults).")
    private String caSignatureAlgorithms;

    public SftpConfiguration() {
        setProtocol("sftp");
    }

    public SftpConfiguration(URI uri) {
        super(uri);
    }

    @Deprecated
    public String getPrivateKeyFilePassphrase() {
        return getPrivateKeyPassphrase();
    }

    @Deprecated
    public void setPrivateKeyFilePassphrase(String privateKeyFilePassphrase) {
        setPrivateKeyPassphrase(privateKeyFilePassphrase);
    }

    /**
     * Set a comma separated list of ciphers that will be used in order of preference. Possible cipher names are defined
     * by JCraft JSCH. Some examples include:
     * aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc,aes192-cbc,aes256-cbc. If not specified the default list
     * from JSCH will be used.
     */
    public void setCiphers(String ciphers) {
        this.ciphers = ciphers;
    }

    public String getCiphers() {
        return ciphers;
    }

    public LoggingLevel getJschLoggingLevel() {
        return jschLoggingLevel;
    }

    /**
     * The logging level to use for JSCH activity logging. As JSCH is verbose at by default at INFO level the threshold
     * is WARN by default.
     */
    public void setJschLoggingLevel(LoggingLevel jschLoggingLevel) {
        this.jschLoggingLevel = jschLoggingLevel;
    }

    public boolean isExistDirCheckUsingLs() {
        return existDirCheckUsingLs;
    }

    /**
     * Whether to check for existing directory using LS command or CD. By default LS is used which is safer as otherwise
     * Camel needs to change the directory back after checking. However LS has been reported to cause a problem on
     * windows system in some situations and therefore you can disable this option to use CD.
     */
    public void setExistDirCheckUsingLs(boolean existDirCheckUsingLs) {
        this.existDirCheckUsingLs = existDirCheckUsingLs;
    }

    public String getKeyExchangeProtocols() {
        return keyExchangeProtocols;
    }

    /**
     * Set a comma separated list of key exchange protocols that will be used in order of preference. Possible cipher
     * names are defined by JCraft JSCH. Some examples include:
     * diffie-hellman-group-exchange-sha1,diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,
     * diffie-hellman-group-exchange-sha256,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521. If not specified
     * the default list from JSCH will be used.
     */
    public void setKeyExchangeProtocols(String keyExchangeProtocols) {
        this.keyExchangeProtocols = keyExchangeProtocols;
    }

    public String getServerHostKeys() {
        return serverHostKeys;
    }

    /**
     * Set a comma separated list of algorithms supported for the server host key. Some examples include:
     * ssh-dss,ssh-rsa,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521. If not specified the default list
     * from JSCH will be used.
     */
    public void setServerHostKeys(String serverHostKeys) {
        this.serverHostKeys = serverHostKeys;
    }

    public LoggingLevel getServerMessageLoggingLevel() {
        return serverMessageLoggingLevel;
    }

    /**
     * The logging level used for various human intended log messages from the FTP server.
     *
     * This can be used during troubleshooting to raise the logging level and inspect the logs received from the FTP
     * server.
     */
    public void setServerMessageLoggingLevel(LoggingLevel serverMessageLoggingLevel) {
        this.serverMessageLoggingLevel = serverMessageLoggingLevel;
    }

    public String getCaSignatureAlgorithms() {
        return caSignatureAlgorithms;
    }

    /**
     * Set a comma separated list of CA signature algorithms accepted for host certificate verification. If not
     * specified the default list from JSch will be used (matches OpenSSH 8.2+ defaults).
     */
    public void setCaSignatureAlgorithms(String caSignatureAlgorithms) {
        this.caSignatureAlgorithms = caSignatureAlgorithms;
    }
}
