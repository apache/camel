package org.apache.camel.component.kubernetes.properties;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
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
public class ConfigMapPropertiesFunctionTest extends KubernetesTestSupport {

    @Test
    @Order(1)
    public void configMapPropertiesFunction() throws Exception {
        ConfigBuilder builder = new ConfigBuilder();
        builder.withOauthToken(authToken);
        builder.withMasterUrl(host);

        KubernetesClient client = new DefaultKubernetesClient(builder.build());

        Map<String, String> data = Map.of("foo", "123", "bar", "Moes Bar");
        ConfigMap cm = new ConfigMapBuilder().editOrNewMetadata().withName("myconfig").endMetadata().withData(data).build();
        client.configMaps().createOrReplace(cm);

        try {
            ConfigMapPropertiesFunction cmf = new ConfigMapPropertiesFunction();
            cmf.setClient(client);
            cmf.setCamelContext(context);
            cmf.start();

            String out = cmf.apply("myconfig/foo");
            Assertions.assertEquals("123", out);

            out = cmf.apply("myconfig/unknown");
            Assertions.assertNull(out);

            out = cmf.apply("myconfig/unknown:444");
            Assertions.assertEquals("444", out);

            out = cmf.apply("myconfig/bar");
            Assertions.assertEquals("Moes Bar", out);

            cmf.stop();
        } finally {
            client.configMaps().delete(cm);
        }
    }

}
