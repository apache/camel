package org.apache.camel.component.kubernetes.properties;

import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConfigMapMountPropertiesFunctionTest extends KubernetesTestSupport {

    @Test
    @Order(1)
    public void configMapMountPropertiesFunction() throws Exception {
        ConfigMapPropertiesFunction cmf = new ConfigMapPropertiesFunction();
        cmf.setMountPathConfigMaps("src/test/resources/");
        cmf.setCamelContext(context);
        cmf.start();

        String out = cmf.apply("myconfig/foo");
        Assertions.assertEquals("456", out);

        out = cmf.apply("myconfig/unknown");
        Assertions.assertNull(out);

        out = cmf.apply("myconfig/unknown:444");
        Assertions.assertEquals("444", out);

        out = cmf.apply("myconfig/bar");
        Assertions.assertEquals("Jacks Bar", out);

        cmf.stop();
    }

}
