/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.generator.openapi;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Generated;
import javax.lang.model.element.Modifier;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.util.StringHelper.notEmpty;

public class SpringBootProjectSourceCodeGenerator {

    private static final String DEFAULT_INDENT = "    ";

    private String indent = DEFAULT_INDENT;

    private String packageName;

    public void generate(final Path destination) throws IOException {
        final JavaFile javaFile = generateSourceCode();

        javaFile.writeTo(destination);
    }

    public SpringBootProjectSourceCodeGenerator withIndent(final String indent) {
        this.indent = ObjectHelper.notNull(indent, "indent");
        return this;
    }

    public SpringBootProjectSourceCodeGenerator withPackageName(final String packageName) {
        notEmpty(packageName, "packageName");
        this.packageName = packageName;
        return this;
    }

    MethodSpec generateRestMethod() {
        final ClassName req = ClassName.bestGuess("javax.servlet.http.HttpServletRequest");
        final ClassName res = ClassName.bestGuess("javax.servlet.http.HttpServletResponse");

        final AnnotationSpec.Builder reqAnnotation = AnnotationSpec.builder(ClassName.bestGuess("org.springframework.web.bind.annotation.RequestMapping"))
            .addMember("value", "\"/**\"");

        final MethodSpec.Builder forward = MethodSpec.methodBuilder("camelServlet").addModifiers(Modifier.PUBLIC)
            .addParameter(req, "request")
            .addParameter(res, "response")
            .addAnnotation(reqAnnotation.build())
            .returns(void.class);

        forward.addCode("try {\n");
        forward.addCode("    String path = request.getRequestURI();\n");
        forward.addCode("    request.getServletContext().getRequestDispatcher(\"/camel/\" + path).forward(request, response);\n");
        forward.addCode("} catch (Exception e) {\n");
        forward.addCode("    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);\n");
        forward.addCode("}\n");

        return forward.build();
    }

    JavaFile generateSourceCode() {
        notEmpty(packageName, "packageName");

        final MethodSpec methodSpec = generateRestMethod();

        final String classNameToUse = "CamelRestController";

        final AnnotationSpec.Builder generatedAnnotation = AnnotationSpec.builder(Generated.class).addMember("value",
            "$S", getClass().getName());
        final AnnotationSpec.Builder restAnnotation = AnnotationSpec.builder(ClassName.bestGuess("org.springframework.web.bind.annotation.RestController"));

        final TypeSpec.Builder builder = TypeSpec.classBuilder(classNameToUse)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL).addMethod(methodSpec)
            .addAnnotation(generatedAnnotation.build())
            .addAnnotation(restAnnotation.build())
            .addJavadoc("Forward requests to the Camel servlet so it can service REST requests.\n");
        final TypeSpec generatedRestController = builder.build();

        return JavaFile.builder(packageName, generatedRestController).indent(indent).build();
    }

    public static SpringBootProjectSourceCodeGenerator generator() {
        return new SpringBootProjectSourceCodeGenerator();
    }

}
