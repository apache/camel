package org.apache.camel.maven.packaging.dsl.component;

import java.util.Objects;

import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;

/**
 * DSL Generator class that generates main component specific builder factory, e.g: KafkaComponentBuilderFactory
 */
public class ComponentDslGenerator {
    private static final String BUILDER_FACTORY_SUFFIX = "BuilderFactory";

    private final ComponentModel componentModel;
    private final String packageName;
    private JavaClass javaClass;
    private ComponentDslInnerBuilderGenerator componentDslInnerBuilderGenerator;
    private ComponentDslInnerImplBuilderGenerator componentDslInnerImplBuilderGenerator;

    private ComponentDslGenerator(final ComponentModel componentModel, final ClassLoader classLoader, final String packageName) {
        this.componentModel = componentModel;
        this.packageName = packageName;

        javaClass = new JavaClass(classLoader);
        // generate java class
        generateJavaClass();
    }

    public static ComponentDslGenerator generateClass(final ComponentModel componentModel, final ClassLoader classLoader, final String componentDslPackageName) {
        Objects.requireNonNull(componentModel);
        Objects.requireNonNull(classLoader);

        return new ComponentDslGenerator(componentModel, classLoader, componentDslPackageName);
    }

    public static String getExpectedGeneratedClassName(final ComponentModel componentModel) {
        return componentModel.getShortJavaType() + BUILDER_FACTORY_SUFFIX;
    }

    public String printClassAsString() {
        return javaClass.printClass(true);
    }

    @Override
    public String toString() {
        return printClassAsString();
    }

    public String getGeneratedClassName() {
        return getExpectedGeneratedClassName(componentModel);
    }

    public ComponentDslInnerBuilderGenerator getComponentDslInnerBuilderGenerator() {
        return componentDslInnerBuilderGenerator;
    }

    public ComponentDslInnerImplBuilderGenerator getComponentDslInnerImplBuilderGenerator() {
        return componentDslInnerImplBuilderGenerator;
    }

    private void generateJavaClass() {
        setPackage();
        setImports();
        setBuilderFactoryClassNameAndType();
        componentDslInnerBuilderGenerator = ComponentDslInnerBuilderGenerator.generateClass(javaClass.addNestedType(), componentModel);
        componentDslInnerImplBuilderGenerator = ComponentDslInnerImplBuilderGenerator.generateClass(javaClass.addNestedType(), componentModel, componentDslInnerBuilderGenerator.getGeneratedInterfaceName());
        setDslEntryMethod(componentDslInnerBuilderGenerator.getGeneratedInterfaceName(), componentDslInnerImplBuilderGenerator.getGeneratedClassName());
    }

    private void setPackage() {
        javaClass.setPackage(packageName + ".dsl");
    }

    private void setImports() {
        javaClass.addImport("org.apache.camel.Component");
        javaClass.addImport(packageName + ".AbstractComponentBuilder");
        javaClass.addImport(packageName + ".ComponentBuilder");
        javaClass.addImport(componentModel.getJavaType());
    }

    private void setBuilderFactoryClassNameAndType() {
        javaClass.setClass(false)
                .setName(getGeneratedClassName());

    }

    private void setDslEntryMethod(final String innerBuilderInterfaceName, final String innerBuilderImplName) {
        javaClass.addMethod()
                .setStatic()
                .setReturnType(innerBuilderInterfaceName)
                .setName(DslHelper.toCamelCaseLower(componentModel.getScheme()))
                .setBody(String.format("return new %s();", innerBuilderImplName));
    }
}
