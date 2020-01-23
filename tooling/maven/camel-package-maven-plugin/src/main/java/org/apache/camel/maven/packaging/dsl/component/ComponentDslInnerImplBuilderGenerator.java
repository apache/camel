package org.apache.camel.maven.packaging.dsl.component;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;

public class ComponentDslInnerImplBuilderGenerator {
    private static final String BUILDER_SUFFIX = "Builder";
    private static final String BUILDER_IMPL_SUFFIX = "Impl";


    final private JavaClass javaClass;
    final private ComponentModel componentModel;

    private ComponentDslInnerImplBuilderGenerator(final JavaClass javaClass, final ComponentModel componentModel) {
        this.javaClass = javaClass;
        this.componentModel = componentModel;
        // generate class
        generateJavaClass();
    }

    public static ComponentDslInnerImplBuilderGenerator generateClass(final JavaClass javaClass, final ComponentModel componentModel) {
        return new ComponentDslInnerImplBuilderGenerator(javaClass, componentModel);
    }

    public String getBuilderClassName() {
        return componentModel.getShortJavaType() + BUILDER_SUFFIX;
    }

    public String getBuilderImplClassName() {
        return getBuilderClassName() + BUILDER_IMPL_SUFFIX;
    }

    private void generateJavaClass() {
        setClassNameAndType();
        setConstructor();
        setMethods();
    }

    private void setClassNameAndType() {
        javaClass.setName(getBuilderImplClassName())
                .setPackagePrivate()
                .setStatic(false)
                .extendSuperType("AbstractComponentBuilder")
                .implementInterface(getBuilderClassName());
    }

    private void setConstructor() {
        javaClass.addMethod()
                .setConstructor(true)
                .setName(getBuilderImplClassName())
                .setPublic()
                .setBody(String.format("super(\"%s\");", componentModel.getScheme()));
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
