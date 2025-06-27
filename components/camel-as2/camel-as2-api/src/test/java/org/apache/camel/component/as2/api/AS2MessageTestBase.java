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
package org.apache.camel.component.as2.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.helger.as2lib.client.AS2Client;
import com.helger.as2lib.client.AS2ClientRequest;
import com.helger.as2lib.client.AS2ClientResponse;
import com.helger.as2lib.client.AS2ClientSettings;
import com.helger.as2lib.crypto.ECompressionType;
import com.helger.as2lib.crypto.ECryptoAlgorithmCrypt;
import com.helger.as2lib.crypto.ECryptoAlgorithmSign;
import com.helger.mail.cte.EContentTransferEncoding;
import com.helger.security.keystore.EKeyStoreType;
import org.apache.camel.component.as2.api.entity.ApplicationEntity;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.hc.core5.http.protocol.HttpDateGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class AS2MessageTestBase {

    protected static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
                                                + "UNH+00000000000117+INVOIC:D:97B:UN'\n"
                                                + "BGM+380+342459+9'\n"
                                                + "DTM+3:20060515:102'\n"
                                                + "RFF+ON:521052'\n"
                                                + "NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT'\n"
                                                + "NAD+SE+005435656::16++GENERAL WIDGET COMPANY'\n"
                                                + "CUX+1:USD'\n"
                                                + "LIN+1++157870:IN'\n"
                                                + "IMD+F++:::WIDGET'\n"
                                                + "QTY+47:1020:EA'\n"
                                                + "ALI+US'\n"
                                                + "MOA+203:1202.58'\n"
                                                + "PRI+INV:1.179'\n"
                                                + "LIN+2++157871:IN'\n"
                                                + "IMD+F++:::DIFFERENT WIDGET'\n"
                                                + "QTY+47:20:EA'\n"
                                                + "ALI+JP'\n"
                                                + "MOA+203:410'\n"
                                                + "PRI+INV:20.5'\n"
                                                + "UNS+S'\n"
                                                + "MOA+39:2137.58'\n"
                                                + "ALC+C+ABG'\n"
                                                + "MOA+8:525'\n"
                                                + "UNT+23+00000000000117'\n"
                                                + "UNZ+1+00000000000778'";

    protected static final String METHOD = "POST";
    protected static final String TARGET_HOST = "localhost";
    protected static final int TARGET_PORT = AvailablePortFinder.getNextAvailable();
    protected static final Duration HTTP_SOCKET_TIMEOUT = Duration.ofSeconds(5);
    protected static final Duration HTTP_CONNECTION_TIMEOUT = Duration.ofSeconds(5);
    protected static final Integer HTTP_CONNECTION_POOL_SIZE = 5;
    protected static final Duration HTTP_CONNECTION_POOL_TTL = Duration.ofMinutes(15);
    protected static final Certificate[] VALIDATE_SIGNING_CERTIFICATE_CHAIN = null;
    protected static final String RECIPIENT_DELIVERY_ADDRESS = "http://localhost:" + TARGET_PORT + "/handle-receipts";
    protected static final String AS2_VERSION = "1.1";
    protected static final String USER_AGENT = "Camel AS2 Endpoint";
    protected static final String REQUEST_URI = "/";
    protected static final String AS2_NAME = "878051556";
    protected static final String SUBJECT = "Test Case";
    protected static final String FROM = "mrAS@example.org";
    protected static final String CLIENT_FQDN = "client.example.org";
    protected static final String SERVER_FQDN = "server.example.org";
    protected static final String REPORTING_UA = "Server Responding with MDN";
    protected static final String DISPOSITION_NOTIFICATION_TO = "mrAS@example.org";
    protected static final String DISPOSITION_NOTIFICATION_OPTIONS
            = "signed-receipt-protocol=optional,pkcs7-signature; signed-receipt-micalg=optional,sha1";
    protected static final String SIGNED_RECEIPT_MIC_ALGORITHMS = "sha1,md5";
    protected static final String MDN_MESSAGE_TEMPLATE = "TBD";
    protected static final HttpDateGenerator DATE_GENERATOR = HttpDateGenerator.INSTANCE;
    protected static AS2ServerConnection testServer;
    protected static KeyPair issueKP;
    protected static X509Certificate issueCert;
    protected static KeyPair signingKP;
    protected static KeyPair decryptingKP;
    protected static X509Certificate signingCert;
    protected static List<X509Certificate> certList;
    protected static File keystoreFile;
    protected static ApplicationEntity ediEntity;
    protected AS2SignedDataGenerator gen;

    @BeforeAll
    public static void setUpOnce() throws Exception {
        setupKeysAndCertificates();
    }

    @AfterAll
    public static void tearDownOnce() {
        testServer.close();
    }

    protected static void setupKeysAndCertificates() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // set up our certificates
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");

        kpg.initialize(1024, new SecureRandom());

        String issueDN = "O=Punkhorn Software, C=US";
        issueKP = kpg.generateKeyPair();
        issueCert = Utils.makeCertificate(issueKP, issueDN, issueKP, issueDN);

        // certificate we sign against
        String signingDN = "CN=William J. Collins, E=punkhornsw@gmail.com, O=Punkhorn Software, C=US";
        signingKP = kpg.generateKeyPair();
        signingCert = Utils.makeCertificate(signingKP, signingDN, issueKP, issueDN);

        // initialize as2-lib keystore file
        KeyStore ks = KeyStore.getInstance(EKeyStoreType.PKCS12.getID());
        ks.load(null, "test".toCharArray());
        ks.setKeyEntry("openas2a_alias", issueKP.getPrivate(), "test".toCharArray(), new X509Certificate[] { issueCert });
        ks.setKeyEntry("openas2b_alias", signingKP.getPrivate(), "test".toCharArray(), new X509Certificate[] { signingCert });
        keystoreFile = Files.createTempFile("camel-as2", "keystore-p12").toFile();
        keystoreFile.deleteOnExit();
        ks.store(new FileOutputStream(keystoreFile), "test".toCharArray());

        certList = new ArrayList<>();
        certList.add(signingCert);
        certList.add(issueCert);
        decryptingKP = signingKP;
    }

    protected void binaryContentTransferEncodingTest(boolean encrypt, boolean sign, boolean compress) throws IOException {
        // test with as2-lib because Camel AS2 client doesn't support binary content transfer encoding at the moment
        // inspired from https://github.com/phax/as2-lib/wiki/Submodule-as2%E2%80%90lib#as2-client

        // Start client configuration
        final AS2ClientSettings aSettings = new AS2ClientSettings();
        aSettings.setKeyStore(EKeyStoreType.PKCS12, keystoreFile, "test");

        // Fixed sender
        aSettings.setSenderData(AS2_NAME, FROM, "openas2a_alias");

        // Fixed receiver
        aSettings.setReceiverData(AS2_NAME, "openas2b_alias", "http://" + TARGET_HOST + ":" + TARGET_PORT + "/");
        aSettings.setReceiverCertificate(issueCert);

        // AS2 stuff
        aSettings.setPartnershipName(aSettings.getSenderAS2ID() + "_" + aSettings.getReceiverAS2ID());

        // Build client request
        final AS2ClientRequest aRequest = new AS2ClientRequest("AS2 test message from as2-lib");
        aRequest.setData(EDI_MESSAGE, StandardCharsets.US_ASCII);
        aRequest.setContentType(AS2MediaType.APPLICATION_EDIFACT);

        // reproduce https://issues.apache.org/jira/projects/CAMEL/issues/CAMEL-15111
        aSettings.setEncryptAndSign(encrypt ? ECryptoAlgorithmCrypt.CRYPT_AES128_GCM : null,
                sign ? ECryptoAlgorithmSign.DIGEST_SHA_512 : null);
        if (compress) {
            aSettings.setCompress(ECompressionType.ZLIB, false);
        }
        aRequest.setContentTransferEncoding(EContentTransferEncoding.BINARY);

        // Send message
        ediEntity = null;
        final AS2ClientResponse aResponse = new AS2Client().sendSynchronous(aSettings, aRequest);

        // Assertions
        if (aResponse.hasException()) {
            fail(aResponse.getException());
        }
        assertTrue(ediEntity.getEdiMessage() instanceof InputStream);
        InputStream is = (InputStream) ediEntity.getEdiMessage();
        String rcvdMessage = new String(is.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\r", "");

        assertEquals(EDI_MESSAGE, rcvdMessage, "EDI message does not match");
    }

    protected void compressionSignatureOrderTest(boolean encrypt, boolean compressBeforeSign) throws IOException {
        // test with as2-lib because Camel AS2 client doesn't support different orders at the moment
        // inspired from https://github.com/phax/as2-lib/wiki/Submodule-as2%E2%80%90lib#as2-client

        // Start client configuration
        final AS2ClientSettings aSettings = new AS2ClientSettings();
        aSettings.setKeyStore(EKeyStoreType.PKCS12, keystoreFile, "test");

        // Fixed sender
        aSettings.setSenderData(AS2_NAME, FROM, "openas2a_alias");

        // Fixed receiver
        aSettings.setReceiverData(AS2_NAME, "openas2b_alias", "http://" + TARGET_HOST + ":" + TARGET_PORT + "/");
        aSettings.setReceiverCertificate(issueCert);

        // AS2 stuff
        aSettings.setPartnershipName(aSettings.getSenderAS2ID() + "_" + aSettings.getReceiverAS2ID());

        // Build client request
        final AS2ClientRequest aRequest = new AS2ClientRequest("AS2 test message from as2-lib");
        aRequest.setData(EDI_MESSAGE, StandardCharsets.US_ASCII);
        aRequest.setContentType(AS2MediaType.APPLICATION_EDIFACT);

        // reproduce https://issues.apache.org/jira/browse/CAMEL-18842
        aSettings.setEncryptAndSign(encrypt ? ECryptoAlgorithmCrypt.CRYPT_AES128_GCM : null,
                ECryptoAlgorithmSign.DIGEST_SHA_512);
        aSettings.setCompress(ECompressionType.ZLIB, compressBeforeSign);
        aRequest.setContentTransferEncoding(EContentTransferEncoding.BINARY);

        // Send message
        ediEntity = null;
        final AS2ClientResponse aResponse = new AS2Client().sendSynchronous(aSettings, aRequest);

        // Assertions
        if (aResponse.hasException()) {
            fail(aResponse.getException());
        }
        assertTrue(ediEntity.getEdiMessage() instanceof InputStream);
        InputStream is = (InputStream) ediEntity.getEdiMessage();
        String rcvdMessage = new String(is.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\r", "");

        assertEquals(EDI_MESSAGE, rcvdMessage, "EDI message does not match");
    }

    protected AS2ClientManager createDefaultClientManager() throws IOException {
        AS2ClientConnection clientConnection = new AS2ClientConnection(
                AS2_VERSION, USER_AGENT, CLIENT_FQDN,
                TARGET_HOST, TARGET_PORT, HTTP_SOCKET_TIMEOUT, HTTP_CONNECTION_TIMEOUT, HTTP_CONNECTION_POOL_SIZE,
                HTTP_CONNECTION_POOL_TTL, null, null);
        return new AS2ClientManager(clientConnection);
    }
}
