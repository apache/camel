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
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.codegen.AbstractSalesforceExecution;
import org.apache.camel.component.salesforce.codegen.GenerateExecution;
import org.apache.camel.component.salesforce.codegen.ObjectDescriptions;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Goal to generate DTOs for Salesforce SObjects
 */
@Mojo(name = "generate", requiresProject = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateMojo extends AbstractSalesforceMojo {
    @Parameter
    Map<String, String> customTypes;

    /**
     * Include Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.includePattern")
    String includePattern;

    /**
     * Location of generated DTO files, defaults to target/generated-sources/camel-salesforce.
     */
    @Parameter(property = "camelSalesforce.outputDirectory",
               defaultValue = "${project.build.directory}/generated-sources/camel-salesforce")
    File outputDirectory;

    /**
     * Java package name for generated DTOs.
     */
    @Parameter(property = "camelSalesforce.packageName", defaultValue = "org.apache.camel.salesforce.dto")
    String packageName;

    /**
     * Suffix for child relationship property name. Necessary if an SObject has a lookup field with the same name as its
     * Child Relationship Name. If setting to something other than default, "List" is a sensible value.
     */
    @Parameter(property = "camelSalesforce.childRelationshipNameSuffix")
    String childRelationshipNameSuffix;

    /**
     * Override picklist enum value generation via a java.util.Properties instance. Property name format:
     * `SObject.FieldName.PicklistValue`. Property value is the desired enum value.
     */
    @Parameter(property = "camelSalesforce.enumerationOverrideProperties")
    Properties enumerationOverrideProperties = new Properties();

    /**
     * Names of specific picklist/multipicklist fields, which should be converted to Enum (default case) if property
     * {@link this#useStringsForPicklists} is set to true. Format: SObjectApiName.FieldApiName (e.g. Account.DataSource)
     */
    @Parameter
    String[] picklistToEnums;

    /**
     * Names of specific picklist/multipicklist fields, which should be converted to String if property
     * {@link this#useStringsForPicklists} is set to false. Format: SObjectApiName.FieldApiName (e.g.
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

    @Parameter(property = "camelSalesforce.useOptionals", defaultValue = "false")
    private boolean useOptionals;

    private final GenerateExecution execution = new GenerateExecution();

    @Override
    protected void setup() {
        super.setup();
        execution.setCustomTypes(customTypes);
        execution.setChildRelationshipNameSuffix(childRelationshipNameSuffix);
        execution.setExcludes(excludes);
        execution.setIncludes(includes);
        execution.setOutputDirectory(outputDirectory);
        execution.setPackageName(packageName);
        execution.setPicklistToEnums(picklistToEnums);
        execution.setPicklistToStrings(picklistToStrings);
        execution.setEnumerationOverrideProperties(enumerationOverrideProperties);
        execution.setUseStringsForPicklists(useStringsForPicklists);
        execution.setExcludePattern(excludePattern);
        execution.setIncludePattern(includePattern);
        execution.setUseOptionals(useOptionals);
        execution.setup();
    }

    @Override
    protected AbstractSalesforceExecution getSalesforceExecution() {
        return execution;
    }

    public void parsePicklistToEnums() {
        execution.parsePicklistToEnums();
    }

    public void parsePicklistToStrings() {
        execution.parsePicklistToStrings();
    }

    public GenerateExecution.GeneratorUtility generatorUtility() {
        return execution.new GeneratorUtility();
    }

    public void processDescription(
            File pkgDir, SObjectDescription description, GenerateExecution.GeneratorUtility utility, Set<String> sObjectNames)
            throws Exception {
        execution.processDescription(pkgDir, description, utility, sObjectNames);
    }

    public void setDescriptions(ObjectDescriptions descriptions) {
        execution.setDescriptions(descriptions);
    }
}
