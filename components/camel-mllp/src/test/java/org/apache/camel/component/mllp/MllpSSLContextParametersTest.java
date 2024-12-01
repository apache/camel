package org.apache.camel.component.mllp;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.KeyManagersParameters;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MllpSSLContextParametersTest extends CamelTestSupport {

    @BindToRegistry("sslContextParameters")
    public SSLContextParameters createSSLContextParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setResource(this.getClass().getClassLoader().getResource("keystore.jks").toString());
        ksp.setPassword("changeit");

        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyPassword("changeit");
        kmp.setKeyStore(ksp);

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setKeyStore(ksp);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setKeyManagers(kmp);
        sslContextParameters.setTrustManagers(tmp);

        return sslContextParameters;
    }

    @Test
    public void testSSLInOutWithMllpConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // MLLP consumer with SSL
                from("mllp://localhost:8888?sslContextParameters=#sslContextParameters")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) {
                                String receivedMessage = exchange.getMessage().getBody(String.class);
                                exchange.getMessage().setBody("MSH|^~\\&|ACK|SERVER|TEST|CLIENT|20231118120000||ACK^A01|123456|T|2.6\r" +
                                        "MSA|AA|123456");
                            }
                        });
            }
        });
        context.start();

        // HL7 Message to send
        String hl7Message = "MSH|^~\\&|CLIENT|TEST|SERVER|ACK|20231118120000||ADT^A01|123456|T|2.6\r" +
                "PID|1|12345|67890||DOE^JOHN||19800101|M|||123 Main St^^Springfield^IL^62704||(555)555-5555|||||S\r";

        // Send HL7 message and receive response
        String response = template.requestBody(
                "mllp://localhost:8888?sslContextParameters=#sslContextParameters",
                hl7Message,
                String.class);

        // Validate response
        assertEquals("MSH|^~\\&|ACK|SERVER|TEST|CLIENT|20231118120000||ACK^A01|123456|T|2.6\r" +
                "MSA|AA|123456", response);

        context.stop();
    }

}
