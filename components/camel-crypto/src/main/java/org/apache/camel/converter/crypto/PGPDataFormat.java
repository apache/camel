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
package org.apache.camel.converter.crypto;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.Security;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.util.io.Streams;

/**
 * <code>PGPDataFormat</code> uses the <a href="http://www.bouncycastle.org/java.htm">bouncy castle</a>
 * libraries to enable encryption and decryption in the PGP format.
 */
public class PGPDataFormat implements DataFormat {

    private String keyUserid;
    private String password;
    private String keyFileName;
    private boolean armored;
    private boolean integrity = true;

    public PGPDataFormat() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        PGPPublicKey key = PGPDataFormatUtil.findPublicKey(exchange.getContext(), this.keyFileName, this.keyUserid);
        if (key == null) {
            throw new IllegalArgumentException("Public key is null, cannot proceed");
        }

        byte[] plaintextData;
        InputStream plaintextStream = null;
        try {
            plaintextStream = ExchangeHelper.convertToMandatoryType(exchange, InputStream.class, graph);
            plaintextData = IOUtils.toByteArray(plaintextStream);
        } finally {
            IOUtils.closeQuietly(plaintextStream);
        }
        byte[] compressedData = PGPDataFormatUtil.compress(plaintextData, PGPLiteralData.CONSOLE, CompressionAlgorithmTags.ZIP);

        if (armored) {
            outputStream = new ArmoredOutputStream(outputStream);
        }

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(PGPEncryptedData.CAST5, integrity, new SecureRandom(), "BC");
        encGen.addMethod(key);

        OutputStream encOut = encGen.open(outputStream, compressedData.length);
        try {
            encOut.write(compressedData);
        } finally {
            IOHelper.close(encOut, outputStream);
        }
    }

    public Object unmarshal(Exchange exchange, InputStream encryptedStream) throws Exception {
        if (encryptedStream == null) {
            return null;
        }

        PGPPrivateKey key = PGPDataFormatUtil.findPrivateKey(exchange.getContext(), keyFileName, encryptedStream, password);
        if (key == null) {
            throw new IllegalArgumentException("Private key is null, cannot proceed");
        }

        InputStream in;
        try {
            byte[] encryptedData = IOUtils.toByteArray(encryptedStream);
            InputStream byteStream = new ByteArrayInputStream(encryptedData);
            in = PGPUtil.getDecoderStream(byteStream);
        } finally {
            IOUtils.closeQuietly(encryptedStream);
        }

        PGPObjectFactory pgpF = new PGPObjectFactory(in);
        PGPEncryptedDataList enc;
        Object o = pgpF.nextObject();

        // the first object might be a PGP marker packet.
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }
        IOHelper.close(in);

        PGPPublicKeyEncryptedData pbe = (PGPPublicKeyEncryptedData) enc.get(0);
        InputStream clear = pbe.getDataStream(key, "BC");

        PGPObjectFactory pgpFact = new PGPObjectFactory(clear);
        PGPCompressedData cData = (PGPCompressedData) pgpFact.nextObject();
        pgpFact = new PGPObjectFactory(cData.getDataStream());
        PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();

        byte[] answer;
        try {
            answer = Streams.readAll(ld.getInputStream());
        } finally {
            IOHelper.close(clear);
        }

        return answer;
    }

    /**
     * Sets if the encrypted file should be written in ascii visible text
     */
    public void setArmored(boolean armored) {
        this.armored = armored;
    }

    public boolean getArmored() {
        return this.armored;
    }

    /**
     * Whether or not to add a integrity check/sign to the encrypted file
     */
    public void setIntegrity(boolean integrity) {
        this.integrity = integrity;
    }

    public boolean getIntegrity() {
        return this.integrity;
    }

    /**
     * Userid of the key used to encrypt/decrypt
     */
    public void setKeyUserid(String keyUserid) {
        this.keyUserid = keyUserid;
    }

    public String getKeyUserid() {
        return keyUserid;
    }

    /**
     * filename of the keyring that will be used, classpathResource
     */
    public void setKeyFileName(String keyFileName) {
        this.keyFileName = keyFileName;
    }

    public String getKeyFileName() {
        return keyFileName;
    }

    /**
     * Password used to open the private keyring
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }
}
