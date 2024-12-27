package org.apache.camel.component.mllp;

import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit.rule.mllp.MllpClientResource;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;


public class MllpSSLContextParametersTest extends CamelTestSupport {

    @RegisterExtension
    public MllpClientResource mllpClient = new MllpClientResource();

    @EndpointInject("mock://result")
    MockEndpoint result;

    public SSLContextParameters createSslContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("password");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("password");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        mllpClient.setMllpHost("localhost");
        mllpClient.setMllpPort(AvailablePortFinder.getNextAvailable());

        DefaultCamelContext context = (DefaultCamelContext) super.createCamelContext();

        context.setUseMDCLogging(true);
        context.getCamelContextExtension().setName(this.getClass().getSimpleName());

        SSLContextParameters sslContextParameters = createSslContextParameters();
        context.getRegistry().bind("sslContextParameters", sslContextParameters);

        return context;
    }


    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            String routeId = "mllp-ssl-sender";

            public void configure() {
                fromF("mllp://%d?sslContextParameters=#sslContextParameters", mllpClient.getMllpPort())
                        .log(LoggingLevel.INFO, routeId, "Received Message: ${body}")
                        .to(result);
            }
        };
    }

    @Test
    public void testSSLInOutWithMllpConsumer() throws Exception, MllpSocketException {

        String hl7Message =
                "MSH|^~\\&|CLIENT|TEST|SERVER|ACK|20231118120000||ADT^A01|123456|T|2.6\r" +
                "EVN|A01|20231118120000\r" +
                "PID|1|12345|67890||DOE^JOHN||19800101|M|||123 Main St^^Springfield^IL^62704||(555)555-5555|||||S\r" +
                "PV1|1|O\r";

        result.expectedBodiesReceived(hl7Message);

        String endpointUri = String.format("mllp://%s:%d?sslContextParameters=#sslContextParameters",
                mllpClient.getMllpHost(), mllpClient.getMllpPort());
        template.sendBody(endpointUri, hl7Message);
        result.assertIsSatisfied();

    }

}
