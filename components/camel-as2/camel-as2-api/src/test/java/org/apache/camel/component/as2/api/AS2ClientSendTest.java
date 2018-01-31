package org.apache.camel.component.as2.api;


import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AS2ClientSendTest {
    private static final Logger LOG = LoggerFactory.getLogger(AS2ClientSendTest.class);
    
    private static final String AS2_VERSION = "1.1";
    private static final String REQUEST_URI = "/";
    private static final String AS2_NAME = "878051556";
    private static final String SUBJECT = "Test Case";
    private static final String USER_AGENT = "AS2TestClientSend Client";
    private static final String TARGET_HOSTNAME = "localhost";
    private static final String FROM = "mrAS@example.org";
    private static final String CLIENT_FQDN = "example.org";
    private static final int TARGET_PORT = 8888;
    private static final String TARGET_HOST = TARGET_HOSTNAME + ":" + TARGET_PORT;

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
    
    public class RequestHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {

            String content = null;
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
                HttpEntity entity = entityEnclosingRequest.getEntity();
                content = EntityUtils.toString(entity);
            }
            try {
                if (!requestQueue.offer(request, 10, TimeUnit.SECONDS)) {
                    LOG.error("Request not enqueued");
                }
                if (content != null && !contentQueue.offer(content, 10, TimeUnit.SECONDS)) {
                    LOG.error("Content not enqueued");
                }
            } catch (InterruptedException e) {
                LOG.error("Interrupted waiting on request queue", e);
            }
        }
        
    }

    private AS2ServerConnection as2ServerConnection;
    
    private ArrayBlockingQueue<HttpRequest> requestQueue = new ArrayBlockingQueue<HttpRequest>(1);
    private ArrayBlockingQueue<String> contentQueue = new ArrayBlockingQueue<String>(1);
    
    private AS2SignedDataGenerator gen;
    private String algorithmName;
    private Certificate[] chain;
    private PrivateKey privateKey;

    
    @Before
    public void setup() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        
        // Load keystore
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        keystore.load(new FileInputStream("keystore.pfx"), "CamelsKool".toCharArray()); // TODO remove before checkin
        
        chain = keystore.getCertificateChain("mailidentitykeys");
        X509Certificate signingCert = (X509Certificate) chain[0];
        privateKey = (PrivateKey)keystore.getKey("mailidentitykeys", "CamelsKool".toCharArray());
        algorithmName = "DSA".equals(privateKey.getAlgorithm()) ? "SHA1withDSA" : "MD5withRSA";
        
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

        startServer();
    }
    
    @After
    public void tearDown() {
        stopServer();
    }

    @Test
    public void testListen() throws Exception {
        sendTestMessage();
        
        // Retrieve request from server
        HttpRequest request = requestQueue.poll(10, TimeUnit.SECONDS);
        
        // Validate Request Headers
        assertNotNull("Request is null", request);
        assertEquals("Request URI: ", REQUEST_URI, request.getRequestLine().getUri());
        assertEquals(AS2Header.AS2_VERSION + ": ", AS2_VERSION, request.getFirstHeader(AS2Header.AS2_VERSION).getValue());
        assertTrue(AS2Header.CONTENT_TYPE + ": ", request.getFirstHeader(AS2Header.CONTENT_TYPE).getValue().startsWith(AS2MediaType.APPLICATION_EDIFACT));
        assertEquals(AS2Header.AS2_FROM + ": ", AS2_NAME, request.getFirstHeader(AS2Header.AS2_FROM).getValue());
        assertEquals(AS2Header.AS2_TO + ": ", AS2_NAME, request.getFirstHeader(AS2Header.AS2_TO).getValue());
        assertEquals(AS2Header.SUBJECT + ": ", SUBJECT, request.getFirstHeader(AS2Header.SUBJECT).getValue());
        assertThat(AS2Header.MESSAGE_ID + ": ", request.getFirstHeader(AS2Header.MESSAGE_ID).getValue(), containsString(CLIENT_FQDN));
        assertEquals(AS2Header.TARGET_HOST + ": ", TARGET_HOST, request.getFirstHeader(AS2Header.TARGET_HOST).getValue());
        assertEquals(AS2Header.DATE + ": ", USER_AGENT, request.getFirstHeader(AS2Header.USER_AGENT).getValue());
        assertNotNull(AS2Header.DATE + ": ", request.getFirstHeader(AS2Header.DATE));
        assertNotNull(AS2Header.CONTENT_LENGTH + ": ", request.getFirstHeader(AS2Header.CONTENT_LENGTH));
        assertEquals(AS2Header.CONNECTION + ": ", HTTP.CONN_KEEP_ALIVE, request.getFirstHeader(AS2Header.CONNECTION).getValue());
        assertEquals(AS2Header.EXPECT + ": ", HTTP.EXPECT_CONTINUE, request.getFirstHeader(AS2Header.EXPECT).getValue());
        
        // Validate Request Type
        assertThat("Unexpected request type: ", request, instanceOf(HttpEntityEnclosingRequest.class));
        
        // Retrieve content from server 
        String content = contentQueue.poll(10, TimeUnit.SECONDS);
        
        // Validated content
        assertNotNull("Content is null", content);
//        assertEquals("", EDI_MESSAGE, content);
    }
    
    private void startServer() throws IOException {
        as2ServerConnection = new AS2ServerConnection("AS2TestClientSend Server", 8888);
        as2ServerConnection.listen(REQUEST_URI, new RequestHandler());
    }
    
    private void stopServer() {
        as2ServerConnection.stopListening("");
        as2ServerConnection.close();
    }
    
    private void sendTestMessage() throws UnknownHostException, IOException, InvalidAS2NameException, HttpException {
        AS2ClientConnection clientConnection = new AS2ClientConnection(AS2_VERSION, USER_AGENT, CLIENT_FQDN, TARGET_HOSTNAME, TARGET_PORT);
        AS2ClientManager clientManager = new AS2ClientManager(clientConnection);
        
        // Add Context attributes
        HttpCoreContext httpContext = HttpCoreContext.create();
        httpContext.setAttribute(AS2ClientManager.REQUEST_URI, REQUEST_URI);
        httpContext.setAttribute(AS2ClientManager.SUBJECT, SUBJECT);
        httpContext.setAttribute(AS2ClientManager.FROM, FROM);
        httpContext.setAttribute(AS2ClientManager.AS2_FROM, AS2_NAME);
        httpContext.setAttribute(AS2ClientManager.AS2_TO, AS2_NAME);
        httpContext.setAttribute(AS2ClientManager.AS2_MESSAGE_STRUCTURE, AS2MessageStructure.PLAIN);
        httpContext.setAttribute(AS2ClientManager.EDI_MESSAGE_CONTENT_TYPE, ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2CharSet.US_ASCII));
        
        clientManager.send(EDI_MESSAGE, httpContext);
    }
    
}
