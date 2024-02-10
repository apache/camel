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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.CodeBlock;
import com.squareup.kotlinpoet.FileSpec;
import com.squareup.kotlinpoet.FunSpec;
import com.squareup.kotlinpoet.KModifier;
import com.squareup.kotlinpoet.LambdaTypeName;
import com.squareup.kotlinpoet.ParameterSpec;
import com.squareup.kotlinpoet.ParameterizedTypeName;
import com.squareup.kotlinpoet.PropertySpec;
import com.squareup.kotlinpoet.TypeName;
import com.squareup.kotlinpoet.TypeNames;
import com.squareup.kotlinpoet.TypeSpec;
import com.squareup.kotlinpoet.WildcardTypeName;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.text.CaseUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

@Mojo(
      name = "generate-all-dsl-kotlin",
      threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class AllDslKotlinMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}")
    protected File baseDir;

    @Parameter(defaultValue = "org.apache.camel.kotlin.components")
    protected String componentsPackageName;

    @Parameter(defaultValue = "org.apache.camel.kotlin.dataformats")
    protected String dataFormatsPackageName;

    @Parameter(defaultValue = "org.apache.camel.kotlin.languages")
    protected String languagesPackageName;

    @Parameter
    protected File sourcesOutputDir;

    @Parameter
    protected File outputResourcesDir;

    @Parameter(defaultValue = "${project.basedir}/../../catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components")
    protected File componentsJsonDir;

    @Parameter(defaultValue = "${project.basedir}/../../catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/dataformats")
    protected File dataFormatsJsonDir;

    @Parameter(defaultValue = "${project.basedir}/../../catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/languages")
    protected File languagesJsonDir;

    private transient String licenseHeader;

    private static final Predicate<String> identifierPattern = Pattern.compile("\\w+").asMatchPredicate();

    private static final Pattern genericPattern = Pattern.compile("<([\\w.]+)>");

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        baseDir = project.getBasedir();
        componentsPackageName = "org.apache.camel.kotlin.components";
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File camelDir = findCamelDirectory(baseDir, "dsl/camel-kotlin-api");
        if (camelDir == null) {
            getLog().debug("No dsl/camel-kotlin-api folder found, skipping execution");
            return;
        }
        Path root = camelDir.toPath();
        if (sourcesOutputDir == null) {
            sourcesOutputDir = root.resolve("src/generated/kotlin").toFile();
        }
        if (outputResourcesDir == null) {
            outputResourcesDir = root.resolve("src/generated/resources").toFile();
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt")) {
            this.licenseHeader = loadText(is);
        } catch (Exception e) {
            throw new MojoFailureException("Error loading license-header-java.txt file", e);
        }

        ClassLoader classLoader = constructClassLoaderForCamelProjects(
                "core/camel-core-model", "core/camel-api");

        executeLanguages(classLoader);
        executeDataFormats(classLoader);
        executeComponents();
    }

    // --- Languages DSL ---

    private void executeLanguages(ClassLoader classLoader) throws MojoFailureException {
        List<LanguageModel> models = new ArrayList<>();

        for (File file : languagesJsonDir.listFiles()) {
            try {
                LanguageModel result = JsonMapper.generateLanguageModel(Files.readString(file.toPath()));
                models.add(result);
            } catch (IOException e) {
                throw new MojoFailureException("Error while reading language from catalog", e);
            }
        }

        for (LanguageModel model : models) {
            createLanguageDsl(model, classLoader);
        }
    }

    private void createLanguageDsl(LanguageModel model, ClassLoader classLoader) throws MojoFailureException {
        String name = model.getName();
        getLog().debug("Generating Language DSL for " + name);
        String pascalCaseName = toPascalCase(name);
        String dslClassName = pascalCaseName + "LanguageDsl";
        Class<?> clazz;
        try {
            clazz = classLoader.loadClass(model.getModelJavaType());
        } catch (ClassNotFoundException e) {
            throw new MojoFailureException("Error while discovering class", e);
        }

        String funName = name;
        if (name.contains("-") || name.contains("+")) {
            funName = "`" + name + "`";
        }
        ClassName language = ClassName.bestGuess(model.getModelJavaType());

        FunSpec.Builder funBuilder = FunSpec.builder(funName);
        funBuilder.addParameter(funName, TypeNames.STRING);
        funBuilder.addParameter(ParameterSpec
                .builder("i", LambdaTypeName.get(
                        new ClassName(languagesPackageName, dslClassName),
                        new ArrayList<>(),
                        TypeNames.UNIT))
                .defaultValue("{}")
                .build());
        funBuilder.addCode("""
                val def = %s(%s)
                %s(def).apply(i)
                return def
                """.formatted(language.getSimpleName(), funName, dslClassName));
        funBuilder.returns(language);

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(dslClassName);
        typeBuilder.addAnnotation(new ClassName("org.apache.camel.kotlin", "CamelDslMarker"));
        typeBuilder.primaryConstructor(FunSpec.constructorBuilder()
                .addParameter("def", language)
                .build());
        typeBuilder.addProperty("def", language);
        typeBuilder.addInitializerBlock(CodeBlock.of("this.def = def\n"));

        for (LanguageModel.LanguageOptionModel property : model.getOptions()) {
            String propertyName = property.getName();
            if (propertyName.equals("expression")) {
                continue;
            }
            Field field = FieldUtils.getField(clazz, propertyName, true);
            if (field == null) {
                MojoFailureException ex = new MojoFailureException(
                        "Not found field %s for class %s".formatted(propertyName, clazz.getCanonicalName()));
                getLog().error(ex);
                throw ex;
            }
            TypeName javaType = parseJavaType(property.getJavaType());

            if (field.getType().equals(String.class) && !javaType.equals(TypeNames.STRING)) {
                appendPropertyBuilder(typeBuilder, propertyName, javaType, true);
                appendPropertyBuilder(typeBuilder, propertyName, TypeNames.STRING, false);
            } else if (field.getType().equals(Class.class)) {
                TypeName clazzTypeName = ParameterizedTypeName.get(
                        new ClassName("java.lang", "Class"),
                        WildcardTypeName.producerOf(TypeNames.ANY));
                appendPropertyBuilder(typeBuilder, propertyName, clazzTypeName, false);
            } else {
                appendPropertyBuilder(typeBuilder, propertyName, javaType, false);
            }
        }

        writeSource(
                FileSpec.builder(languagesPackageName, model.getName())
                        .addFunction(funBuilder.build())
                        .addType(typeBuilder.build()),
                dslClassName, languagesPackageName, "Language DSL for " + name);
    }

    // --- DataFormat DSL ---

    private void executeDataFormats(ClassLoader classLoader) throws MojoFailureException {
        List<DataFormatModel> models = new ArrayList<>();

        for (File file : dataFormatsJsonDir.listFiles()) {
            try {
                DataFormatModel result = JsonMapper.generateDataFormatModel(Files.readString(file.toPath()));
                models.add(result);
            } catch (IOException e) {
                throw new MojoFailureException("Error while reading dataformat from catalog", e);
            }
        }

        for (DataFormatModel model : models) {
            createDataFormatDsl(model, classLoader);
        }
    }

    private void createDataFormatDsl(DataFormatModel model, ClassLoader classLoader) throws MojoFailureException {
        String name = model.getName();
        getLog().debug("Generating DataFormat DSL for " + name);
        String pascalCaseName = toPascalCase(name);
        String dslClassName = pascalCaseName + "DataFormatDsl";
        Class<?> clazz;
        try {
            clazz = classLoader.loadClass(model.getModelJavaType());
        } catch (ClassNotFoundException e) {
            throw new MojoFailureException("Error while discovering class", e);
        }

        String funName = name;
        if (name.contains("-") || name.contains("+")) {
            funName = "`" + name + "`";
        }
        ClassName dataFormatDsl = new ClassName("org.apache.camel.kotlin", "DataFormatDsl");
        ClassName dataFormat = ClassName.bestGuess(model.getModelJavaType());

        FunSpec.Builder funBuilder = FunSpec.builder(funName);
        funBuilder.receiver(dataFormatDsl);
        funBuilder.addParameter("i", LambdaTypeName.get(
                new ClassName(dataFormatsPackageName, dslClassName),
                new ArrayList<>(),
                TypeNames.UNIT));
        funBuilder.addCode("def = %s().apply(i).def".formatted(dslClassName));

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(dslClassName);
        typeBuilder.addAnnotation(new ClassName("org.apache.camel.kotlin", "CamelDslMarker"));
        typeBuilder.addProperty("def", dataFormat);
        typeBuilder.addInitializerBlock(CodeBlock.of("def = %s()".formatted(dataFormat.getSimpleName())));

        for (DataFormatModel.DataFormatOptionModel property : model.getOptions()) {
            String propertyName = extractPropertyName(model, property);
            Field field = FieldUtils.getField(clazz, propertyName, true);
            if (field == null) {
                MojoFailureException ex = new MojoFailureException(
                        "Not found field %s for class %s".formatted(propertyName, clazz.getCanonicalName()));
                getLog().error(ex);
                throw ex;
            }
            TypeName javaType = parseJavaType(property.getJavaType());

            if (field.getType().equals(String.class) && !javaType.equals(TypeNames.STRING)) {
                appendPropertyBuilder(typeBuilder, propertyName, javaType, true);
                appendPropertyBuilder(typeBuilder, propertyName, TypeNames.STRING, false);
            } else if (field.getType().equals(Class.class)) {
                TypeName clazzTypeName = ParameterizedTypeName.get(
                        new ClassName("java.lang", "Class"),
                        WildcardTypeName.producerOf(TypeNames.ANY));
                appendPropertyBuilder(typeBuilder, propertyName, clazzTypeName, false);
            } else {
                appendPropertyBuilder(typeBuilder, propertyName, javaType, false);
            }
        }

        writeSource(
                FileSpec.builder(dataFormatsPackageName, model.getName())
                        .addFunction(funBuilder.build())
                        .addType(typeBuilder.build()),
                dslClassName, dataFormatsPackageName, "DataFormat DSL for " + name);
    }

    private String extractPropertyName(DataFormatModel model, DataFormatModel.DataFormatOptionModel property) {
        if (model.getModelName().equals("yaml") && property.getName().equals("typeFilter")) {
            return "typeFilters";
        }
        return property.getName();
    }

    // --- Endpoint DSL ---

    private void executeComponents() throws MojoFailureException {
        List<ComponentModel> models = new ArrayList<>();

        for (File file : componentsJsonDir.listFiles()) {
            BaseModel<?> model = JsonMapper.generateModel(file.toPath());
            models.add((ComponentModel) model);
        }

        executeComponent(models);
    }

    private void executeComponent(List<ComponentModel> allModels) throws MojoFailureException {
        if (allModels.isEmpty()) {
            return;
        }
        if (getLog().isDebugEnabled()) {
            getLog().debug("Found " + allModels.size() + " components");
        }
        Map<String, List<ComponentModel>> grModels
                = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));

        for (List<ComponentModel> compModels : grModels.values()) {
            if (compModels.size() > 1) {
                compModels.sort((o1, o2) -> {
                    String s1 = o1.getScheme();
                    String s2 = o2.getScheme();
                    String as = o1.getAlternativeSchemes();
                    int i1 = as.indexOf(s1);
                    int i2 = as.indexOf(s2);
                    return Integer.compare(i1, i2);
                });
            }

            ComponentModel model = compModels.get(0);
            createEndpointDsl(model, compModels);
        }
    }

    private void createEndpointDsl(ComponentModel model, List<ComponentModel> aliases) throws MojoFailureException {
        String name = model.getName();
        getLog().debug("Generating Endpoint DSL for " + name);
        String pascalCaseName = toPascalCase(name);
        String dslClassName = pascalCaseName + "UriDsl";

        String funName = name;
        if (name.contains("-") || name.contains("+")) {
            funName = "`" + name + "`";
        }
        ClassName uriDsl = new ClassName("org.apache.camel.kotlin", "UriDsl");

        FunSpec.Builder funBuilder = FunSpec.builder(funName);
        funBuilder.receiver(uriDsl);
        funBuilder.addParameter("i", LambdaTypeName.get(
                new ClassName(componentsPackageName, dslClassName),
                new ArrayList<>(),
                TypeNames.UNIT));
        funBuilder.addCode("%s(this).apply(i)".formatted(dslClassName));

        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(dslClassName);
        typeBuilder.addAnnotation(new ClassName("org.apache.camel.kotlin", "CamelDslMarker"));
        typeBuilder.primaryConstructor(FunSpec.constructorBuilder()
                .addParameter(ParameterSpec.builder("it", uriDsl).build())
                .build());
        typeBuilder.addProperty("it", uriDsl, KModifier.PRIVATE);
        typeBuilder.addInitializerBlock(CodeBlock.of("""
                this.it = it
                this.it.component("%s")
                """.formatted(name)));

        processPathOptions(model, typeBuilder);
        processParameterOptions(model, typeBuilder);

        writeSource(
                FileSpec.builder(componentsPackageName, model.getName())
                        .addFunction(funBuilder.build())
                        .addType(typeBuilder.build()),
                dslClassName, componentsPackageName, "Endpoint DSL for " + name);
    }

    private void processPathOptions(ComponentModel model, TypeSpec.Builder typeBuilder) throws MojoFailureException {
        String urlExpression = model.getSyntax();
        urlExpression = urlExpression.replaceFirst(model.getScheme() + ":", "");
        StringBuilder sb = new StringBuilder();
        char[] urlExpressionArray = urlExpression.toCharArray();
        boolean wasIdentifier = false;
        for (int i = 0; i < urlExpressionArray.length; ++i) {
            boolean isIdentifier = identifierPattern.test(String.valueOf(urlExpressionArray[i]));
            if (!wasIdentifier && isIdentifier) {
                boolean parameterExists = false;
                String subUrlExpression = urlExpression.substring(i);
                for (ComponentModel.EndpointOptionModel property : model.getEndpointPathOptions()) {
                    if (subUrlExpression.startsWith(property.getName())) {
                        parameterExists = true;
                        break;
                    }
                }
                if (parameterExists) {
                    sb.append('$');
                }
            }
            sb.append(urlExpressionArray[i]);
            wasIdentifier = isIdentifier;
        }
        urlExpression = sb.toString();

        for (ComponentModel.EndpointOptionModel property : model.getEndpointPathOptions()) {
            PropertySpec.Builder pathPropertyBuilder = PropertySpec.builder(property.getName(), TypeNames.STRING);
            pathPropertyBuilder.mutable(true);
            pathPropertyBuilder.initializer("\"\"");
            pathPropertyBuilder.addModifiers(KModifier.PRIVATE);
            typeBuilder.addProperty(pathPropertyBuilder.build());
            FunSpec.Builder propertyBuilder = FunSpec.builder(property.getName());
            propertyBuilder.addParameter(property.getName(), TypeNames.STRING);
            propertyBuilder.addCode(CodeBlock.of("""
                    this.%s = %s
                    it.url("%s")
                    """.formatted(property.getName(), property.getName(), urlExpression)));
            typeBuilder.addFunction(propertyBuilder.build());
            ClassName className = parsePropertyType(property.getType());
            if (!className.equals(TypeNames.STRING)) {
                FunSpec.Builder stringPropertyBuilder = FunSpec.builder(property.getName());
                stringPropertyBuilder.addParameter(property.getName(), className);
                stringPropertyBuilder.addCode(CodeBlock.of("""
                        this.%s = %s.toString()
                        it.url("%s")
                        """.formatted(property.getName(), property.getName(), urlExpression)));
                typeBuilder.addFunction(stringPropertyBuilder.build());
            }
        }
    }

    private void processParameterOptions(ComponentModel model, TypeSpec.Builder typeBuilder) throws MojoFailureException {
        for (ComponentModel.EndpointOptionModel property : model.getEndpointParameterOptions()) {
            FunSpec.Builder propertyBuilder = FunSpec.builder(property.getName());
            propertyBuilder.addParameter(property.getName(), TypeNames.STRING);
            propertyBuilder.addCode(CodeBlock.of("""
                    it.property("%s", %s)
                    """.formatted(property.getName(), property.getName())));
            typeBuilder.addFunction(propertyBuilder.build());
            ClassName className = parsePropertyType(property.getType());
            if (!className.equals(TypeNames.STRING)) {
                FunSpec.Builder stringPropertyBuilder = FunSpec.builder(property.getName());
                stringPropertyBuilder.addParameter(property.getName(), className);
                stringPropertyBuilder.addCode(CodeBlock.of("""
                        it.property("%s", %s.toString())
                        """.formatted(property.getName(), property.getName())));
                typeBuilder.addFunction(stringPropertyBuilder.build());
            }
        }
    }

    // --- Utils ---

    private ClassName parsePropertyType(String type) throws MojoFailureException {
        ClassName className;
        switch (type) {
            case "string", "object", "duration", "array" -> className = TypeNames.STRING;
            case "integer" -> className = TypeNames.INT;
            case "number" -> className = TypeNames.DOUBLE;
            case "boolean" -> className = TypeNames.BOOLEAN;
            default -> throw new MojoFailureException("Unexpected type of parameter: " + type);
        }
        return className;
    }

    private void writeSource(FileSpec.Builder fileBuilder, String fileName, String packageName, String what)
            throws MojoFailureException {
        StringBuilder codeBuilder = new StringBuilder();
        codeBuilder.append(licenseHeader);
        codeBuilder.append("\n");
        try {
            fileBuilder.build().writeTo(codeBuilder);
        } catch (IOException e) {
            throw new MojoFailureException("Error while appending kotlin code", e);
        }
        String code = codeBuilder.toString();
        String filePath = packageName.replace('.', '/') + "/" + fileName + ".kt";
        Path fullPath = sourcesOutputDir.toPath().resolve(filePath);
        boolean update = true;
        try {
            if (Files.exists(fullPath)) {
                String existingCode = Files.readString(fullPath);
                if (existingCode.equals(code)) {
                    update = false;
                }
            }
        } catch (IOException e) {
            throw new MojoFailureException(e);
        }
        if (update) {
            getLog().info("Updating " + what);
            updateResource(sourcesOutputDir.toPath(), filePath, code);
        }
    }

    private String toPascalCase(String name) {
        return CaseUtils.toCamelCase(name, true, '-', '+');
    }

    private void appendPropertyBuilder(TypeSpec.Builder typeBuilder, String propertyName, TypeName javaType, Boolean toString) {
        FunSpec.Builder propertyBuilder = FunSpec.builder(propertyName);
        propertyBuilder.addParameter(propertyName, javaType);
        String code = "def.%s = %s".formatted(propertyName, propertyName);
        if (toString) {
            code += ".toString()";
        }
        propertyBuilder.addCode(CodeBlock.of(code));
        typeBuilder.addFunction(propertyBuilder.build());
    }

    private TypeName parseJavaType(String javaType) throws MojoFailureException {
        if (javaType.equals(String.class.getCanonicalName())) {
            return TypeNames.STRING;
        }
        if (javaType.equals(Integer.class.getCanonicalName()) || javaType.equals(int.class.getCanonicalName())) {
            return TypeNames.INT;
        }
        if (javaType.equals(Double.class.getCanonicalName()) || javaType.equals(double.class.getCanonicalName())) {
            return TypeNames.DOUBLE;
        }
        if (javaType.equals(Boolean.class.getCanonicalName()) || javaType.equals(boolean.class.getCanonicalName())) {
            return TypeNames.BOOLEAN;
        }
        if (javaType.equals(Long.class.getCanonicalName()) || javaType.equals(long.class.getCanonicalName())) {
            return TypeNames.LONG;
        }
        if (javaType.equals(Character.class.getCanonicalName()) || javaType.equals(char.class.getCanonicalName())) {
            return TypeNames.CHAR;
        }
        if (javaType.equals("byte[]")) {
            return TypeNames.BYTE_ARRAY;
        }
        if (javaType.startsWith(List.class.getCanonicalName())) {
            Matcher matcher = genericPattern.matcher(javaType);
            matcher.find();
            String typeArgument = matcher.group(1);
            return ParameterizedTypeName.get(TypeNames.MUTABLE_LIST, parseJavaType(typeArgument));
        }
        try {
            return ClassName.bestGuess(javaType);
        } catch (Exception e) {
            MojoFailureException ex = new MojoFailureException("Unable to resolve java type: " + javaType, e);
            getLog().error(ex);
            throw ex;
        }
    }

    private ClassLoader constructClassLoaderForCamelProjects(String... projectPaths) throws MojoFailureException {
        URL[] urls = new URL[projectPaths.length];
        for (int i = 0; i < urls.length; ++i) {
            String projectPath = projectPaths[i];
            File buildDirectory = Path.of(baseDir.toPath().toString(), "../../", projectPath, "target/classes").toFile();
            try {
                urls[i] = buildDirectory.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException("Error while resolving build directory of project " + projectPath, e);
            }
        }
        return new URLClassLoader(urls);
    }
}
