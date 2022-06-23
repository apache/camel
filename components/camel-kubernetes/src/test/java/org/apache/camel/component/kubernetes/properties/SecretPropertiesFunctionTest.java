package org.apache.camel.component.kubernetes.properties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
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
public class SecretPropertiesFunctionTest extends KubernetesTestSupport {

    @Test
    @Order(1)
    public void secretPropertiesFunction() throws Exception {
        ConfigBuilder builder = new ConfigBuilder();
        builder.withOauthToken(authToken);
        builder.withMasterUrl(host);

        KubernetesClient client = new DefaultKubernetesClient(builder.build());

        Map<String, String> data
                = Map.of("myuser", Base64.getEncoder().encodeToString("scott".getBytes(StandardCharsets.UTF_8)),
                        "mypass", Base64.getEncoder().encodeToString("tiger".getBytes(StandardCharsets.UTF_8)));
        Secret sec = new SecretBuilder().editOrNewMetadata().withName("mysecret").endMetadata().withData(data).build();
        client.secrets().createOrReplace(sec);

        try {
            SecretPropertiesFunction cmf = new SecretPropertiesFunction();
            cmf.setClient(client);
            cmf.setCamelContext(context);
            cmf.start();

            String out = cmf.apply("mysecret/myuser");
            Assertions.assertEquals("scott", out);

            out = cmf.apply("mysecret/unknown");
            Assertions.assertNull(out);

            out = cmf.apply("mysecret/unknown:444");
            Assertions.assertEquals("444", out);

            out = cmf.apply("mysecret/mypass");
            Assertions.assertEquals("tiger", out);

            cmf.stop();
        } finally {
            client.secrets().delete(sec);
        }
    }

}
