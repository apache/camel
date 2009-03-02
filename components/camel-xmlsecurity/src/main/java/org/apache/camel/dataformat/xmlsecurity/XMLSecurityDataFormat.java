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
package org.apache.camel.dataformat.xmlsecurity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.NodeIterator;

import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xpath.XPathAPI;

public class XMLSecurityDataFormat implements DataFormat {
    private String xmlCipherAlgorithm;
    private byte[] passPhrase;
    private String secureTag;
    private boolean secureTagContents;

    public XMLSecurityDataFormat() {
        this.xmlCipherAlgorithm = XMLCipher.TRIPLEDES;
        // set a default pass phrase as its required
        this.passPhrase = "Just another 24 Byte key".getBytes();
        this.secureTag = "";
        this.secureTagContents = true;
        org.apache.xml.security.Init.init();
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, byte[] passPhrase) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, byte[] passPhrase, String xmlCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
    }

    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {

        // Retrieve the message body as byte array
        InputStream is = exchange.getContext().getTypeConverter().convertTo(InputStream.class, graph);
        if (is == null) {
            throw new IllegalArgumentException("Cannot get the inputstream for XMLSecurityDataFormat mashalling");
        }

        Document document = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);

        Key keyEncryptionkey;
        Key dataEncryptionkey;
        if (xmlCipherAlgorithm.equals(XMLCipher.TRIPLEDES)) {
            keyEncryptionkey = generateEncryptionKey("DESede");
            dataEncryptionkey = generateEncryptionKey("DESede");
        } else {
            keyEncryptionkey = generateEncryptionKey("AES");
            dataEncryptionkey = generateEncryptionKey("AES");
        }

        XMLCipher keyCipher = XMLCipher.getInstance(generateXmlCipherAlgorithmKeyWrap());
        keyCipher.init(XMLCipher.WRAP_MODE, keyEncryptionkey);

        XMLCipher xmlCipher = XMLCipher.getInstance(xmlCipherAlgorithm);
        xmlCipher.init(XMLCipher.ENCRYPT_MODE, dataEncryptionkey);

        if (secureTag.equalsIgnoreCase("")) {
            embedKeyInfoInEncryptedData(document, keyCipher, xmlCipher, dataEncryptionkey);
            document = xmlCipher.doFinal(document, document.getDocumentElement());
        } else {
            NodeIterator iter = XPathAPI.selectNodeIterator(document, secureTag);
            Node node;
            while ((node = iter.nextNode()) != null) {
                embedKeyInfoInEncryptedData(document, keyCipher, xmlCipher, dataEncryptionkey);
                Document temp = xmlCipher.doFinal(document, (Element) node, getSecureTagContents());
                document.importNode(temp.getDocumentElement().cloneNode(true), true);
            }
        }

        DOMSource source = new DOMSource(document);
        try {
            IOHelper.copy(IOConverter.toInputStrean(source), stream);
        } finally {
            stream.close();
        }

    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        InputStream is = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);

        Key keyEncryptionkey;
        if (xmlCipherAlgorithm.equals(XMLCipher.TRIPLEDES)) {
            keyEncryptionkey = generateEncryptionKey("DESede");
        } else {
            keyEncryptionkey = generateEncryptionKey("AES");
        }

        XMLCipher xmlCipher = XMLCipher.getInstance();
        xmlCipher.init(XMLCipher.DECRYPT_MODE, null);
        xmlCipher.setKEK(keyEncryptionkey);

        Document encodedDocument = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);

        if (secureTag.equalsIgnoreCase("")) {
            encodedDocument = xmlCipher.doFinal(encodedDocument, encodedDocument.getDocumentElement());
        } else {
            NodeIterator iter =
                    XPathAPI.selectNodeIterator(encodedDocument, secureTag);
            Node node;
            while ((node = iter.nextNode()) != null) {
                Document temp = xmlCipher.doFinal(encodedDocument, (Element) node, getSecureTagContents());
                encodedDocument.importNode(temp.getDocumentElement().cloneNode(true), true);
            }
        }

        DOMSource source = new DOMSource(encodedDocument);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            IOHelper.copy(IOConverter.toInputStrean(source), bos);
        } finally {
            bos.close();
        }

        // Return the decrypted data
        return bos.toByteArray();
    }

    private Key generateEncryptionKey(String algorithm) throws InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException {
        DESedeKeySpec keySpec;
        Key secretKey;
        try {
            if (algorithm.equalsIgnoreCase("DESede")) {
                keySpec = new DESedeKeySpec(passPhrase);
                SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm);
                secretKey = keyFactory.generateSecret(keySpec);
            } else {
                secretKey = new SecretKeySpec(passPhrase, "AES");
            }
        } catch (InvalidKeyException e) {
            throw new InvalidKeyException("InvalidKeyException due to invalid passPhrase: " + Arrays.toString(passPhrase));
        } catch (NoSuchAlgorithmException e) {
            throw new NoSuchAlgorithmException("NoSuchAlgorithmException while using XMLCipher.TRIPLEDES algorithm: DESede");
        } catch (InvalidKeySpecException e) {
            throw new InvalidKeySpecException("Invalid Key generated while using passPhrase: " + Arrays.toString(passPhrase));
        }
        return secretKey;
    }

    private void embedKeyInfoInEncryptedData(Document document, XMLCipher keyCipher, XMLCipher xmlCipher, Key dataEncryptionkey) throws XMLEncryptionException {
        EncryptedKey encryptedKey = keyCipher.encryptKey(document, dataEncryptionkey);
        KeyInfo keyInfo = new KeyInfo(document);
        keyInfo.add(encryptedKey);
        EncryptedData encryptedDataElement = xmlCipher.getEncryptedData();
        encryptedDataElement.setKeyInfo(keyInfo);
    }

    private String generateXmlCipherAlgorithmKeyWrap() {
        String algorithmKeyWrap = null;
        if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.TRIPLEDES)) {
            algorithmKeyWrap = XMLCipher.TRIPLEDES_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_128)) {
            algorithmKeyWrap = XMLCipher.AES_128_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_192)) {
            algorithmKeyWrap = XMLCipher.AES_192_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_256)) {
            algorithmKeyWrap = XMLCipher.AES_256_KeyWrap;
        }

        return algorithmKeyWrap;
    }

    public String getXmlCipherAlgorithm() {
        return xmlCipherAlgorithm;
    }

    public void setXmlCipherAlgorithm(String xmlCipherAlgorithm) {
        this.xmlCipherAlgorithm = xmlCipherAlgorithm;
    }

    public byte[] getPassPhrase() {
        return passPhrase;
    }

    public void setPassPhrase(byte[] passPhrase) {
        this.passPhrase = passPhrase;
    }

    public String getSecureTag() {
        return secureTag;
    }

    public void setSecureTag(String secureTag) {
        this.secureTag = secureTag;
    }

    public boolean isSecureTagContents() {
        return secureTagContents;
    }

    public boolean getSecureTagContents() {
        return secureTagContents;
    }

    public void setSecureTagContents(boolean secureTagContents) {
        this.secureTagContents = secureTagContents;
    }

}
