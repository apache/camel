package org.apache.camel.maven.packaging.dsl.component;

import java.util.Objects;
import java.util.Set;

import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;

/**
 * Used to generate the main ComponentBuilderFactory which is the main entry of the DSLs.
 * E.g: ComponentBuilderFactory.kafka().setBrokers("{{host:port}}").build()
 */
public class ComponentBuilderFactoryGenerator {
    private static final String CLASS_NAME = "ComponentBuilderFactory";

    private final String packageName;
    private final Set<ComponentModel> componentModels;
    private JavaClass javaClass;

    private ComponentBuilderFactoryGenerator(final Set<ComponentModel> componentModels, final ClassLoader classLoader, final String packageName) {
        this.componentModels = componentModels;
        this.packageName = packageName;

        javaClass = new JavaClass(classLoader);
        // generate java class
        generateJavaClass();
    }

    public static ComponentBuilderFactoryGenerator generateClass(final Set<ComponentModel> componentModels, final ClassLoader classLoader, final String packageName) {
        Objects.requireNonNull(componentModels);
        Objects.requireNonNull(classLoader);
        Objects.requireNonNull(packageName);

        return new ComponentBuilderFactoryGenerator(componentModels, classLoader, packageName);
    }

    public String printClassAsString() {
        return javaClass.printClass(true);
    }

    @Override
    public String toString() {
        return printClassAsString();
    }

    public String getGeneratedClassName() {
        return CLASS_NAME;
    }

    private void generateJavaClass() {
        setPackage();
        setBuilderFactoryClassNameAndType();
        setComponentsDslMethods();
    }

    private void setPackage() {
        javaClass.setPackage(packageName);
    }

    private void setBuilderFactoryClassNameAndType() {
        javaClass.setClass(false)
                .setPublic()
                .setName(getGeneratedClassName());
    }

    private void setComponentsDslMethods() {
        componentModels.forEach(componentModel -> {
            final String returnType = packageName + ".dsl." + ComponentDslGenerator.getExpectedGeneratedClassName(componentModel) + "."
                    + ComponentDslInnerBuilderGenerator.getExpectedGeneratedInterfaceName(componentModel);

            final Method componentEntryMethod = javaClass.addMethod();

            componentEntryMethod.setStatic()
                    .setReturnType(returnType)
                    .setName(DslHelper.toCamelCaseLower(componentModel.getScheme()))
                    .setBody(
                            String.format("return %s.dsl.%s.%s();", packageName,
                                    ComponentDslGenerator.getExpectedGeneratedClassName(componentModel),
                                    DslHelper.toCamelCaseLower(componentModel.getScheme())));
        });
    }
}
