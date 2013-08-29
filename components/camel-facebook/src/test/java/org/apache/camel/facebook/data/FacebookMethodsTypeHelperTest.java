package org.apache.camel.facebook.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

import facebook4j.Facebook;
import org.apache.camel.facebook.config.FacebookEndpointConfiguration;
import org.junit.Test;

/**
 * Test {@link FacebookMethodsTypeHelper}.
 */
public class FacebookMethodsTypeHelperTest {
    @Test
    public void testGetCandidateMethods() throws Exception {
        // TODO
    }

    @Test
    public void testFilterMethods() throws Exception {
        // TODO
    }

    @Test
    public void testGetArguments() throws Exception {
        final Class<?>[] interfaces = Facebook.class.getInterfaces();
        for (Class clazz : interfaces) {
            if (clazz.getName().endsWith("Methods")) {
                // check all methods of this *Methods interface
                for (Method method : clazz.getDeclaredMethods()) {
                    // will throw an exception if can't be found
                    final List<Object> arguments = FacebookMethodsTypeHelper.getArguments(method.getName());
                    final int nArgs = arguments.size() / 2;
                    List<Class> types = new ArrayList<Class>(nArgs);
                    for (int i = 0; i < nArgs; i++) {
                        types.add((Class) arguments.get(2 * i));
                    }
                    assertTrue("Missing parameters for " + method,
                        types.containsAll(Arrays.asList(method.getParameterTypes())));
                }
            }
        }
    }

    @Test
    public void testAllArguments() throws Exception {
        assertFalse("Missing arguments", FacebookMethodsTypeHelper.allArguments().isEmpty());
    }

    @Test
    public void testGetType() throws Exception {
        for (Field field : FacebookEndpointConfiguration.class.getDeclaredFields()) {
            Class expectedType = field.getType();
            final Class actualType = FacebookMethodsTypeHelper.getType(field.getName());
            // test for auto boxing, un-boxing
            if (actualType.isPrimitive()) {
                expectedType = (Class) expectedType.getField("TYPE").get(null);
            } else if (List.class.isAssignableFrom(expectedType) && actualType.isArray()) {
                // skip lists, since they will be converted in invokeMethod()
                expectedType = actualType;
            }
            assertEquals("Missing property " + field.getName(), expectedType, actualType);
        }
    }

    @Test
    public void testConvertToGetMethod() throws Exception {
        assertEquals("Invalid get method name",
            FacebookMethodsType.GET_ACCOUNTS.getName(), FacebookMethodsTypeHelper.convertToGetMethod("accounts"));
    }

    @Test
    public void testConvertToSearchMethod() throws Exception {
        assertEquals("Invalid get method name",
            FacebookMethodsType.SEARCHPOSTS.getName(), FacebookMethodsTypeHelper.convertToSearchMethod("posts"));
    }

}
