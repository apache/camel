package org.apache.camel.graalvm.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.graalvm.CamelRuntime;
import org.apache.camel.graalvm.Reflection;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.xbean.finder.ClassFinder;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;

public class CamelFeature implements Feature {

    private void allowInstantiate(Class cl) {
        RuntimeReflection.register(cl);
        for (Constructor c : cl.getConstructors()) {
            RuntimeReflection.register(c);
        }
    }

    private void allowMethods(Class cl) {
        for (Method method : cl.getMethods()) {
            RuntimeReflection.register(method);
        }
    }

    private void allowMethod(Method method) {
        RuntimeReflection.register(method);
    }

    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            ClassFinder finder = new ClassFinder(CamelRuntime.class.getClassLoader());

            finder.findAnnotatedClasses(Reflection.class).forEach(this::allowInstantiate);
            finder.findAnnotatedMethods(Reflection.class).forEach(this::allowMethod);

            finder.findImplementations(Component.class).forEach(this::allowInstantiate);
            finder.findImplementations(Language.class).forEach(this::allowInstantiate);
            finder.findImplementations(DataFormat.class).forEach(this::allowInstantiate);
            finder.findImplementations(Endpoint.class).forEach(this::allowMethods);
            finder.findImplementations(Consumer.class).forEach(this::allowMethods);
            finder.findImplementations(Producer.class).forEach(this::allowMethods);

            allowInstantiate(org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory.class);
            allowMethods(org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory.class);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
