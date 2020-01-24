package org.apache.camel.maven.packaging.dsl.component;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;

/**
 * DSL Generator class that generates component implementation of the component builder interface.
 * E.g: class KafkaComponentBuilderImpl extends AbstractComponentBuilder implements KafkaComponentBuilder
 */
public class ComponentDslInnerImplBuilderGenerator {
    private static final String BUILDER_IMPL_SUFFIX = "Impl";


    final private JavaClass javaClass;
    final private ComponentModel componentModel;
    final private String classBuilderName;

    private ComponentDslInnerImplBuilderGenerator(final JavaClass javaClass, final ComponentModel componentModel, final String interfaceBuilderName) {
        this.javaClass = javaClass;
        this.componentModel = componentModel;
        this.classBuilderName = interfaceBuilderName;
        // generate class
        generateJavaClass();
    }

    public static ComponentDslInnerImplBuilderGenerator generateClass(final JavaClass javaClass, final ComponentModel componentModel, final String classBuilderName) {
        return new ComponentDslInnerImplBuilderGenerator(javaClass, componentModel, classBuilderName);
    }

    public String getGeneratedClassName() {
        return classBuilderName + BUILDER_IMPL_SUFFIX;
    }

    private void generateJavaClass() {
        setClassNameAndType();
        setMethods();
    }

    private void setClassNameAndType() {
        javaClass.setName(getGeneratedClassName())
                .setPackagePrivate()
                .setStatic(false)
                .extendSuperType("AbstractComponentBuilder")
                .implementInterface(classBuilderName);
    }

    private void setMethods() {
        javaClass.addMethod()
                .setProtected()
                .setReturnType("Component")
                .setName("buildConcreteComponent")
                .setBody(String.format("return new %s();", componentModel.getShortJavaType()))
                .addAnnotation(Override.class);
    }
}
