package org.apache.camel.component.huaweicloud.smn.models;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
public class ClientConfigurationsModelTest {
    String[] expectedFields = {"operation",
            "secretKey",
            "authenticationkey",
            "projectId",
            "topicUrn",
            "subject",
            "proxyHost",
            "proxyPort",
            "proxyUser",
            "proxyPassword",
            "serviceEndpoint",
            "messageTtl",
            "ignoreSslVerification"};
    @Test
    public void testClientConfigurationFields(){
        Field[] declaredFields = ClientConfigurations.class.getDeclaredFields();

        Assert.assertEquals(13, declaredFields.length);

        List<String> fieldNameList = Arrays.asList(declaredFields).stream()
                .map(field -> field.getName())
                .collect(Collectors.toList());

        for(String expectedField : expectedFields) {
            Assert.assertTrue(fieldNameList.contains(expectedField));
        }
    }
}
