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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.util.io.Streams;

/**
 * <code>PGPDataFormat</code> uses the bouncy castle libraries to enable
 * encryption and decryption in the PGP format I have also tested decrypting the
 * files produced using GnuPG Linux command line program gpg (GnuPG) 1.4.11
 * <ul>
 *   <li>http://www.bouncycastle.org/java.html</li>
 * <ul>
 * <p/>
 */
public class PGPDataFormat implements DataFormat {

    public static final String KEY_PUB = "CamelCryptoKeyPub";
    public static final String KEY_PRI = "CamelCryptoKeyPri";

    private PGPPublicKey configuredKey;
    private PGPPrivateKey configuredPrivateKey;
    private boolean armor;
    private boolean integrity = true;

    public PGPDataFormat() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public void setArmored(boolean armor) {
        this.armor = armor;
    }

    public void setIntegrity(boolean integrity) {
        this.integrity = integrity;
    }

    /**
     * Set the key that should be used to encrypt or decrypt incoming encrypted exchanges.
     */
    public void setPublicKey(PGPPublicKey key) {
        this.configuredKey = key;
    }

    public void setPrivateKey(PGPPrivateKey key) {
        this.configuredPrivateKey = key;
    }

    public void marshal(Exchange exchange, Object graph, OutputStream outputStream) throws Exception {
        PGPPublicKey key = getPublicKey(exchange);
        if (key == null) {
            throw new IllegalArgumentException("Public key is null, cannot proceed");
        }

        InputStream plaintextStream = ExchangeHelper.convertToMandatoryType(exchange, InputStream.class, graph);

        byte[] compressedData = compress(IOUtils.toByteArray(plaintextStream),
                PGPLiteralData.CONSOLE, CompressionAlgorithmTags.ZIP);

        if (armor) {
            outputStream = new ArmoredOutputStream(outputStream);
        }

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                PGPEncryptedData.CAST5, integrity, new SecureRandom(), "BC");
        encGen.addMethod(key);

        OutputStream encOut = encGen.open(outputStream, compressedData.length);
        try {
            encOut.write(compressedData);
        } finally {
            IOHelper.close(encOut);
            if (armor) {
                IOHelper.close(outputStream);
            }
        }
    }

    public Object unmarshal(Exchange exchange, InputStream encryptedStream) throws Exception {
        if (encryptedStream == null) {
            return null;
        }

        PGPPrivateKey key = getPrivateKey(exchange);
        if (key == null) {
            throw new IllegalArgumentException("Private key is null, cannot proceed");
        }

        InputStream in = new ByteArrayInputStream(IOUtils.toByteArray(encryptedStream));
        in = PGPUtil.getDecoderStream(in);

        PGPObjectFactory pgpF = new PGPObjectFactory(in);
        PGPEncryptedDataList enc;
        Object o = pgpF.nextObject();

        // the first object might be a PGP marker packet.
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }

        PGPPublicKeyEncryptedData pbe = (PGPPublicKeyEncryptedData) enc.get(0);
        InputStream clear = pbe.getDataStream(key, "BC");

        PGPObjectFactory pgpFact = new PGPObjectFactory(clear);
        PGPCompressedData cData = (PGPCompressedData) pgpFact.nextObject();
        pgpFact = new PGPObjectFactory(cData.getDataStream());
        PGPLiteralData ld = (PGPLiteralData) pgpFact.nextObject();
        return Streams.readAll(ld.getInputStream());
    }

    private static byte[] compress(byte[] clearData, String fileName, int algorithm) throws IOException {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(algorithm);
        OutputStream cos = comData.open(bOut); // open it with the final destination

        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

        OutputStream pOut = lData.open(cos, // the compressed output stream
                PGPLiteralData.BINARY, fileName, // "filename" to store
                clearData.length, // length of clear data
                new Date() // current time
        );

        try {
            pOut.write(clearData);
        } finally {
            IOHelper.close(pOut);
            comData.close();
        }
        return bOut.toByteArray();
    }

    private PGPPublicKey getPublicKey(Exchange exchange) {
        PGPPublicKey key = exchange.getIn().getHeader(KEY_PUB, PGPPublicKey.class);
        if (key == null) {
            key = configuredKey;
        }
        return key;
    }

    private PGPPrivateKey getPrivateKey(Exchange exchange) {
        PGPPrivateKey key = exchange.getIn().getHeader(KEY_PRI, PGPPrivateKey.class);
        if (key == null) {
            key = configuredPrivateKey;
        }
        return key;
    }

}
