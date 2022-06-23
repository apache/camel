package org.apache.camel.component.kubernetes.properties;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
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
public class ConfigMapPropertiesFunctionRouteTest extends KubernetesTestSupport {

    private KubernetesClient client;
    private ConfigMap cm;

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .transform().simple("Hello ${body} we are at {{configmap:myconfig/bar}}");
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

        Map<String, String> data = Map.of("foo", "123", "bar", "Moes Bar");
        ConfigMap cm = new ConfigMapBuilder().editOrNewMetadata().withName("myconfig").endMetadata().withData(data).build();
        this.cm = client.configMaps().createOrReplace(cm);

        return context;
    }

    @Override
    public void tearDown() throws Exception {
        if (client != null && cm != null) {
            try {
                client.configMaps().delete(cm);
            } catch (Exception e) {
                // ignore
            }
        }

        super.tearDown();
    }

    @Test
    @Order(1)
    public void configMapPropertiesFunction() throws Exception {
        String out = template.requestBody("direct:start", "Jack", String.class);
        Assertions.assertEquals("Hello Jack we are at Moes Bar", out);
    }

}
