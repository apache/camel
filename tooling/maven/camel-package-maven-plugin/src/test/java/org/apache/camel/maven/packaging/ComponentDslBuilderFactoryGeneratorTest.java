package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.dsl.component.ComponentDslBuilderFactoryGenerator;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.PackageHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.tooling.util.PackageHelper.loadText;

class ComponentDslBuilderFactoryGeneratorTest {

    @Test
    public void testIfCreateJavaClassCorrectly() throws IOException {
        final String json = PackageHelper.loadText(new File(Objects.requireNonNull(getClass().getClassLoader().getResource("json/test_component.json")).getFile()));
        final ComponentModel componentModel = ComponentModel.generateComponentModelFromJsonString(json);

        final ComponentDslBuilderFactoryGenerator componentDslBuilderFactoryGenerator = ComponentDslBuilderFactoryGenerator.generateClass(componentModel, getClass().getClassLoader(), "org.apache.camel.builder.component.dsl");

        System.out.println(componentDslBuilderFactoryGenerator.toString());
    }

    @Test
    public void testFiles() throws Exception {
        Map<File, Supplier<String>> files = PackageHelper.findJsonFiles(new File("/Users/oalsafi/Work/Apache/camel/components"), p -> p.isDirectory() || p.getName().endsWith(".json")).values().stream()
                .collect(Collectors.toMap(Function.identity(), s -> cache(() -> loadJson(s))));
        System.out.println(files);
    }

    private static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private static <T> Supplier<T> cache(Supplier<T> supplier) {
        return new Supplier<T>() {
            T value;

            @Override
            public T get() {
                if (value == null) {
                    value = supplier.get();
                }
                return value;
            }
        };
    }
}