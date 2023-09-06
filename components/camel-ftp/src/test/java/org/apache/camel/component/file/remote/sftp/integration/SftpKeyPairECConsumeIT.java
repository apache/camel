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
package org.apache.camel.component.file.remote.sftp.integration;

import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import com.jcraft.jsch.JSch;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf(value = "org.apache.camel.test.infra.ftp.services.embedded.SftpUtil#hasRequiredAlgorithms('src/test/resources/hostkey.pem')")
public class SftpKeyPairECConsumeIT extends SftpServerTestSupport {

    private static final ByteArrayOutputStream PRIVATE_KEY = new ByteArrayOutputStream();
    private static KeyPair keyPair;

    @BeforeAll
    public static void createKeys() throws Exception {
        // default EC KeyPairGenerator returns this ASN.1 structure (PrivateKey.getEncoded()):
        // $ xclip -o | base64 -d | openssl asn1parse -inform der -i
        //    0:d=0  hl=2 l=  96 cons: SEQUENCE
        //    2:d=1  hl=2 l=   1 prim:  INTEGER           :00
        //    5:d=1  hl=2 l=  16 cons:  SEQUENCE
        //    7:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
        //   16:d=2  hl=2 l=   5 prim:   OBJECT            :secp521r1
        //   23:d=1  hl=2 l=  73 prim:  OCTET STRING      [HEX DUMP]:30470201010442006659F1D83A914AFDF5B92A031F8...
        // $ xclip -o | base64 -d | openssl asn1parse -inform der -i -strparse 23
        //    0:d=0  hl=2 l=  71 cons: SEQUENCE
        //    2:d=1  hl=2 l=   1 prim:  INTEGER           :01
        //    5:d=1  hl=2 l=  66 prim:  OCTET STRING      [HEX DUMP]:006659F1D83A914AFDF5B92A031F8B478738B376B63...
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(521);
        keyGen.generateKeyPair();

        // BouncyCastle EC KeyPairGenerator returns this ASN.1 structure (PrivateKey.getEncoded()):
        // $ xclip -o | base64 -d | openssl asn1parse -inform der -i
        //    0:d=0  hl=3 l= 247 cons: SEQUENCE
        //    3:d=1  hl=2 l=   1 prim:  INTEGER           :00
        //    6:d=1  hl=2 l=  16 cons:  SEQUENCE
        //    8:d=2  hl=2 l=   7 prim:   OBJECT            :id-ecPublicKey
        //   17:d=2  hl=2 l=   5 prim:   OBJECT            :secp521r1
        //   24:d=1  hl=3 l= 223 prim:  OCTET STRING      [HEX DUMP]:3081DC0201010442003A93246A8E4E7AC6B8E62276F...
        // $ xclip -o | base64 -d | openssl asn1parse -inform der -i -strparse 24
        //    0:d=0  hl=3 l= 220 cons: SEQUENCE
        //    3:d=1  hl=2 l=   1 prim:  INTEGER           :01
        //    6:d=1  hl=2 l=  66 prim:  OCTET STRING      [HEX DUMP]:003A93246A8E4E7AC6B8E62276F4E730463DE08BAB1...
        //   74:d=1  hl=2 l=   7 cons:  cont [ 0 ]
        //   76:d=2  hl=2 l=   5 prim:   OBJECT            :secp521r1
        //   83:d=1  hl=3 l= 137 cons:  cont [ 1 ]
        //   86:d=2  hl=3 l= 134 prim:   BIT STRING

        KeyPairGenerator keyGenBc = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
        keyGenBc.initialize(521);
        keyGenBc.generateKeyPair();

        // this works better, it generates ASN.1 structure (so the same as embedded OCTET STRING in
        // BC-generated EC key structure):
        // $ xclip -o | openssl asn1parse -i
        //    0:d=0  hl=3 l= 220 cons: SEQUENCE
        //    3:d=1  hl=2 l=   1 prim:  INTEGER           :01
        //    6:d=1  hl=2 l=  66 prim:  OCTET STRING      [HEX DUMP]:01F923B0E659D67612C3F695B0DE377AD295D4EEA1E...
        //   74:d=1  hl=2 l=   7 cons:  cont [ 0 ]
        //   76:d=2  hl=2 l=   5 prim:   OBJECT            :secp521r1
        //   83:d=1  hl=3 l= 137 cons:  cont [ 1 ]
        //   86:d=2  hl=3 l= 134 prim:   BIT STRING
        // and a key with "-----BEGIN EC PRIVATE KEY-----"
        com.jcraft.jsch.KeyPair kp = com.jcraft.jsch.KeyPair.genKeyPair(new JSch(), com.jcraft.jsch.KeyPair.ECDSA, 521);
        kp.writePrivateKey(PRIVATE_KEY);
    }

    @Test
    public void testSftpSimpleConsume() throws Exception {
        String expected = "Hello World";

        // create file using regular file
        template.sendBodyAndHeader("file://" + service.getFtpRootDir(), expected, Exchange.FILE_NAME, "hello.txt");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, "hello.txt");
        mock.expectedBodiesReceived(expected);

        context.getRouteController().startRoute("foo");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("privateKey", PRIVATE_KEY.toByteArray());
        context.getRegistry().bind("knownHosts", service.buildKnownHosts());

        return new RouteBuilder() {
            @Override
            public void configure() {
                from("sftp://localhost:{{ftp.server.port}}/{{ftp.root.dir}}"
                     + "?username=admin&knownHosts=#knownHosts&privateKey=#privateKey&delay=10000&strictHostKeyChecking=yes&useUserKnownHostsFile=false&disconnect=true")
                        .routeId("foo").noAutoStartup()
                        .to("mock:result");
            }
        };
    }
}
