package org.apache.camel.maven.packaging;

import java.net.URLClassLoader;
import java.util.Objects;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;

public class ComponentDslGenerator {
    private static final String COMPONENT_DSL_PACKAGE_NAME = "org.apache.camel.builder.component";

    private final ComponentModel componentModel;
    private JavaClass javaClass;
    private ComponentDslGenerator(final ComponentModel componentModel, final ClassLoader classLoader) {
        this.componentModel = componentModel;

        javaClass = new JavaClass(classLoader);
    }

    public static ComponentDslGenerator createFromComponentModel(final ComponentModel componentModel, final ClassLoader classLoader) {
        Objects.requireNonNull(componentModel);
        Objects.requireNonNull(classLoader);

        return new ComponentDslGenerator(componentModel, classLoader);
    }

    private void generateJavaClass() {

    }

    private void setPackage() {
        javaClass.setPackage(COMPONENT_DSL_PACKAGE_NAME + ".dsl");
    }
}
