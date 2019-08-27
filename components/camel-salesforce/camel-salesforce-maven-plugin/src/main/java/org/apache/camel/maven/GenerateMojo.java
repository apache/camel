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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.PickListValue;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.support.IntrospectionSupport;
import org.apache.camel.util.StringHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

/**
 * Goal to generate DTOs for Salesforce SObjects
 */
@Mojo(name = "generate", requiresProject = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateMojo extends AbstractSalesforceMojo {
    public class GeneratorUtility {

        private Stack<String> stack;
        private final Map<String, AtomicInteger> varNames = new HashMap<>();

        public String current() {
            return stack.peek();
        }

        public String enumTypeName(final String sObjectName, final String name) {
            return sObjectName + "_" + (name.endsWith("__c") ? name.substring(0, name.length() - 3) : name) + "Enum";
        }

        public List<SObjectField> externalIdsOf(final String name) {
            return descriptions.externalIdsOf(name);
        }

        public String getEnumConstant(final String value) {

            // TODO add support for supplementary characters
            final StringBuilder result = new StringBuilder();
            boolean changed = false;
            if (!Character.isJavaIdentifierStart(value.charAt(0))) {
                result.append("_");
                changed = true;
            }
            for (final char c : value.toCharArray()) {
                if (Character.isJavaIdentifierPart(c)) {
                    result.append(c);
                } else {
                    // replace non Java identifier character with '_'
                    result.append('_');
                    changed = true;
                }
            }

            return changed ? result.toString().toUpperCase() : value.toUpperCase();
        }

        public String getFieldType(final SObjectDescription description, final SObjectField field) {
            // check if this is a picklist
            if (isPicklist(field)) {
                if (Boolean.TRUE.equals(useStringsForPicklists)) {
                    if (picklistsEnumToSObject.containsKey(description.getName()) && picklistsEnumToSObject.get(description.getName()).contains(field.getName())) {
                        return enumTypeName(description.getName(), field.getName());
                    }
                    return String.class.getName();
                } else if (picklistsStringToSObject.containsKey(description.getName()) && picklistsStringToSObject.get(description.getName()).contains(field.getName())) {
                    return String.class.getName();
                }

                // use a pick list enum, which will be created after generating
                // the SObject class
                return enumTypeName(description.getName(), field.getName());
            } else if (isMultiSelectPicklist(field)) {
                if (Boolean.TRUE.equals(useStringsForPicklists)) {
                    if (picklistsEnumToSObject.containsKey(description.getName()) && picklistsEnumToSObject.get(description.getName()).contains(field.getName())) {
                        return enumTypeName(description.getName(), field.getName()) + "[]";
                    }
                    return String.class.getName() + "[]";
                } else if (picklistsStringToSObject.containsKey(description.getName()) && picklistsStringToSObject.get(description.getName()).contains(field.getName())) {
                    return String.class.getName() + "[]";
                }

                // use a pick list enum array, enum will be created after
                // generating the SObject class
                return enumTypeName(description.getName(), field.getName()) + "[]";
            } else {
                // map field to Java type
                final String soapType = field.getSoapType();
                final String lookupType = soapType.substring(soapType.indexOf(':') + 1);
                final String type = types.get(lookupType);
                if (type == null) {
                    getLog().warn(String.format("Unsupported field type `%s` in field `%s` of object `%s`", soapType, field.getName(), description.getName()));
                    getLog().debug("Currently known types:\n " + types.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n")));
                }
                return type;
            }
        }

        public String getLookupRelationshipName(final SObjectField field) {
            return StringHelper.notEmpty(field.getRelationshipName(), "relationshipName", field.getName());
        }

        public List<PickListValue> getUniqueValues(final SObjectField field) {
            if (field.getPicklistValues().isEmpty()) {
                return field.getPicklistValues();
            }
            final List<PickListValue> result = new ArrayList<>();
            final Set<String> literals = new HashSet<>();
            for (final PickListValue listValue : field.getPicklistValues()) {
                final String value = listValue.getValue();
                if (!literals.contains(value)) {
                    literals.add(value);
                    result.add(listValue);
                }
            }
            literals.clear();
            Collections.sort(result, (o1, o2) -> o1.getValue().compareTo(o2.getValue()));
            return result;
        }

        public boolean hasExternalIds(final String name) {
            return descriptions.hasExternalIds(name);
        }

        public boolean atLeastOneHasExternalIds(final List<String> names) {
            return names.stream().anyMatch(n -> descriptions.hasExternalIds(n));
        }

        public boolean hasMultiSelectPicklists(final SObjectDescription desc) {
            for (final SObjectField field : desc.getFields()) {
                if (isMultiSelectPicklist(field)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasPicklists(final SObjectDescription desc) {
            for (final SObjectField field : desc.getFields()) {
                if (isPicklist(field)) {
                    return true;
                }
            }
            return false;
        }

        public boolean includeList(final List<?> list, final String propertyName) {
            return !list.isEmpty() && !BLACKLISTED_PROPERTIES.contains(propertyName);
        }

        public boolean isBlobField(final SObjectField field) {
            final String soapType = field.getSoapType();
            return BASE64BINARY.equals(soapType.substring(soapType.indexOf(':') + 1));
        }

        public boolean isExternalId(final SObjectField field) {
            return field.isExternalId();
        }

        public boolean isLookup(final SObjectField field) {
            return "reference".equals(field.getType());
        }

        public boolean isMultiSelectPicklist(final SObjectField field) {
            return MULTIPICKLIST.equals(field.getType());
        }

        public boolean isPicklist(final SObjectField field) {
            return PICKLIST.equals(field.getType());
        }

        public boolean isPrimitiveOrBoxed(final Object object) {
            final Class<?> clazz = object.getClass();

            final boolean isWholeNumberWrapper = Byte.class.equals(clazz) || Short.class.equals(clazz) || Integer.class.equals(clazz) || Long.class.equals(clazz);

            final boolean isFloatingPointWrapper = Double.class.equals(clazz) || Float.class.equals(clazz);

            final boolean isWrapper = isWholeNumberWrapper || isFloatingPointWrapper || Boolean.class.equals(clazz) || Character.class.equals(clazz);

            final boolean isPrimitive = clazz.isPrimitive();

            return isPrimitive || isWrapper;
        }

        public boolean notBaseField(final String name) {
            return !BASE_FIELDS.contains(name);
        }

        public boolean notNull(final Object val) {
            return val != null;
        }

        public void pop() {
            stack.pop();
        }

        public Set<Map.Entry<String, Object>> propertiesOf(final Object object) {
            final Map<String, Object> properties = new TreeMap<>();
            IntrospectionSupport.getProperties(object, properties, null, false);

            final Function<Map.Entry<String, Object>, String> keyMapper = e -> StringUtils.capitalize(e.getKey());
            final Function<Map.Entry<String, Object>, Object> valueMapper = Map.Entry::getValue;
            final BinaryOperator<Object> mergeFunction = (u, v) -> {
                throw new IllegalStateException(String.format("Duplicate key %s", u));
            };
            final Supplier<Map<String, Object>> mapSupplier = LinkedHashMap::new;
            return properties.entrySet().stream().collect(Collectors.toMap(keyMapper, valueMapper, mergeFunction, mapSupplier)).entrySet();
        }

        public void push(final String additional) {
            stack.push(additional);
        }

        public void start(final String initial) {
            stack = new Stack<>();
            stack.push(initial);
            varNames.clear();
        }

        public String variableName(final String given) {
            final String base = StringUtils.uncapitalize(given);

            AtomicInteger counter = varNames.get(base);
            if (counter == null) {
                counter = new AtomicInteger(0);
                varNames.put(base, counter);
            }

            return base + counter.incrementAndGet();
        }
    }

    public static final Map<String, String> DEFAULT_TYPES = defineLookupMap();

    private static final Set<String> BASE_FIELDS = defineBaseFields();

    private static final String BASE64BINARY = "base64Binary";

    private static final List<String> BLACKLISTED_PROPERTIES = Arrays.asList("PicklistValues", "ChildRelationships");

    private static final Pattern FIELD_DEFINITION_PATTERN = Pattern.compile("\\w+\\.{1}\\w+");

    private static final String JAVA_EXT = ".java";
    // used for velocity logging, to avoid creating velocity.log
    private static final Logger LOG = Logger.getLogger(GenerateMojo.class.getName());

    private static final String MULTIPICKLIST = "multipicklist";
    private static final String PACKAGE_NAME_PATTERN = "(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final String PICKLIST = "picklist";
    private static final String SOBJECT_PICKLIST_VM = "/sobject-picklist.vm";
    private static final String SOBJECT_POJO_OPTIONAL_VM = "/sobject-pojo-optional.vm";

    private static final String SOBJECT_POJO_VM = "/sobject-pojo.vm";

    private static final String SOBJECT_QUERY_RECORDS_OPTIONAL_VM = "/sobject-query-records-optional.vm";

    private static final String SOBJECT_QUERY_RECORDS_VM = "/sobject-query-records.vm";

    private static final String UTF_8 = "UTF-8";

    @Parameter
    Map<String, String> customTypes;

    ObjectDescriptions descriptions;

    VelocityEngine engine;

    /**
     * Include Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.includePattern")
    String includePattern;

    /**
     * Location of generated DTO files, defaults to
     * target/generated-sources/camel-salesforce.
     */
    @Parameter(property = "camelSalesforce.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/camel-salesforce")
    File outputDirectory;

    /**
     * Java package name for generated DTOs.
     */
    @Parameter(property = "camelSalesforce.packageName", defaultValue = "org.apache.camel.salesforce.dto")
    String packageName;

    /**
     * Names of specific picklist/multipicklist fields, which should be
     * converted to Enum (default case) if property
     * {@link this#useStringsForPicklists} is set to true. Format:
     * SObjectApiName.FieldApiName (e.g. Account.DataSource)
     */
    @Parameter
    String[] picklistToEnums;

    /**
     * Names of specific picklist/multipicklist fields, which should be
     * converted to String if property {@link this#useStringsForPicklists} is
     * set to false. Format: SObjectApiName.FieldApiName (e.g.
     * Account.DataSource)
     */
    @Parameter
    String[] picklistToStrings;

    @Parameter(property = "camelSalesforce.useStringsForPicklists", defaultValue = "false")
    Boolean useStringsForPicklists;

    /**
     * Exclude Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.excludePattern")
    private String excludePattern;

    /**
     * Do NOT generate DTOs for these Salesforce SObjects.
     */
    @Parameter
    private String[] excludes;

    /**
     * Names of Salesforce SObject for which DTOs must be generated.
     */
    @Parameter
    private String[] includes;

    private final Map<String, Set<String>> picklistsEnumToSObject = new HashMap<>();

    private final Map<String, Set<String>> picklistsStringToSObject = new HashMap<>();

    private final Map<String, String> types = new HashMap<>(DEFAULT_TYPES);

    @Parameter(property = "camelSalesforce.useOptionals", defaultValue = "false")
    private boolean useOptionals;

    void parsePicklistToEnums() {
        parsePicklistOverrideArgs(picklistToEnums, picklistsEnumToSObject);
    }

    void parsePicklistToStrings() {
        parsePicklistOverrideArgs(picklistToStrings, picklistsStringToSObject);
    }

    void processDescription(final File pkgDir, final SObjectDescription description, final GeneratorUtility utility, final Set<String> sObjectNames) throws IOException {
        useStringsForPicklists = useStringsForPicklists == null ? Boolean.FALSE : useStringsForPicklists;

        parsePicklistToEnums();
        parsePicklistToStrings();

        // generate a source file for SObject
        final VelocityContext context = new VelocityContext();
        context.put("packageName", packageName);
        context.put("utility", utility);
        context.put("esc", StringEscapeUtils.class);
        context.put("desc", description);
        context.put("useStringsForPicklists", useStringsForPicklists);

        final String pojoFileName = description.getName() + JAVA_EXT;
        final File pojoFile = new File(pkgDir, pojoFileName);
        context.put("sObjectNames", sObjectNames);
        context.put("descriptions", descriptions);
        try (final Writer writer = new OutputStreamWriter(new FileOutputStream(pojoFile), StandardCharsets.UTF_8)) {
            final Template pojoTemplate = engine.getTemplate(SOBJECT_POJO_VM, UTF_8);
            pojoTemplate.merge(context, writer);
        }

        if (useOptionals) {
            final String optionalFileName = description.getName() + "Optional" + JAVA_EXT;
            final File optionalFile = new File(pkgDir, optionalFileName);
            try (final Writer writer = new OutputStreamWriter(new FileOutputStream(optionalFile), StandardCharsets.UTF_8)) {
                final Template optionalTemplate = engine.getTemplate(SOBJECT_POJO_OPTIONAL_VM, UTF_8);
                optionalTemplate.merge(context, writer);
            }
        }

        // write required Enumerations for any picklists
        for (final SObjectField field : description.getFields()) {
            if (utility.isPicklist(field) || utility.isMultiSelectPicklist(field)) {
                final String enumName = utility.enumTypeName(description.getName(), field.getName());
                final String enumFileName = enumName + JAVA_EXT;
                final File enumFile = new File(pkgDir, enumFileName);

                context.put("field", field);
                context.put("enumName", enumName);
                final Template enumTemplate = engine.getTemplate(SOBJECT_PICKLIST_VM, UTF_8);

                try (final Writer writer = new OutputStreamWriter(new FileOutputStream(enumFile), StandardCharsets.UTF_8)) {
                    enumTemplate.merge(context, writer);
                }
            }
        }

        // write the QueryRecords class
        final String queryRecordsFileName = "QueryRecords" + description.getName() + JAVA_EXT;
        final File queryRecordsFile = new File(pkgDir, queryRecordsFileName);
        final Template queryTemplate = engine.getTemplate(SOBJECT_QUERY_RECORDS_VM, UTF_8);
        try (final Writer writer = new OutputStreamWriter(new FileOutputStream(queryRecordsFile), StandardCharsets.UTF_8)) {
            queryTemplate.merge(context, writer);
        }

        if (useOptionals) {
            // write the QueryRecords Optional class
            final String queryRecordsOptionalFileName = "QueryRecords" + description.getName() + "Optional" + JAVA_EXT;
            final File queryRecordsOptionalFile = new File(pkgDir, queryRecordsOptionalFileName);
            final Template queryRecordsOptionalTemplate = engine.getTemplate(SOBJECT_QUERY_RECORDS_OPTIONAL_VM, UTF_8);
            try (final Writer writer = new OutputStreamWriter(new FileOutputStream(queryRecordsOptionalFile), StandardCharsets.UTF_8)) {
                queryRecordsOptionalTemplate.merge(context, writer);
            }
        }
    }

    @Override
    protected void executeWithClient(final RestClient client) throws MojoExecutionException {
        descriptions = new ObjectDescriptions(client, getResponseTimeout(), includes, includePattern, excludes, excludePattern, getLog());

        engine = createVelocityEngine();

        // make sure we can load both templates
        if (!engine.resourceExists(SOBJECT_POJO_VM) || !engine.resourceExists(SOBJECT_QUERY_RECORDS_VM) || !engine.resourceExists(SOBJECT_POJO_OPTIONAL_VM)
            || !engine.resourceExists(SOBJECT_QUERY_RECORDS_OPTIONAL_VM)) {
            throw new MojoExecutionException("Velocity templates not found");
        }

        // create package directory
        // validate package name
        if (!packageName.matches(PACKAGE_NAME_PATTERN)) {
            throw new MojoExecutionException("Invalid package name " + packageName);
        }
        if (outputDirectory.getAbsolutePath().contains("$")) {
            outputDirectory = new File("generated-sources/camel-salesforce");
        }
        final File pkgDir = new File(outputDirectory, packageName.trim().replace('.', File.separatorChar));
        if (!pkgDir.exists()) {
            if (!pkgDir.mkdirs()) {
                throw new MojoExecutionException("Unable to create " + pkgDir);
            }
        }

        getLog().info("Generating Java Classes...");
        Set<String> sObjectNames = StreamSupport.stream(descriptions.fetched().spliterator(), false).map(d -> d.getName()).collect(Collectors.toSet());
        // generate POJOs for every object description
        final GeneratorUtility utility = new GeneratorUtility();
        for (final SObjectDescription description : descriptions.fetched()) {
            if (Defaults.IGNORED_OBJECTS.contains(description.getName())) {
                continue;
            }
            try {
                processDescription(pkgDir, description, utility, sObjectNames);
            } catch (final IOException e) {
                throw new MojoExecutionException("Unable to generate source files for: " + description.getName(), e);
            }
        }

        getLog().info(String.format("Successfully generated %s Java Classes", descriptions.count() * 2));
    }

    @Override
    protected void setup() {
        if (customTypes != null) {
            types.putAll(customTypes);
        }
    }

    static VelocityEngine createVelocityEngine() {
        // initialize velocity to load resources from class loader and use Log4J
        final Properties velocityProperties = new Properties();
        velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
        velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, LOG.getName());
        final VelocityEngine engine = new VelocityEngine(velocityProperties);

        return engine;
    }

    private static Set<String> defineBaseFields() {
        final Set<String> baseFields = new HashSet<>();
        for (final Field field : AbstractSObjectBase.class.getDeclaredFields()) {
            baseFields.add(field.getName());
        }
        return baseFields;
    }

    private static Map<String, String> defineLookupMap() {
        // create a type map
        // using JAXB mapping, for the most part
        // mapping for tns:ID SOAPtype
        final String[][] typeMap = new String[][] {//
                                                   {"ID", "String"}, //
                                                   {"string", "String"}, //
                                                   {"integer", "java.math.BigInteger"}, //
                                                   {"int", "Integer"}, //
                                                   {"long", "Long"}, //
                                                   {"short", "Short"}, //
                                                   {"decimal", "java.math.BigDecimal"}, //
                                                   {"float", "Float"}, //
                                                   {"double", "Double"}, //
                                                   {"boolean", "Boolean"}, //
                                                   {"byte", "Byte"}, //
                                                   // the blob base64Binary type
                                                   // is mapped to String URL
                                                   // for retrieving
                                                   // the blob
                                                   {"base64Binary", "String"}, //
                                                   {"unsignedInt", "Long"}, //
                                                   {"unsignedShort", "Integer"}, //
                                                   {"unsignedByte", "Short"}, //
                                                   {"dateTime", "java.time.ZonedDateTime"}, //
                                                   {"time", "java.time.OffsetTime"}, //
                                                   {"date", "java.time.LocalDate"}, //
                                                   {"g", "java.time.ZonedDateTime"}, //
                                                   // Salesforce maps any types
                                                   // like string, picklist,
                                                   // reference, etc.
                                                   // to string
                                                   {"anyType", "String"}, //
                                                   {"address", "org.apache.camel.component.salesforce.api.dto.Address"}, //
                                                   {"location", "org.apache.camel.component.salesforce.api.dto.GeoLocation"}, //
                                                   {"RelationshipReferenceTo", "String"}//
        };

        final Map<String, String> lookupMap = new HashMap<>();
        for (final String[] entry : typeMap) {
            lookupMap.put(entry[0], entry[1]);
        }

        return Collections.unmodifiableMap(lookupMap);
    }

    private static void parsePicklistOverrideArgs(final String[] picklists, final Map<String, Set<String>> picklistsToSObject) {
        if (picklists != null && picklists.length > 0) {
            String[] strings;
            for (final String picklist : picklists) {
                if (!FIELD_DEFINITION_PATTERN.matcher(picklist).matches()) {
                    throw new IllegalArgumentException("Invalid format provided for picklistFieldToEnum value - allowed format SObjectName.FieldName");
                }
                strings = picklist.split("\\.");
                picklistsToSObject.putIfAbsent(strings[0], new HashSet<>());
                picklistsToSObject.get(strings[0]).add(strings[1]);
            }
        }
    }

}
