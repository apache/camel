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
import java.lang.reflect.Field;
import java.net.URL;
import java.security.AccessController;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
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

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.builder.xml.DefaultNamespaceContext;
import org.apache.camel.builder.xml.XPathBuilder;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.apache.xml.security.encryption.EncryptedData;
import org.apache.xml.security.encryption.EncryptedKey;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.encryption.XMLEncryptionException;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.EncryptionConstants;
import org.apache.xml.security.utils.XMLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class XMLSecurityDataFormat extends ServiceSupport implements DataFormat, DataFormatName, CamelContextAware {

    /**
     * @deprecated  Use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.
     */
    @Deprecated
    public static final String XML_ENC_RECIPIENT_ALIAS = "CamelXmlEncryptionRecipientAlias";
    
    /**
     * @deprecated  Use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.
     */
    @Deprecated
    public static final String XML_ENC_TRUST_STORE_URL = "CamelXmlEncryptionTrustStoreUrl";
    
    /**
     * @deprecated  Use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.
     */
    @Deprecated
    public static final String XML_ENC_TRUST_STORE_PASSWORD = "CamelXmlEncryptionTrustStorePassword";
    
    /**
     * @deprecated  Use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.
     */                 
    @Deprecated
    public static final String XML_ENC_KEY_STORE_URL = "CamelXmlEncryptionKeyStoreUrl";
    
    /**
     * @deprecated  Use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.
     */
    @Deprecated
    public static final String XML_ENC_KEY_STORE_PASSWORD = "CamelXmlEncryptionKeyStorePassword";
    
    /**
     * @deprecated  Use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.
     */
    @Deprecated
    public static final String XML_ENC_KEY_STORE_ALIAS = "CamelXmlEncryptionKeyAlias";
    
    private static final Logger LOG = LoggerFactory.getLogger(XMLSecurityDataFormat.class);
    private static final String DEFAULT_KEY = "Just another 24 Byte key";

    private String xmlCipherAlgorithm;
    private String keyCipherAlgorithm;
    
    /**
     * Digest Algorithm to be used with RSA-OAEP. The default is SHA-1 (which is not
     * written out unless it is explicitly configured).
     */
    private String digestAlgorithm;
    
    /**
     * MGF Algorithm to be used with RSA-OAEP. The default is MGF-SHA-1 (which is not
     * written out unless it is explicitly configured).
     */
    private String mgfAlgorithm;

    private byte[] passPhrase;

    private String secureTag;
    private boolean secureTagContents;
    
    private KeyStore keyStore;
    private KeyStore trustStore;

    private String keyStorePassword;
    private String trustStorePassword;
    private String recipientKeyAlias;
    private String keyPassword;
    
    private KeyStoreParameters keyOrTrustStoreParameters;
    private String keyOrTrustStoreParametersId;
    
    private CamelContext camelContext;
    private DefaultNamespaceContext nsContext = new DefaultNamespaceContext();
    private boolean addKeyValueForEncryptedKey = true;
        
    public XMLSecurityDataFormat() {
        this.xmlCipherAlgorithm = XMLCipher.TRIPLEDES;
        // set a default pass phrase as its required
        this.passPhrase = DEFAULT_KEY.getBytes();
        this.secureTag = "";
        this.secureTagContents = true;

        // Set ignoreLineBreaks to true
        boolean wasSet = false;
        try {
            // Don't override if it was set explicitly
            wasSet = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String lineBreakPropName = "org.apache.xml.security.ignoreLineBreaks";
                    if (System.getProperty(lineBreakPropName) == null) {
                        System.setProperty(lineBreakPropName, "true");
                        return false;
                    }
                    return true; 
                }
            });
        } catch (Throwable t) { //NOPMD
            //ignore
        }
        org.apache.xml.security.Init.init();
        if (!wasSet) {
            try {
                AccessController.doPrivileged(new PrivilegedExceptionAction<Boolean>() {
                    public Boolean run() throws Exception {
                        Field f = XMLUtils.class.getDeclaredField("ignoreLineBreaks");
                        f.setAccessible(true);
                        f.set(null, Boolean.TRUE);
                        return false;
                    }
                });
            } catch (Throwable t) { //NOPMD
                //ignore
            }
        }
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setNamespaces(namespaces);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, byte[] passPhrase) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, byte[] passPhrase) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
        this.setNamespaces(namespaces);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, byte[] passPhrase, 
                                 String xmlCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setPassPhrase(passPhrase);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
    }
    
    /**
     * @deprecated  use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, String)} or
     *                  {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead. 
     */
    @Deprecated
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String xmlCipherAlgorithm, 
            String keyCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
    }
            
    /**
     * @deprecated  use {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, String)} or
     *                  {@link #XMLSecurityDataFormat(String, Map, boolean, String, String, String, KeyStoreParameters)} instead.  
     */
    @Deprecated
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
    }
      
    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias, 
                                 String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParametersId(keyOrTrustStoreParametersId);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId, String keyPassword) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParametersId(keyOrTrustStoreParametersId);
        this.setKeyPassword(keyPassword);
    }

    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        if (null != keyOrTrustStoreParametersId && !keyOrTrustStoreParametersId.equals("")) {
            this.keyOrTrustStoreParametersId = keyOrTrustStoreParametersId;
        }
    }

    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, String keyOrTrustStoreParametersId, String keyPassword) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        if (null != keyOrTrustStoreParametersId && !keyOrTrustStoreParametersId.equals("")) {
            this.keyOrTrustStoreParametersId = keyOrTrustStoreParametersId;
        }
        this.setKeyPassword(keyPassword);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
    }

    public XMLSecurityDataFormat(String secureTag, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        this.setKeyPassword(keyPassword);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
    }

    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        this.setKeyPassword(keyPassword);
    }
    
    public XMLSecurityDataFormat(String secureTag, Map<String, String> namespaces, boolean secureTagContents, String recipientKeyAlias, 
            String xmlCipherAlgorithm, String keyCipherAlgorithm, KeyStoreParameters keyOrTrustStoreParameters, String keyPassword,
            String digestAlgorithm) {
        this();
        this.setSecureTag(secureTag);
        this.setSecureTagContents(secureTagContents);
        this.setXmlCipherAlgorithm(xmlCipherAlgorithm);
        this.setRecipientKeyAlias(recipientKeyAlias);
        this.setKeyCipherAlgorithm(keyCipherAlgorithm);
        this.setNamespaces(namespaces);
        this.setKeyOrTrustStoreParameters(keyOrTrustStoreParameters);
        this.setKeyPassword(keyPassword);
        this.setDigestAlgorithm(digestAlgorithm);
    }

    @Override
    public String getDataFormatName() {
        return "secureXML";
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
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

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    /**
     * Sets missing properties that are defined in the Camel context.
     * @deprecated  this operation populates the data format using depreciated properties and will be
     *              removed at the end of the deprecation period
     */
    @Deprecated
    private void setDefaultsFromContext(CamelContext context) throws Exception {

        Map<String, String> contextProps = context.getProperties();
               
        if (this.recipientKeyAlias == null) {
            recipientKeyAlias = context.getProperty(XML_ENC_RECIPIENT_ALIAS);
        }

        if (this.trustStore == null && contextProps.containsKey(XML_ENC_TRUST_STORE_URL)) {
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            URL trustStoreUrl = new URL(context.getProperty(XML_ENC_TRUST_STORE_URL));
            if (trustStorePassword == null) {
                trustStorePassword = context.getProperty(XML_ENC_TRUST_STORE_PASSWORD);
            }
            trustStore.load(trustStoreUrl.openStream(), trustStorePassword.toCharArray());
        }
        
        if (this.keyStore == null && contextProps.containsKey(XML_ENC_KEY_STORE_URL)) {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            URL keyStoreUrl = new URL(context.getProperty(XML_ENC_KEY_STORE_URL));
            if (keyStorePassword == null) {
                keyStorePassword = context.getProperty(XML_ENC_KEY_STORE_PASSWORD);
            }
            keyStore.load(keyStoreUrl.openStream(), keyStorePassword.toCharArray());    
        }
        
        if (context.getProperties().containsKey(XML_ENC_KEY_STORE_ALIAS) && this.recipientKeyAlias == null) {
            recipientKeyAlias = context.getProperty(XML_ENC_KEY_STORE_ALIAS);
        }
    }
    
    
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        // Retrieve the message body as input stream
        InputStream is = exchange.getContext().getTypeConverter().mandatoryConvertTo(InputStream.class, graph);
        // and covert that to XML
        Document document = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, is);
        
        if (null != keyCipherAlgorithm 
            && (keyCipherAlgorithm.equals(XMLCipher.RSA_v1dot5) || keyCipherAlgorithm.equals(XMLCipher.RSA_OAEP)
                || keyCipherAlgorithm.equals(XMLCipher.RSA_OAEP_11))) {
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
        
        if (trustStore == null && null != this.keyOrTrustStoreParameters) {
            trustStore = keyOrTrustStoreParameters.createKeyStore();
            trustStorePassword = keyOrTrustStoreParameters.getPassword();
        }

        if (null == trustStore) {
            throw new IllegalStateException("A trust store must be defined for asymmetric key encryption.");
        }
        
        String password = 
            this.keyPassword != null ? this.keyPassword : this.trustStorePassword;
        Key keyEncryptionKey = getPublicKey(this.trustStore, exchangeRecipientAlias, password);
        
        if (null == keyEncryptionKey) {
            throw new IllegalStateException("No key for the alias [ " + exchangeRecipientAlias 
                + " ] exists in " + "the configured trust store.");
        }
        
        Key dataEncryptionKey = generateDataEncryptionKey();
        
        XMLCipher keyCipher;
        if (null != this.getKeyCipherAlgorithm()) {
            keyCipher = XMLCipher.getInstance(this.getKeyCipherAlgorithm(), null, digestAlgorithm);
        } else {
            keyCipher = XMLCipher.getInstance(XMLCipher.RSA_OAEP, null, digestAlgorithm);
        }
        
        keyCipher.init(XMLCipher.WRAP_MODE, keyEncryptionKey);
        encrypt(exchange, document, stream, dataEncryptionKey, keyCipher, keyEncryptionKey);
    }
     
    private void encryptSymmetric(Exchange exchange, Document document, OutputStream stream) throws Exception {
        Key keyEncryptionKey;
        Key dataEncryptionKey;
        if (xmlCipherAlgorithm.equals(XMLCipher.TRIPLEDES)) {
            keyEncryptionKey = generateKeyEncryptionKey("DESede");
            dataEncryptionKey = generateDataEncryptionKey();
        } else if (xmlCipherAlgorithm.equals(XMLCipher.SEED_128)) {
            keyEncryptionKey = generateKeyEncryptionKey("SEED");
            dataEncryptionKey = generateDataEncryptionKey();
        } else if (xmlCipherAlgorithm.contains("camellia")) {
            keyEncryptionKey = generateKeyEncryptionKey("CAMELLIA");
            dataEncryptionKey = generateDataEncryptionKey();
        } else {
            keyEncryptionKey = generateKeyEncryptionKey("AES");
            dataEncryptionKey = generateDataEncryptionKey();
        } 
        
        XMLCipher keyCipher = XMLCipher.getInstance(generateXmlCipherAlgorithmKeyWrap());
        keyCipher.init(XMLCipher.WRAP_MODE, keyEncryptionKey);
        
        encrypt(exchange, document, stream, dataEncryptionKey, keyCipher, keyEncryptionKey);
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
        java.security.cert.Certificate cert = keystore.getCertificate(alias);
        if (cert != null) {
            // Get public key
            return cert.getPublicKey();
        }
        return keystore.getKey(alias, password.toCharArray());
    }
 
    
    private void encrypt(Exchange exchange, Document document, OutputStream stream, Key dataEncryptionKey, 
                         XMLCipher keyCipher, Key keyEncryptionKey) throws Exception {
        XMLCipher xmlCipher = XMLCipher.getInstance(xmlCipherAlgorithm);
        xmlCipher.init(XMLCipher.ENCRYPT_MODE, dataEncryptionKey);

        if (secureTag.equalsIgnoreCase("")) {
            embedKeyInfoInEncryptedData(document, keyCipher, xmlCipher, dataEncryptionKey, keyEncryptionKey);
            document = xmlCipher.doFinal(document, document.getDocumentElement());
        } else {
                          
            XPathBuilder xpathBuilder = new XPathBuilder(secureTag);
            xpathBuilder.setNamespaceContext(getNamespaceContext());
            NodeList nodeList = xpathBuilder.evaluate(exchange, NodeList.class);
            
            
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                document = node.getOwnerDocument();
                embedKeyInfoInEncryptedData(node.getOwnerDocument(), keyCipher, xmlCipher, 
                                            dataEncryptionKey, keyEncryptionKey);
                Document temp = xmlCipher.doFinal(node.getOwnerDocument(), (Element) node, getSecureTagContents());
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
        InputStream is = exchange.getIn().getMandatoryBody(InputStream.class);
        return unmarshal(exchange, is);
    }
    
    
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        Document encodedDocument = exchange.getContext().getTypeConverter().convertTo(Document.class, exchange, stream);        
        
        if (null != keyCipherAlgorithm 
            && (keyCipherAlgorithm.equals(XMLCipher.RSA_v1dot5) || keyCipherAlgorithm.equals(XMLCipher.RSA_OAEP)
                || keyCipherAlgorithm.equals(XMLCipher.RSA_OAEP_11))) {
            return decodeWithAsymmetricKey(exchange, encodedDocument);
        } else {
            LOG.debug("No (known) asymmetric keyCipherAlgorithm specified. Attempting to "
                      + "decrypt using a symmetric key"); 
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

        Object ret = null;
        try {
            ret = decode(exchange, encodedDocument, keyEncryptionKey, true);
        } catch (org.apache.xml.security.encryption.XMLEncryptionException ex) {
            if (ex.getMessage().equals("encryption.nokey")) {
                //the message don't have EncryptionKey, try key directly
                ret = decode(exchange, encodedDocument, keyEncryptionKey, false);
            } else {
                throw ex;
            }
        }
        return  ret;
    }
    
    private Object decodeWithAsymmetricKey(Exchange exchange, Document encodedDocument) throws Exception { 
        
        if (keyStore == null && null != keyOrTrustStoreParameters) {
            keyStore = keyOrTrustStoreParameters.createKeyStore();
            keyStorePassword = keyOrTrustStoreParameters.getPassword();
        }
        
        if (this.keyStore == null) {
            throw new IllegalStateException("A key store must be defined for asymmetric key decryption.");
        }
        
        Key keyEncryptionKey = getPrivateKey(this.keyStore, this.recipientKeyAlias, 
                 this.keyPassword != null ? this.keyPassword : this.keyStorePassword);
        Object ret = null;
        try {
            ret = decode(exchange, encodedDocument, keyEncryptionKey, true);
        } catch (org.apache.xml.security.encryption.XMLEncryptionException ex) {
            if (ex.getMessage().equals("encryption.nokey")) {
                //the message don't have EncryptionKey, try key directly
                ret = decode(exchange, encodedDocument, keyEncryptionKey, false);
            } else {
                throw ex;
            }
        }
        return  ret;
    }
    
    private Object decode(Exchange exchange, Document encodedDocument, Key keyEncryptionKey,
                          boolean hasEncrytionKey) throws Exception {
        XMLCipher xmlCipher = XMLCipher.getInstance();
        xmlCipher.setSecureValidation(true);
        if (hasEncrytionKey) {
            xmlCipher.init(XMLCipher.DECRYPT_MODE, null);
            xmlCipher.setKEK(keyEncryptionKey);
        } else {
            xmlCipher.init(XMLCipher.DECRYPT_MODE, keyEncryptionKey);
        }

        if (secureTag.equalsIgnoreCase("")) {
            checkEncryptionAlgorithm(keyEncryptionKey, encodedDocument.getDocumentElement());
            encodedDocument = xmlCipher.doFinal(encodedDocument, encodedDocument.getDocumentElement());
        } else {

            XPathBuilder xpathBuilder = new XPathBuilder(secureTag);
            xpathBuilder.setNamespaceContext(getNamespaceContext());
            NodeList nodeList = xpathBuilder.evaluate(exchange, NodeList.class);


            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                encodedDocument = node.getOwnerDocument();
                if (getSecureTagContents()) {
                    checkEncryptionAlgorithm(keyEncryptionKey, (Element)node);
                    Document temp = xmlCipher.doFinal(encodedDocument, (Element) node, true);
                    encodedDocument.importNode(temp.getDocumentElement().cloneNode(true), true);
                } else {
                    NodeList childNodes = node.getChildNodes();
                    for (int j = 0; j < childNodes.getLength(); j++) {
                        Node childNode = childNodes.item(j);
                        if (childNode.getLocalName().equals("EncryptedData")) {
                            checkEncryptionAlgorithm(keyEncryptionKey, (Element) childNode);
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
            } else if (algorithm.equalsIgnoreCase("SEED")) { 
                secretKey = new SecretKeySpec(passPhrase, "SEED");
            } else if (algorithm.equalsIgnoreCase("CAMELLIA")) { 
                secretKey = new SecretKeySpec(passPhrase, "CAMELLIA");
            } else {
                secretKey = new SecretKeySpec(passPhrase, "AES");
            }
            
            if (Arrays.equals(passPhrase, DEFAULT_KEY.getBytes())) {
                LOG.warn("Using the default encryption key is not secure");
            }
        } catch (InvalidKeyException e) {
            throw new InvalidKeyException("InvalidKeyException due to invalid passPhrase: " + Arrays.toString(passPhrase));
        } catch (NoSuchAlgorithmException e) {
            throw new NoSuchAlgorithmException("NoSuchAlgorithmException while using algorithm: " + algorithm);
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
        
            if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_128)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_128_GCM)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.SEED_128)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.CAMELLIA_128)) {
                keyGenerator.init(128);
            } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_192)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_192_GCM)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.CAMELLIA_192)) {
                keyGenerator.init(192);
            } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_256)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_256_GCM)
                || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.CAMELLIA_256)) {
                keyGenerator.init(256);
            }
        }
        return keyGenerator.generateKey();
    }

    private void embedKeyInfoInEncryptedData(Document document, XMLCipher keyCipher, 
                                             XMLCipher xmlCipher, Key dataEncryptionkey,
                                             Key keyEncryptionKey) 
        throws XMLEncryptionException {

        EncryptedKey encryptedKey = keyCipher.encryptKey(document, dataEncryptionkey, mgfAlgorithm, null);
        if (addKeyValueForEncryptedKey && keyEncryptionKey instanceof PublicKey) {
            KeyInfo keyInfo = new KeyInfo(document);
            keyInfo.add((PublicKey)keyEncryptionKey);
            encryptedKey.setKeyInfo(keyInfo);
        }
        
        KeyInfo keyInfo = new KeyInfo(document);
        keyInfo.add(encryptedKey);    
        EncryptedData encryptedDataElement = xmlCipher.getEncryptedData();
        encryptedDataElement.setKeyInfo(keyInfo);
    }

    private String generateXmlCipherAlgorithmKeyWrap() {
        String algorithmKeyWrap = null;
        if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.TRIPLEDES)) {
            algorithmKeyWrap = XMLCipher.TRIPLEDES_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_128)
            || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_128_GCM)) {
            algorithmKeyWrap = XMLCipher.AES_128_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_192)
            || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_192_GCM)) {
            algorithmKeyWrap = XMLCipher.AES_192_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_256)
            || xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.AES_256_GCM)) {
            algorithmKeyWrap = XMLCipher.AES_256_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.SEED_128)) {
            algorithmKeyWrap = XMLCipher.SEED_128_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.CAMELLIA_128)) {
            algorithmKeyWrap = XMLCipher.CAMELLIA_128_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.CAMELLIA_192)) {
            algorithmKeyWrap = XMLCipher.CAMELLIA_192_KeyWrap;
        } else if (xmlCipherAlgorithm.equalsIgnoreCase(XMLCipher.CAMELLIA_256)) {
            algorithmKeyWrap = XMLCipher.CAMELLIA_256_KeyWrap;
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
    
    // Check to see if the asymmetric key transport algorithm is allowed
    private void checkEncryptionAlgorithm(Key keyEncryptionKey, Element parentElement) throws Exception {
        if (XMLCipher.RSA_v1dot5.equals(keyCipherAlgorithm)
            || keyCipherAlgorithm == null
            || !(keyEncryptionKey instanceof PrivateKey)) {
            // This only applies for Asymmetric Encryption
            return;
        }
        Element encryptedElement = findEncryptedDataElement(parentElement);
        if (encryptedElement == null) {
            return;
        }
        
        // The EncryptedKey EncryptionMethod algorithm
        String foundEncryptedKeyMethod = findEncryptedKeyMethod(encryptedElement);
        if (XMLCipher.RSA_v1dot5.equals(foundEncryptedKeyMethod)) {
            String errorMessage = "The found key transport encryption method is not allowed"; 
            throw new XMLEncryptionException(errorMessage);
        }
    }
    
    private Element findEncryptedDataElement(Element element) {
        // First check the Element itself
        if (EncryptionConstants._TAG_ENCRYPTEDDATA.equals(element.getLocalName())
            && EncryptionConstants.EncryptionSpecNS.equals(element.getNamespaceURI())) {
            return element;
        }
        
        // Now check the child nodes
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element)child;
                if (EncryptionConstants._TAG_ENCRYPTEDDATA.equals(childElement.getLocalName())
                    && EncryptionConstants.EncryptionSpecNS.equals(childElement.getNamespaceURI())) {
                    return childElement;
                }
            }
            child = child.getNextSibling();
        }
        
        return null;
    }
    
    private String findEncryptionMethod(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element)child;
                if (EncryptionConstants._TAG_ENCRYPTIONMETHOD.equals(childElement.getLocalName())
                    && EncryptionConstants.EncryptionSpecNS.equals(childElement.getNamespaceURI())) {
                    return childElement.getAttributeNS(null, EncryptionConstants._ATT_ALGORITHM);
                }
            }
            child = child.getNextSibling();
        }
        
        return null;
    }
    
    private String findEncryptedKeyMethod(Element element) {
        Node child = element.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element)child;
                if (Constants._TAG_KEYINFO.equals(childElement.getLocalName())
                    && Constants.SignatureSpecNS.equals(childElement.getNamespaceURI())) {
                    Node keyInfoChild = child.getFirstChild();
                    while (keyInfoChild != null) {
                        if (child.getNodeType() == Node.ELEMENT_NODE) {
                            childElement = (Element)keyInfoChild;
                            if (EncryptionConstants._TAG_ENCRYPTEDKEY.equals(childElement.getLocalName())
                                && EncryptionConstants.EncryptionSpecNS.equals(childElement.getNamespaceURI())) {
                                return findEncryptionMethod(childElement);
                            }
                        }
                        keyInfoChild = keyInfoChild.getNextSibling();
                    }
                }
            }
            child = child.getNextSibling();
        }
        
        return null;
    }
     
    private DefaultNamespaceContext getNamespaceContext() {
        return this.nsContext;
    };

    public String getXmlCipherAlgorithm() {
        return xmlCipherAlgorithm;
    }

    public void setXmlCipherAlgorithm(String xmlCipherAlgorithm) {
        this.xmlCipherAlgorithm = xmlCipherAlgorithm;
    }
    
    @Deprecated
    public String getKeyCyperAlgorithm() {
        return keyCipherAlgorithm;
    }
    
    public String getKeyCipherAlgorithm() {
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
    
    /**
     * Gets the KeyStore configured for this data format.
     * @deprecated  Will change to private access in the future.
     */
    @Deprecated
    public KeyStore getKeyStore() {
        if (keyStore == null && this.keyOrTrustStoreParameters != null) {
            try {
                keyStore = keyOrTrustStoreParameters.createKeyStore();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create KeyStore with configured KeyStoreParameters. " + e.getMessage(), e);
            }
        }
        return this.keyStore;
    }
    
    /**
     * @deprecated  Use {@link #getKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated  
    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }
         
    /**
     * @deprecated  Will change to private access in the future. Use {@link #getKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated
    public KeyStore getTrustStore() {
        if (trustStore == null && this.keyOrTrustStoreParameters != null) {
            try {
                trustStore = keyOrTrustStoreParameters.createKeyStore();
            } catch (Exception e) {
                throw new RuntimeException("Unable to create KeyStore with configured KeyStoreParameters. " + e.getMessage(), e);
            }
        }
        return this.trustStore;
    }
    
    /**
     * @deprecated  Use {@link #setKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated  
    public void setTrustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
    }
      
    /**
     * @deprecated  Will change to private access in the future. Use {@link #getKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated
    public String getKeyStorePassword() {
        if (this.keyOrTrustStoreParameters != null) {
            return keyOrTrustStoreParameters.getPassword();
        }
        return this.keyStorePassword;
    }
    
    /**
     * @deprecated  Use {@link #setKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated
    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }
    
    /**
     * @deprecated  Will change to private access in the future.  Use {@link #setKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated
    public String getTrustStorePassword() {
        if (this.keyOrTrustStoreParameters != null) {
            return keyOrTrustStoreParameters.getPassword();
        }
        return this.trustStorePassword;
    }
    
    /**
     * @deprecated  Use {@link #setKeyOrTrustStoreParameters()} instead.
     */
    @Deprecated
    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }
    
    public void setKeyOrTrustStoreParameters(KeyStoreParameters parameters) {
        this.keyOrTrustStoreParameters = parameters;
    }
    
    public KeyStoreParameters getKeyOrTrustStoreParameters() {
        return this.keyOrTrustStoreParameters;
    }
    
    public void setKeyOrTrustStoreParametersId(String registryId) {
        this.keyOrTrustStoreParametersId = registryId;
        if (camelContext != null) {
            Object parametersObj = camelContext.getRegistry().lookupByName(this.keyOrTrustStoreParametersId);
            if (parametersObj instanceof KeyStoreParameters) {
                this.keyOrTrustStoreParameters = (KeyStoreParameters)parametersObj;
            } else {
                throw new IllegalStateException("Could not initialize XMLSecurityDataFormat with camelContext." 
                        + "The id for the keyOrTrustStoreParameters specified [ " + keyOrTrustStoreParametersId 
                        + " ] does not identify a KeyStoreParameters bean.");
            }
        } 
    }
    
    public String getKeyOrTrustStoreParametersId() {
        return this.keyOrTrustStoreParametersId;
    }

    public void setNamespaces(Map<String, String> namespaces) {
        getNamespaceContext().setNamespaces(namespaces);
    }
    
    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public String getMgfAlgorithm() {
        return mgfAlgorithm;
    }

    public void setMgfAlgorithm(String mgfAlgorithm) {
        this.mgfAlgorithm = mgfAlgorithm;
    }

    public boolean isAddKeyValueForEncryptedKey() {
        return addKeyValueForEncryptedKey;
    }

    public void setAddKeyValueForEncryptedKey(boolean addKeyValueForEncryptedKey) {
        this.addKeyValueForEncryptedKey = addKeyValueForEncryptedKey;
    }

}
