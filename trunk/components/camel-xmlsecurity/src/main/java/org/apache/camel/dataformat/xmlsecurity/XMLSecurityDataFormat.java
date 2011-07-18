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
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xpath.XPathAPI;

public class XMLSecurityDataFormat implements DataFormat, CamelContextAware {

    public static final String XML_ENC_RECIPIENT_ALIAS = "CamelXmlEncryptionRecipientAlias";
    public static final String XML_ENC_TRUST_STORE_URL = "CamelXmlEncryptionTrustStoreUrl";
    public static final String XML_ENC_TRUST_STORE_PASSWORD = "CamelXmlEncryptionTrustStorePassword";
    public static final String XML_ENC_KEY_STORE_URL = "CamelXmlEncryptionKeyStoreUrl";
    public static final String XML_ENC_KEY_STORE_PASSWORD = "CamelXmlEncryptionKeyStorePassword";
    public static final String XML_ENC_KEY_STORE_ALIAS = "CamelXmlEncryptionKeyAlias";

    private String xmlCipherAlgorithm;
    private String keyCipherAlgorithm;
    private byte[] passPhrase;

    private String secureTag;
    private boolean secureTagContents;
    
    private KeyStore keyStore;
    private KeyStore trustStore;
    private String keyStoreAlias;
    private String keyStorePassword;
    private String trustStorePassword;
    private String recipientKeyAlias;
    
    private CamelContext camelContext;
        

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

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, byte[] passPhrase, 
                                 String xmlCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
    }
    
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
    }
      
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias, 
                                 String xmlCipherAlgorithm, String keyCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
    }
    
    @Override
    public void setCamelContext(CamelContext camelContext) {
        try {
            setDefaultsFromContext(camelContext);
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize XMLSecurityDataFormat with camelContext. ", e);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }
    
    /**
     * Sets missing properties that are defined in the Camel context.
     */
    private void setDefaultsFromContext(CamelContext context) throws Exception {
        Map<String, String> contextProps = context.getProperties();
        
        if (this.recipientKeyAlias == null) {
            recipientKeyAlias = contextProps.get(XML_ENC_RECIPIENT_ALIAS);
        }
        
        if (this.trustStore == null && contextProps.containsKey(XML_ENC_TRUST_STORE_URL)) {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            URL trustStoreUrl = new URL(contextProps.get(XML_ENC_TRUST_STORE_URL));
            if (trustStorePassword == null) {
                trustStorePassword = contextProps.get(XML_ENC_TRUST_STORE_PASSWORD);
            }
            trustStore.load(trustStoreUrl.openStream(), trustStorePassword.toCharArray());
        }
        
        if (this.keyStore == null && contextProps.containsKey(XML_ENC_KEY_STORE_URL)) {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            URL keyStoreUrl = new URL(contextProps.get(XML_ENC_KEY_STORE_URL));
            if (keyStorePassword == null) {
                keyStorePassword = contextProps.get(XML_ENC_KEY_STORE_PASSWORD);
            }
            keyStore.load(keyStoreUrl.openStream(), keyStorePassword.toCharArray());
        }
        
        if (this.keyStoreAlias == null) {
            keyStoreAlias = contextProps.get(XML_ENC_KEY_STORE_ALIAS);
        }
    }
    
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // Retrieve the message body as input stream
        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, graph);
        // and covert that to XML
        Document document = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);
        
        if (null != keyCipherAlgorithm 
            && (keyCipherAlgorithm.equals(XMLCipher.RSA_v1dot5) || keyCipherAlgorithm.equals(XMLCipher.RSA_OAEP))) {
            encryptAsymmetric(exchange, document, stream);
        } else if (null != recipientKeyAlias) {
            encryptAsymmetric(exchange, document, stream);
        } else {
            encryptSymmetric(exchange, document, stream);
        }
    }
    
    /**
     * Configure the public key for the asymmetric key wrap algorithm, create the key cipher, and delegate
     * to common encryption method.
     * 
     * The method first checks the exchange for a declared key alias, and will fall back to the
     * statically-defined instance variable if no value is found in the exchange. This allows different
     * aliases / keys to be used for multiple-recipient messaging integration patterns such as CBR
     * or recipient list.
     */
    private void encryptAsymmetric(Exchange exchange, Document document, OutputStream stream) throws Exception {       
        String exchangeRecipientAlias = getRecipientKeyAlias(exchange);
        
        if (null == exchangeRecipientAlias) {
            throw new IllegalStateException("The  recipient's key alias must be defined for asymmetric key encryption.");
        }

        if (null == trustStore) {
            throw new IllegalStateException("A trust store must be defined for asymmetric key encryption.");
        }
        
        Key keyEncryptionKey = getPublicKey(this.trustStore, exchangeRecipientAlias, this.trustStorePassword);
        
        if (null == keyEncryptionKey) {
            throw new IllegalStateException("No key for the alias [ " + exchangeRecipientAlias 
                + " ] exists in " + "the configured trust store.");
        }
        
        Key dataEncryptionKey = generateDataEncryptionKey();
        
        XMLCipher keyCipher;
        if (null != this.getKeyCyperAlgorithm()) {
            keyCipher = XMLCipher.getInstance(this.getKeyCyperAlgorithm());
        } else {
            keyCipher = XMLCipher.getInstance(XMLCipher.RSA_v1dot5);
        }
        keyCipher.init(XMLCipher.WRAP_MODE, keyEncryptionKey);
        encrypt(exchange, document, stream, dataEncryptionKey, keyCipher);
    }
     
    private void encryptSymmetric(Exchange exchange, Document document, OutputStream stream) throws Exception {
        Key keyEncryptionKey;
        Key dataEncryptionKey;
        if (xmlCipherAlgorithm.equals(XMLCipher.TRIPLEDES)) {
            keyEncryptionKey = generateKeyEncryptionKey("DESede");
            dataEncryptionKey = generateDataEncryptionKey();
        } else {
            keyEncryptionKey = generateKeyEncryptionKey("AES");
            dataEncryptionKey = generateDataEncryptionKey();
        }
        
        XMLCipher keyCipher = XMLCipher.getInstance(generateXmlCipherAlgorithmKeyWrap());
        keyCipher.init(XMLCipher.WRAP_MODE, keyEncryptionKey);
        
        encrypt(exchange, document, stream, dataEncryptionKey, keyCipher);
    }
    
    
    /**
     * Returns the private key for the specified alias, or null if the alias or private key is not found.
     */
    // TODO Move this to a crypto utility class
    private Key getPrivateKey(KeyStore keystore, String alias, String password) throws Exception {
        Key key = keystore.getKey(alias, password.toCharArray());
        if (key instanceof PrivateKey) {
            return key;
        } else {
            return null;
        }
    }
    
    /**
     * Returns the public key for the specified alias, or null if the alias or private key is not found.
     */    
    // TODO Move this to a crypto utility class
    private Key getPublicKey(KeyStore keystore, String alias, String password) throws Exception {
        Key key = keystore.getKey(alias, password.toCharArray());
        if (key instanceof PublicKey) {
            return key;
        } else {
            java.security.cert.Certificate cert = keystore.getCertificate(alias);
            // Get public key
            PublicKey publicKey = cert.getPublicKey();
            return publicKey;
        }
    }
 
    
    private void encrypt(Exchange exchange, Document document, OutputStream stream, Key dataEncryptionKey, 
                         XMLCipher keyCipher) throws Exception {
        XMLCipher xmlCipher = XMLCipher.getInstance(xmlCipherAlgorithm);
        xmlCipher.init(XMLCipher.ENCRYPT_MODE, dataEncryptionKey);

        if (secureTag.equalsIgnoreCase("")) {
            embedKeyInfoInEncryptedData(document, keyCipher, xmlCipher, dataEncryptionKey);
            document = xmlCipher.doFinal(document, document.getDocumentElement());
        } else {
            NodeIterator iter = XPathAPI.selectNodeIterator(document, secureTag);
            Node node;
            while ((node = iter.nextNode()) != null) {
                embedKeyInfoInEncryptedData(document, keyCipher, xmlCipher, dataEncryptionKey);
                Document temp = xmlCipher.doFinal(document, (Element) node, getSecureTagContents());
                document.importNode(temp.getDocumentElement().cloneNode(true), true);
            }
        }

        try {
            DOMSource source = new DOMSource(document);
            InputStream sis = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, source);
            IOHelper.copy(sis, stream);
        } finally {
            stream.close();
        }
    }
    
    public Object unmarshal(Exchange exchange, Document document) throws Exception {
        InputStream is = ExchangeHelper.getMandatoryInBody(exchange, InputStream.class);
        return unmarshal(exchange, is);
    }
    
    
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        Document encodedDocument = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, stream);        
        
        if (null != keyCipherAlgorithm 
            && (keyCipherAlgorithm.equals(XMLCipher.RSA_v1dot5) || keyCipherAlgorithm.equals(XMLCipher.RSA_OAEP))) {
            return decodeWithAsymmetricKey(exchange, encodedDocument);
        } else {
            return decodeWithSymmetricKey(exchange, encodedDocument);
        }
    }
    
    private Object decodeWithSymmetricKey(Exchange exchange, Document encodedDocument) throws Exception {
        Key keyEncryptionKey;
        if (xmlCipherAlgorithm.equals(XMLCipher.TRIPLEDES)) {
            keyEncryptionKey = generateKeyEncryptionKey("DESede");
        } else {
            keyEncryptionKey = generateKeyEncryptionKey("AES");
        }

        return decode(exchange, encodedDocument, keyEncryptionKey);
    }
    
    private Object decodeWithAsymmetricKey(Exchange exchange, Document encodedDocument) throws Exception {        
        if (this.keyStore ==  null) {
            throw new IllegalStateException("A key store must be defined for asymmetric key decryption.");
        }
        
        Key keyEncryptionKey = getPrivateKey(this.keyStore, this.keyStoreAlias, this.keyStorePassword);
        return decode(exchange, encodedDocument, keyEncryptionKey);
    }
    
    private Object decode(Exchange exchange, Document encodedDocument, Key keyEncryptionKey) throws Exception {
        XMLCipher xmlCipher = XMLCipher.getInstance();
        xmlCipher.init(XMLCipher.DECRYPT_MODE, null);
        xmlCipher.setKEK(keyEncryptionKey);

        if (secureTag.equalsIgnoreCase("")) {
            encodedDocument = xmlCipher.doFinal(encodedDocument, encodedDocument.getDocumentElement());
        } else {
            NodeIterator iter =
                XPathAPI.selectNodeIterator(encodedDocument, secureTag);
            Node node;
            while ((node = iter.nextNode()) != null) {
                if (getSecureTagContents()) {
                    Document temp = xmlCipher.doFinal(encodedDocument, (Element) node, true);
                    encodedDocument.importNode(temp.getDocumentElement().cloneNode(true), true);
                } else {
                    NodeList childNodes = node.getChildNodes();
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        Node childNode = childNodes.item(i);
                        if (childNode.getLocalName().equals("EncryptedData")) {
                            Document temp = xmlCipher.doFinal(encodedDocument, (Element) childNode, false);
                            encodedDocument.importNode(temp.getDocumentElement().cloneNode(true), true);
                        }    
                    }
                }    
            }
        }
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            DOMSource source = new DOMSource(encodedDocument);
            InputStream sis = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, source);
            IOHelper.copy(sis, bos);
        } finally {
            bos.close();
        }

        // Return the decrypted data
        return bos.toByteArray();
    }
    
    
    private Key generateKeyEncryptionKey(String algorithm) throws 
            InvalidKeyException, NoSuchAlgorithmException, InvalidKeySpecException {

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
    
    private Key generateDataEncryptionKey() throws Exception {      
        KeyGenerator keyGenerator = null;
        if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.TRIPLEDES)) {
            keyGenerator = KeyGenerator.getInstance("DESede");
        } else {
            keyGenerator = KeyGenerator.getInstance("AES");
        }
        if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_128)) {
            keyGenerator.init(128);
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_192)) {
            keyGenerator.init(192);
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_256)) {
            keyGenerator.init(256);
        }
        return keyGenerator.generateKey();
    }

    private void embedKeyInfoInEncryptedData(Document document, XMLCipher keyCipher, XMLCipher xmlCipher, Key dataEncryptionkey) 
        throws XMLEncryptionException {

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
    
    private String getRecipientKeyAlias(Exchange exchange) {
        String alias = exchange.getIn().getHeader(XML_ENC_RECIPIENT_ALIAS, String.class);
        if (alias != null) {
            exchange.getIn().setHeader(XML_ENC_RECIPIENT_ALIAS, null);
        } else {
            alias = recipientKeyAlias;
        }
        return alias;
    }

    public String getXmlCipherAlgorithm() {
        return xmlCipherAlgorithm;
    }

    public void setXmlCipherAlgorithm(String xmlCipherAlgorithm) {
        this.xmlCipherAlgorithm = xmlCipherAlgorithm;
    }
    
    public String getKeyCyperAlgorithm() {
        return keyCipherAlgorithm;
    }
    
    public void setKeyCipherAlgorithm(String keyCipherAlgorithm) {
        this.keyCipherAlgorithm = keyCipherAlgorithm;
    }
    
    public String getRecipientKeyAlias() {
        return this.recipientKeyAlias;
    }
    
    public void setRecipientKeyAlias(String recipientKeyAlias) {
        this.recipientKeyAlias = recipientKeyAlias;
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
    
    public KeyStore getKeyStore() {
        return this.keyStore;
    }
    
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }
         
    public KeyStore getTrustStore() {
        return this.trustStore;
    }
    
    public void setTrustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
    }
    
    public String getKeyStoreAlias() {
        return this.keyStoreAlias;
    }
    
    public void setKeyStoreAlias(String keyStoreAlias) {
        this.keyStoreAlias = keyStoreAlias;
    }
    
    public String getKeyStorePassword() {
        return this.keyStorePassword;
    }
    
    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }
    
    public String getTrustStorePassowrd() {
        return this.trustStorePassword;
    }
    
    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }
}
