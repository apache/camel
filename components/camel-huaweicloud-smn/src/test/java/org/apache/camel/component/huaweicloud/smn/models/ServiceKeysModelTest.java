package org.apache.camel.component.huaweicloud.smn.models;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;

public class ServiceKeysModelTest {
    String[] expectedFields = {
            "authenticationKey",
            "secretKey" };

    @Test
    public void testServiceKeysFields() {
        Field[] declaredFields = ServiceKeys.class.getDeclaredFields();

        Assert.assertEquals(2, declaredFields.length);

        List<String> fieldNameList = Arrays.asList(declaredFields).stream()
                .map(field -> field.getName())
                .collect(Collectors.toList());

        for (String expectedField : expectedFields) {
            Assert.assertTrue(fieldNameList.contains(expectedField));
        }
    }
}
