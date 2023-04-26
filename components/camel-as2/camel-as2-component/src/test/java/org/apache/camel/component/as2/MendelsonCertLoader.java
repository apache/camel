package org.apache.camel.component.as2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.io.IOUtils;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * That's a utility class for preparing Mendelson-specific certificate chain, private key, ssl context
 */
public class MendelsonCertLoader {

    private static final Logger LOG = LoggerFactory.getLogger(MendelsonCertLoader.class);

    private static final String MENDELSON_CERT = "mendelson/key4.cer";
    private static final String MENDELSON_PRIVATE_KEY = "mendelson/key3.pfx";

    private final List<Certificate> chainAsList = new ArrayList<>();

    private PrivateKey privateKey;
    private SSLContext sslContext;

    public void setupSslContext() {
        try {
            InputStream mendelsonPrivateKeyAsStream = getClass().getClassLoader().getResourceAsStream(MENDELSON_PRIVATE_KEY);
            KeyStore keyStore = getKeyStore(mendelsonPrivateKeyAsStream);
            sslContext = SSLContexts.custom().setKeyStoreType("PKCS12")
                    .loadTrustMaterial(keyStore, new TrustAllStrategy())
                    .build();
        } catch (KeyStoreException | IOException | KeyManagementException | NoSuchAlgorithmException e) {
            LOG.error("Failed to configure SSLContext", e);
        }

        if (sslContext == null) {
            throw new IllegalStateException("failed to configure SSL context");
        }
    }

    private KeyStore getKeyStore(InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        String password = "test";
        KeyStore ks;
        try {
            ks = KeyStore.getInstance("PKCS12");
            ks.load(inputStream, password.toCharArray());
            return ks;
        } catch (KeyStoreException e) {
            LOG.error("Failed to create instance of KeyStore", e);
        } catch (CertificateException e) {
            LOG.error("Failed to load KeyStore");
        }
        throw new IllegalStateException("about to return null");
    }

    public void setupCertificateChain() {

        InputStream mendelsonCertAsStream = getClass().getClassLoader().getResourceAsStream(MENDELSON_CERT);
        if (mendelsonCertAsStream == null) {
            //LOG.error("Couldn't read out client certificate as stream.");
            throw new IllegalStateException("Couldn't read out certificate as stream.");
        }

        InputStream mendelsonPrivateKeyAsStream = getClass().getClassLoader().getResourceAsStream(MENDELSON_PRIVATE_KEY);
        if (mendelsonPrivateKeyAsStream == null) {
            //LOG.error("Couldn't read out private key as stream.");
            throw new IllegalStateException("Couldn't read out key storage as stream.");
        }

        try {
            Certificate mendelsonCert = getCertificateFromStream(mendelsonCertAsStream);
            chainAsList.add(mendelsonCert);

            //private key
            privateKey = getPrivateKeyFromPKCSStream(mendelsonPrivateKeyAsStream);

        } catch (IOException e) {
            String errMsg
                    = "Error while trying to load certificate to the key store. IO error when reading a byte array.  " + e;
            LOG.error(errMsg);
        } catch (NoSuchAlgorithmException e) {
            String errMsg = "Error while trying to load certificate to the key store. Requested algorithm isn't found.  " + e;
            LOG.error(errMsg);
        } catch (CertificateException e) {
            String errMsg = "Error while trying to load certificate to the key store. There is a certificate problem.  " + e;
            LOG.error(errMsg);
        } catch (InvalidKeySpecException e) {
            String errMsg = "Can not init private key store  " + e;
            LOG.error(errMsg);
        }
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    public Certificate[] getChain() {
        if (chainAsList.size() > 0) {
            Certificate[] arrayCert = new Certificate[chainAsList.size()];

            for (int i = 0; i < chainAsList.size(); i++) {
                arrayCert[i] = chainAsList.get(i);
            }
            return arrayCert;
        } else {
            return null;
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    private List<Certificate> getCertificatesFromStream(InputStream inputStream) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (List<Certificate>) certificateFactory.generateCertificates(inputStream);
    }

    private Certificate getCertificateFromStream(InputStream inputStream) throws IOException, CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return certificateFactory.generateCertificate(inputStream);
    }

    //https://stackoverflow.com/questions/18644286/creating-privatekey-object-from-pkcs12
    private PrivateKey getPrivateKeyFromPKCSStream(InputStream inputStream)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String password = "test";
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("PKCS12");
        } catch (KeyStoreException e) {
            LOG.error("Error while getting instance of KeyStore" + e);
        }
        try {
            ks.load(inputStream, password.toCharArray());
        } catch (CertificateException e) {
            LOG.error("Error while loading the certificate" + e);
        }
        try {
            return (PrivateKey) ks.getKey(
                    ks.aliases().nextElement(),
                    password.toCharArray());
        } catch (KeyStoreException e) {
            LOG.error("Error while retrieving private key" + e);
        } catch (UnrecoverableKeyException e) {
            LOG.error("Error while retrieving private key" + e);
        }
        throw new IllegalStateException("Failed to construct a PrivateKey from provided InputStream");
    }

    private byte[] getBytesFromPem(InputStream inputStream) throws IOException {
        String privateKeyPEM
                = IOUtils.toString(inputStream, StandardCharsets.UTF_8).replaceAll("-{5}.+-{5}", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(privateKeyPEM);
    }

    private byte[] getBytesFromPKCS12(InputStream inputStream) throws IOException {
        String privateKeyPKCS12 = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        return privateKeyPKCS12.getBytes(StandardCharsets.UTF_8);
    }

}
