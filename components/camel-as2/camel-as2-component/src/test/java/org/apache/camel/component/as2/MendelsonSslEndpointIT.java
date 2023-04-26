package org.apache.camel.component.as2;

import java.nio.charset.Charset;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.as2.api.AS2EncryptionAlgorithm;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.as2.internal.AS2ApiName;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for testing connection to a public 3rd party AS2 server Mendelson. This class gives more info for
 * camel-as2 connectivity to a remote server compared to HTTPS connection to localhost server. Eventually test method(s)
 * will be committed with @Disabled annotation due to they can fail because the mendelson server goes offline or the
 * certificate expires. I assume we don't want a build to fail because of such 3rd party connectivity dependency.
 * Mendelson page: http://mendelson-e-c.com/as2_testserver
 */
public class MendelsonSslEndpointIT extends AbstractAS2ITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MendelsonSslEndpointIT.class);
    private MendelsonCertLoader mendelsonCertLoader;
    private static HostnameVerifier hostnameVerifier;

    private static final String[] SIGNED_RECEIPT_MIC_ALGORITHMS = new String[] { "sha1", "md5" };

    private static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
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
                                              + "IMD+F++:::Message from Camel AS2 via HTTPS'\n"
                                              + "QTY+47:20:EA'\n"
                                              + "ALI+JP'\n"
                                              + "MOA+203:410'\n"
                                              + "PRI+INV:20.5'\n"
                                              + "UNS+S'\n"
                                              + "MOA+39:2137.58'\n"
                                              + "ALC+C+ABG'\n"
                                              + "MOA+8:525'\n"
                                              + "UNT+23+00000000000117'\n"
                                              + "UNZ+1+00000000000778'\n";

    @BeforeAll
    public void setupTest() {
        hostnameVerifier = new NoopHostnameVerifier();
        mendelsonCertLoader = new MendelsonCertLoader();
        mendelsonCertLoader.setupCertificateChain();
        mendelsonCertLoader.setupSslContext();
    }

    @Disabled
    @Test
    public void testCreateEndpointAndSendViaHTTPS() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.start();

        org.apache.http.entity.ContentType contentTypeEdifact
                = org.apache.http.entity.ContentType.create("application/edifact", (Charset) null);

        String methodName = "send";
        AS2ApiName as2ApiNameClient = AS2ApiName.CLIENT;

        AS2Configuration endpointConfiguration = new AS2Configuration();
        endpointConfiguration.setApiName(as2ApiNameClient);
        endpointConfiguration.setMethodName(methodName);
        endpointConfiguration.setRequestUri("/as2/HttpReceiver");
        endpointConfiguration.setSignedReceiptMicAlgorithms(SIGNED_RECEIPT_MIC_ALGORITHMS);

        endpointConfiguration.setAs2MessageStructure(AS2MessageStructure.SIGNED_ENCRYPTED);
        endpointConfiguration.setSigningAlgorithm(AS2SignatureAlgorithm.SHA3_256WITHRSA);
        endpointConfiguration.setEncryptingAlgorithm(AS2EncryptionAlgorithm.DES_EDE3_CBC);
        endpointConfiguration.setSigningCertificateChain(mendelsonCertLoader.getChain());
        endpointConfiguration.setSigningPrivateKey(mendelsonCertLoader.getPrivateKey());
        endpointConfiguration.setEncryptingCertificateChain(mendelsonCertLoader.getChain());

        endpointConfiguration.setAs2Version("1.0");
        endpointConfiguration.setAs2To("mendelsontestAS2");
        endpointConfiguration.setAs2From("mycompanyAS2");
        endpointConfiguration.setEdiMessageType(contentTypeEdifact);
        endpointConfiguration.setFrom("dk2kEdi");
        endpointConfiguration.setSubject("mysubject");
        endpointConfiguration.setSigningAlgorithm(AS2SignatureAlgorithm.MD2WITHRSA);
        endpointConfiguration.setEdiMessageTransferEncoding("7bit");
        endpointConfiguration.setAttachedFileName("from_camel.txt");

        endpointConfiguration.setSslContext(mendelsonCertLoader.getSslContext());
        endpointConfiguration.setHostnameVerifier(hostnameVerifier);

        AS2Component as2Component = new AS2Component();
        as2Component.setCamelContext(camelContext);
        as2Component.setConfiguration(endpointConfiguration);
        as2Component.start();

        AS2Endpoint endpoint = (AS2Endpoint) as2Component
                .createEndpoint("as2://client/send?targetHostName=testas2.mendelson-e-c.com"
                                + "&targetPortNumber=8444&inBody=ediMessage&requestUri=/as2/HttpReceiver" +
                                "&ediMessageContentType=application/edifact" +
                                "&signingAlgorithm=SHA3_256WITHRSA");

        Assertions.assertEquals("mycompanyAS2", endpoint.getAs2From());
        Assertions.assertEquals("mendelsontestAS2", endpoint.getAs2To());
        Assertions.assertEquals("dk2kEdi", endpoint.getFrom());

        Exchange out
                = camelContext.createProducerTemplate().request(endpoint,
                        exchange -> exchange.getIn().setBody(EDI_MESSAGE));
        Throwable cause = out.getException();
        Assertions.assertNull(cause);
        LOG.debug("Sending done, check your message in http://testas2.mendelson-e-c.com:8080/webas2/ Login guest, password guest");
    }
}
