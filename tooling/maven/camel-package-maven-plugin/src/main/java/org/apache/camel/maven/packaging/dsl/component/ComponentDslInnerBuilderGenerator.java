package org.apache.camel.maven.packaging.dsl.component;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;
import org.apache.commons.lang3.StringUtils;

public class ComponentDslInnerBuilderGenerator {
    private static final String BUILDER_SUFFIX = "Builder";

    final JavaClass javaClass;
    final ComponentModel componentModel;

    private ComponentDslInnerBuilderGenerator(final JavaClass javaClass, final ComponentModel componentModel) {
        this.javaClass = javaClass;
        this.componentModel = componentModel;
        // generate class
        generateJavaClass();
    }

    public static ComponentDslInnerBuilderGenerator generateInnerClass(final JavaClass javaClass, final ComponentModel componentModel) {
        return new ComponentDslInnerBuilderGenerator(javaClass, componentModel);
    }

    public String getClassName() {
        return componentModel.getShortJavaType() + BUILDER_SUFFIX;
    }

    private void generateJavaClass() {
        setClassNameAndType();
        setDefaultMethods();
        setFluentMethodsFromComponentOptions();
    }

    private void setClassNameAndType() {
        javaClass.setName(getClassName())
                .setPublic()
                .setClass(false)
                .extendSuperType("ComponentBuilder");
    }

    private void setDefaultMethods() {
        final Method method = javaClass.addMethod();
        method.setDefault()
                .setReturnType(getClassName())
                .setName("withComponentName")
                .addParameter(String.class, "name")
                .setBody(
                        "doSetComponentName(name);",
                        "return this;"
                );
    }

    private void setFluentMethodsFromComponentOptions() {
        componentModel.getComponentOptions().forEach(componentOptionModel -> {
            final Method method = javaClass.addMethod();
            method.setDefault()
                    .setReturnType(getClassName())
                    .setName("set" + StringUtils.capitalize(componentOptionModel.getName()))
                    .addParameter(componentOptionModel.getJavaType(), componentOptionModel.getName())
                    .setBody(
                            String.format("doSetProperty(\"%s\", %s);", componentOptionModel.getName(), componentOptionModel.getName()),
                            "return this;"
                    );
        });
    }
}
