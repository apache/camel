package org.apache.camel.component.kubernetes.properties;

import org.apache.camel.component.kubernetes.KubernetesTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecretMountPropertiesFunctionTest extends KubernetesTestSupport {

    @Test
    @Order(1)
    public void secretMountPropertiesFunction() throws Exception {
        SecretPropertiesFunction cmf = new SecretPropertiesFunction();
        cmf.setMountPathSecrets("src/test/resources/");
        cmf.setCamelContext(context);
        cmf.start();

        String out = cmf.apply("mysecret/myuser");
        Assertions.assertEquals("donald", out);

        out = cmf.apply("mysecret/unknown");
        Assertions.assertNull(out);

        out = cmf.apply("mysecret/unknown:444");
        Assertions.assertEquals("444", out);

        out = cmf.apply("mysecret/mypass");
        Assertions.assertEquals("seCre!t", out);

        cmf.stop();
    }

}
