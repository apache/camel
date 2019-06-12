package org.apache.camel.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoReflectionInstanceTest {

    @Parameters
    public static List<String> data() {
        //@formatter:off
        return Arrays.asList(
            "org.apache.camel.util.CamelVersionHelper",
            "org.apache.camel.util.CastUtils",
            "org.apache.camel.util.CollectionHelper",
            "org.apache.camel.util.concurrent.LockHelper",
            "org.apache.camel.util.concurrent.ThreadHelper",
            "org.apache.camel.util.FilePathResolver",
            "org.apache.camel.util.FileUtil",
            "org.apache.camel.util.function.Bindings",
            "org.apache.camel.util.function.Predicates",
            "org.apache.camel.util.function.Suppliers",
            "org.apache.camel.util.function.ThrowingHelper",
            "org.apache.camel.util.HostUtils",
            "org.apache.camel.util.InetAddressUtil",
            "org.apache.camel.util.IntrospectionSupport",
            "org.apache.camel.util.IOHelper",
            "org.apache.camel.util.ObjectHelper",
            "org.apache.camel.util.OgnlHelper",
            "org.apache.camel.util.PackageHelper",
            "org.apache.camel.util.ReflectionHelper",
            "org.apache.camel.util.SedaConstants",
            "org.apache.camel.util.StreamUtils",
            "org.apache.camel.util.StringHelper",
            "org.apache.camel.util.StringQuoteHelper",
            "org.apache.camel.util.TimeUtils",
            "org.apache.camel.util.UnitUtils",
            "org.apache.camel.util.UnsafeUriCharactersEncoder",
            "org.apache.camel.util.URISupport"
        );
        //@formatter:on
    }

    private String className;

    public NoReflectionInstanceTest(String className) {
        this.className = className;
    }

    @Test(expected = InvocationTargetException.class)
    public void testNoReflectionInstanceForUtilities() throws Exception {
        createInstanceByReflection(className);
        System.err.printf(">>> Utility class '%s' can be instantiated\n", className);

    }

    private void createInstanceByReflection(String className) throws Exception {
        Class<?> fooClazz = Class.forName(className);
        Constructor<?> constructor = fooClazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

}
