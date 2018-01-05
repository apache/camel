package org.apache.camel.component.as2.api;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import org.apache.camel.component.as2.api.entity.ApplicationEDIFACTEntity;
import org.apache.camel.component.as2.api.entity.MultipartSignedEntity;
import org.apache.camel.component.as2.api.protocol.RequestAS2;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.ParseException;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestDate;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.smime.SMIMECapabilitiesAttribute;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.smime.SMIMECapabilityVector;
import org.bouncycastle.asn1.smime.SMIMEEncryptionKeyPreferenceAttribute;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AS2MessageTest {

    private static final String TARGET_HOST = "localhost";
    private static final int TARGET_PORT = 80;
    private static final String AS2_VERSION = "1.1";
    private static final String USER_AGENT = "Camel AS2 Endpoint";
    private static final String REQUEST_URI = "/";
    private static final String AS2_NAME = "878051556";
    private static final String SUBJECT = "Test Case";
    private static final String FROM = "mrAS@example.org";
    private static final String CLIENT_FQDN = "example.org";

    public static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
            +"UNH+00000000000117+INVOIC:D:97B:UN'\n"
            +"BGM+380+342459+9'\n"
            +"DTM+3:20060515:102'\n"
            +"RFF+ON:521052'\n"
            +"NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT'\n"
            +"NAD+SE+005435656::16++GENERAL WIDGET COMPANY'\n"
            +"CUX+1:USD'\n"
            +"LIN+1++157870:IN'\n"
            +"IMD+F++:::WIDGET'\n"
            +"QTY+47:1020:EA'\n"
            +"ALI+US'\n"
            +"MOA+203:1202.58'\n"
            +"PRI+INV:1.179'\n"
            +"LIN+2++157871:IN'\n"
            +"IMD+F++:::DIFFERENT WIDGET'\n"
            +"QTY+47:20:EA'\n"
            +"ALI+JP'\n"
            +"MOA+203:410'\n"
            +"PRI+INV:20.5'\n"
            +"UNS+S'\n"
            +"MOA+39:2137.58'\n"
            +"ALC+C+ABG'\n"
            +"MOA+8:525'\n"
            +"UNT+23+00000000000117'\n"
            +"UNZ+1+00000000000778'\n";

    private AS2SignedDataGenerator gen;

    @Before
    public void setUp() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        
        // Load keystore
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(new FileInputStream("keystore.pfx"), "CamelsKool".toCharArray()); // TODO remove before checkin
        
        // Get certificate chain, signing certificate, private key and algorithm name
        Certificate[] chain = keystore.getCertificateChain("mailidentitykeys");
        X509Certificate signingCert = (X509Certificate) chain[0];
        PrivateKey privateKey = (PrivateKey)keystore.getKey("mailidentitykeys", "CamelsKool".toCharArray());
        String algorithmName = "DSA".equals(privateKey.getAlgorithm()) ? "SHA1withDSA" : "MD5withRSA";
        
        // Create and populate certificate store.
        JcaCertStore certs = new JcaCertStore(Arrays.asList(chain));

        // Create capabilities vector
        SMIMECapabilityVector capabilities = new SMIMECapabilityVector();
        capabilities.addCapability(SMIMECapability.dES_EDE3_CBC);
        capabilities.addCapability(SMIMECapability.rC2_CBC, 128);
        capabilities.addCapability(SMIMECapability.dES_CBC);

        // Create signing attributes
        ASN1EncodableVector attributes = new ASN1EncodableVector();
        attributes.add(new SMIMEEncryptionKeyPreferenceAttribute(new IssuerAndSerialNumber(new X500Name(signingCert.getIssuerDN().getName()), signingCert.getSerialNumber())));
        attributes.add(new SMIMECapabilitiesAttribute(capabilities));
        
        gen = new AS2SignedDataGenerator();
        gen.addSignerInfoGenerator(new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC").setSignedAttributeGenerator(new AttributeTable(attributes)).build(algorithmName, privateKey, signingCert));
        gen.addCertificates(certs);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void createSignedMessageTest() throws InvalidAS2NameException, ParseException, IOException, HttpException {
        BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest("POST", REQUEST_URI);
        
        // Build Context
        HttpCoreContext httpContext = HttpCoreContext.create();
        HttpHost targetHost = new HttpHost(TARGET_HOST, TARGET_PORT);
        httpContext.setTargetHost(targetHost);
        
        // Add Context attributes
        httpContext.setAttribute(RequestAS2.SUBJECT, SUBJECT);
        httpContext.setAttribute(RequestAS2.FROM, FROM);
        httpContext.setAttribute(RequestAS2.AS2_FROM, AS2_NAME);
        httpContext.setAttribute(RequestAS2.AS2_TO, AS2_NAME);
        
        // Build Processor
        HttpProcessor httpProcessor = HttpProcessorBuilder.create()
                .add(new RequestAS2(AS2_VERSION, CLIENT_FQDN))
                .add(new RequestTargetHost())
                .add(new RequestUserAgent(USER_AGENT))
                .add(new RequestDate())
                .add(new RequestContent())
                .add(new RequestConnControl())
                .add(new RequestExpectContinue(true)).build();


        // Create Application EDIFACT Mime Part
        ApplicationEDIFACTEntity applicationEDIFACTEntity = new ApplicationEDIFACTEntity(EDI_MESSAGE, AS2CharSet.US_ASCII, AS2TransferEncoding.BASE64, false);
        
         // Create Multipart Signed Message Body
        MultipartSignedEntity multipartSignedEntity = new MultipartSignedEntity(applicationEDIFACTEntity, gen, AS2CharSet.US_ASCII, AS2TransferEncoding.BASE64, false, null);
        request.setEntity(multipartSignedEntity);
        
        HttpRequestExecutor httpexecutor = new HttpRequestExecutor();
        httpexecutor.preProcess(request, httpProcessor, httpContext);
        
        Util.printRequest(System.out, request);
    }
    
}
