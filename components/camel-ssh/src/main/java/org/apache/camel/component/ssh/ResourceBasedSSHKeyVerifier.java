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
package org.apache.camel.component.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.bouncycastle.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ServerKeyVerifier that takes a camel resource as input file to validate the server key against.
 *
 */
public class ResourceBasedSSHKeyVerifier implements ServerKeyVerifier {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private CamelContext camelContext;
    private boolean failOnUnknownHost;
    private String knownHostsResource;

    public ResourceBasedSSHKeyVerifier(CamelContext camelContext, String knownHostsResource) {
        this(camelContext, knownHostsResource, false);
    }

    public ResourceBasedSSHKeyVerifier(CamelContext camelContext, String knownHostsResource,
                                       boolean failOnUnknownHost) {
        this.camelContext = camelContext;
        this.knownHostsResource = knownHostsResource;
        this.failOnUnknownHost = failOnUnknownHost;
    }

    @Override
    public boolean verifyServerKey(ClientSession sshClientSession, SocketAddress remoteAddress, PublicKey serverKey) {
        log.debug("Trying to find known_hosts file {}", knownHostsResource);
        InputStream knownHostsInputStream = null;
        try {
            knownHostsInputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext,
                    knownHostsResource);
            List<String> possibleTokens = getKnownHostsFileTokensForSocketAddress(remoteAddress);
            log.debug("Trying to match PublicKey against provided known_hosts file");
            PublicKey matchingKey = findKeyForServerToken(knownHostsInputStream, possibleTokens);
            if (matchingKey != null) {
                log.debug("Found PublicKey match for server");
                return Arrays.areEqual(matchingKey.getEncoded(), serverKey.getEncoded());
            }
        } catch (IOException ioException) {
            log.debug(String.format("Could not find known_hosts file %s", knownHostsResource), ioException);
        } finally {
            IOHelper.close(knownHostsInputStream);
        }
        if (failOnUnknownHost) {
            log.warn("Could not find matching key for client session, connection will fail due to configuration");
            return false;
        } else {
            log.warn(
                    "Could not find matching key for client session, connection will continue anyway due to configuration");
            return true;
        }
    }

    private PublicKey findKeyForServerToken(InputStream knownHostsInputStream, List<String> possibleTokens) {
        String knowHostsLines = camelContext.getTypeConverter().convertTo(String.class, knownHostsInputStream);
        if (knowHostsLines == null) {
            log.warn("Could not read from the known_hosts file input stream");
            return null;
        }

        for (String s : knowHostsLines.split("\n")) {
            String[] parts = s.split(" ");
            if (parts.length != 3) {
                log.warn("Found malformed entry in known_hosts file");
                continue;
            }
            String entry = parts[0];
            String key = parts[2];
            for (String serverToken : possibleTokens) {
                if (entry.contains(serverToken)) {
                    try {
                        return loadKey(key);
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                        log.warn(String.format("Could not load key for server token %s", entry), e);
                    }
                }
            }
        }
        return null;
    }

    private List<String> getKnownHostsFileTokensForSocketAddress(SocketAddress remoteAddress) {
        List<String> returnList = new LinkedList<>();
        if (remoteAddress instanceof InetSocketAddress) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;

            String hostName = inetSocketAddress.getHostName();
            String ipAddress = inetSocketAddress.getAddress().getHostAddress();
            String remotePort = String.valueOf(inetSocketAddress.getPort());

            returnList.add(hostName);
            returnList.add("[" + hostName + "]:" + remotePort);
            returnList.add(ipAddress);
            returnList.add("[" + ipAddress + "]:" + remotePort);
        }

        return returnList;
    }

    /*
     * Decode the public key string, which is a base64 encoded string that consists
     * of multiple parts: 1. public key type (ssh-rsa, ssh-dss, ...) 2. binary key
     * data (May consists of multiple parts)
     *
     * Each part is composed by two sub-parts 1. Length of the part (4 bytes) 2.
     * Binary part (length as defined by 1.)
     *
     * Uses SSHPublicKeyHolder to construct the actual PublicKey Object
     *
     * Note: Currently only supports RSA and DSA Public keys as required by
     * https://tools.ietf.org/html/rfc4253#section-6.6
     *
     */
    PublicKey loadKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SSHPublicKeyHolder sshPublicKeyHolder = new SSHPublicKeyHolder();

        byte[] keyByteArray = Base64.getDecoder().decode(key);
        int keyByteArrayCursor = 0;

        byte[] tmpData = new byte[4];
        int tmpCursor = 0;

        boolean getLengthMode = true;
        while (keyByteArrayCursor < keyByteArray.length) {
            if (getLengthMode) {
                if (tmpCursor < 4) {
                    tmpData[tmpCursor] = keyByteArray[keyByteArrayCursor];
                    tmpCursor++;
                    keyByteArrayCursor++;
                    continue;
                } else {
                    tmpCursor = 0;
                    getLengthMode = false;
                    tmpData = new byte[byteArrayToInt(tmpData)];
                }
            }
            tmpData[tmpCursor] = keyByteArray[keyByteArrayCursor];
            tmpCursor++;
            keyByteArrayCursor++;
            if (tmpCursor == tmpData.length) {
                sshPublicKeyHolder.push(tmpData);
                getLengthMode = true;
                tmpData = new byte[4];
                tmpCursor = 0;
            }
        }

        return sshPublicKeyHolder.toPublicKey();
    }

    private int byteArrayToInt(byte[] tmpData) {
        return new BigInteger(tmpData).intValue();
    }

}
