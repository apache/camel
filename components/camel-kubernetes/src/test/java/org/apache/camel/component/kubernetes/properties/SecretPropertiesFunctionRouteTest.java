package org.apache.camel.component.kubernetes.properties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "kubernetes.test.auth", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host", matches = ".*", disabledReason = "Requires kubernetes"),
        @EnabledIfSystemProperty(named = "kubernetes.test.host.k8s", matches = "true", disabledReason = "Requires kubernetes"),
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecretPropertiesFunctionRouteTest extends KubernetesTestSupport {

    private KubernetesClient client;
    private Secret sec;

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .transform().simple("Connect with {{secret:mysecret/myuser}}:{{secret:mysecret/mypass}}");
            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        ConfigBuilder builder = new ConfigBuilder();
        builder.withOauthToken(authToken);
        builder.withMasterUrl(host);
        client = new DefaultKubernetesClient(builder.build());
        context.getRegistry().bind("KubernetesClient", client);

        Map<String, String> data
                = Map.of("myuser", Base64.getEncoder().encodeToString("scott".getBytes(StandardCharsets.UTF_8)),
                        "mypass", Base64.getEncoder().encodeToString("tiger".getBytes(StandardCharsets.UTF_8)));
        Secret sec = new SecretBuilder().editOrNewMetadata().withName("mysecret").endMetadata().withData(data).build();
        this.sec = client.secrets().createOrReplace(sec);

        return context;
    }

    @Override
    public void tearDown() throws Exception {
        if (client != null && sec != null) {
            try {
                client.secrets().delete(sec);
            } catch (Exception e) {
                // ignore
            }
        }

        super.tearDown();
    }

    @Test
    @Order(1)
    public void secretPropertiesFunction() throws Exception {
        String out = template.requestBody("direct:start", null, String.class);
        Assertions.assertEquals("Connect with scott:tiger", out);
    }

}
