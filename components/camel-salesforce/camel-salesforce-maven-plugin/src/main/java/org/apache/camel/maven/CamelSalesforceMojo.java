/**
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.PickListValue;
import org.apache.camel.component.salesforce.api.dto.SObject;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.DefaultRestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.SyncResponseCallback;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.Log4JLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Socks4Proxy;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Goal to generate DTOs for Salesforce SObjects
 */
@Mojo(name = "generate", requiresProject = false, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CamelSalesforceMojo extends AbstractMojo {
    // default connect and call timeout
    protected static final int DEFAULT_TIMEOUT = 60000;

    private static final String UTF_8 = "UTF-8";

    private static final String JAVA_EXT = ".java";
    private static final String PACKAGE_NAME_PATTERN = "(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

    private static final Pattern MATCH_EVERYTHING_PATTERN = Pattern.compile(".*");
    private static final Pattern MATCH_NOTHING_PATTERN = Pattern.compile("^$");

    private static final String SOBJECT_POJO_VM = "/sobject-pojo.vm";
    private static final String SOBJECT_POJO_OPTIONAL_VM = "/sobject-pojo-optional.vm";
    private static final String SOBJECT_QUERY_RECORDS_VM = "/sobject-query-records.vm";
    private static final String SOBJECT_QUERY_RECORDS_OPTIONAL_VM = "/sobject-query-records-optional.vm";
    private static final String SOBJECT_PICKLIST_VM = "/sobject-picklist.vm";

    private static final List<String> IGNORED_OBJECTS = Arrays.asList("FieldDefinition");

    // used for velocity logging, to avoid creating velocity.log
    private static final Logger LOG = Logger.getLogger(CamelSalesforceMojo.class.getName());

    /**
     * HTTP client properties.
     */
    @Parameter
    protected Map<String, Object> httpClientProperties;

    /**
     * SSL Context parameters.
     */
    @Parameter(property = "camelSalesforce.sslContextParameters")
    protected SSLContextParameters sslContextParameters;

    /**
     * HTTP Proxy host.
     */
    @Parameter(property = "camelSalesforce.httpProxyHost")
    protected String httpProxyHost;

    /**
     * HTTP Proxy port.
     */
    @Parameter(property = "camelSalesforce.httpProxyPort")
    protected Integer httpProxyPort;

    /**
     * Is it a SOCKS4 Proxy?
     */
    @Parameter(property = "camelSalesforce.isHttpProxySocks4")
    protected boolean isHttpProxySocks4;

    /**
     * Is HTTP Proxy secure, i.e. using secure sockets, true by default.
     */
    @Parameter(property = "camelSalesforce.isHttpProxySecure")
    protected boolean isHttpProxySecure = true;

    /**
     * Addresses to Proxy.
     */
    @Parameter(property = "camelSalesforce.httpProxyIncludedAddresses")
    protected Set<String> httpProxyIncludedAddresses;

    /**
     * Addresses to NOT Proxy.
     */
    @Parameter(property = "camelSalesforce.httpProxyExcludedAddresses")
    protected Set<String> httpProxyExcludedAddresses;

    /**
     * Proxy authentication username.
     */
    @Parameter(property = "camelSalesforce.httpProxyUsername")
    protected String httpProxyUsername;

    /**
     * Proxy authentication password.
     */
    @Parameter(property = "camelSalesforce.httpProxyPassword")
    protected String httpProxyPassword;

    /**
     * Proxy authentication URI.
     */
    @Parameter(property = "camelSalesforce.httpProxyAuthUri")
    protected String httpProxyAuthUri;

    /**
     * Proxy authentication realm.
     */
    @Parameter(property = "camelSalesforce.httpProxyRealm")
    protected String httpProxyRealm;

    /**
     * Proxy uses Digest authentication.
     */
    @Parameter(property = "camelSalesforce.httpProxyUseDigestAuth")
    protected boolean httpProxyUseDigestAuth;

    /**
     * Salesforce client id.
     */
    @Parameter(property = "camelSalesforce.clientId", required = true)
    protected String clientId;

    /**
     * Salesforce client secret.
     */
    @Parameter(property = "camelSalesforce.clientSecret", required = true)
    protected String clientSecret;

    /**
     * Salesforce username.
     */
    @Parameter(property = "camelSalesforce.userName", required = true)
    protected String userName;

    /**
     * Salesforce password.
     */
    @Parameter(property = "camelSalesforce.password", required = true)
    protected String password;

    /**
     * Salesforce API version.
     */
    @Parameter(property = "camelSalesforce.version", defaultValue = SalesforceEndpointConfig.DEFAULT_VERSION)
    protected String version;

    /**
     * Location of generated DTO files, defaults to target/generated-sources/camel-salesforce.
     */
    @Parameter(property = "camelSalesforce.outputDirectory",
        defaultValue = "${project.build.directory}/generated-sources/camel-salesforce")
    protected File outputDirectory;

    /**
     * Salesforce login URL, defaults to https://login.salesforce.com.
     */
    @Parameter(property = "camelSalesforce.loginUrl", defaultValue = SalesforceLoginConfig.DEFAULT_LOGIN_URL)
    protected String loginUrl;

    /**
     * Names of Salesforce SObject for which DTOs must be generated.
     */
    @Parameter
    protected String[] includes;

    /**
     * Do NOT generate DTOs for these Salesforce SObjects.
     */
    @Parameter
    protected String[] excludes;

    /**
     * Include Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.includePattern")
    protected String includePattern;

    /**
     * Exclude Salesforce SObjects that match pattern.
     */
    @Parameter(property = "camelSalesforce.excludePattern")
    protected String excludePattern;

    /**
     * Java package name for generated DTOs.
     */
    @Parameter(property = "camelSalesforce.packageName", defaultValue = "org.apache.camel.salesforce.dto")
    protected String packageName;

    @Parameter(property = "camelSalesforce.useOptionals", defaultValue = "false")
    protected boolean useOptionals;

    @Parameter(property = "camelSalesforce.useStringsForPicklists", defaultValue = "false")
    protected Boolean useStringsForPicklists;

    /**
     * Generate JSON Schema for DTOs, instead of Java Objects.
     */
    @Parameter(property = "camelSalesforce.jsonSchema")
    protected boolean jsonSchema;

    /**
     * Schema ID for JSON Schema for DTOs.
     */
    @Parameter(property = "camelSalesforce.jsonSchemaId", defaultValue = JsonUtils.DEFAULT_ID_PREFIX)
    protected String jsonSchemaId;

    /**
     * Schema ID for JSON Schema for DTOs.
     */
    @Parameter(property = "camelSalesforce.jsonSchemaFilename", defaultValue = "salesforce-dto-schema.json")
    protected String jsonSchemaFilename;

    VelocityEngine engine;

    private long responseTimeout;

    /**
     * Execute the mojo to generate SObject DTOs
     *
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {
        engine = createVelocityEngine();

        // make sure we can load both templates
        if (!engine.resourceExists(SOBJECT_POJO_VM)
                || !engine.resourceExists(SOBJECT_QUERY_RECORDS_VM)
                || !engine.resourceExists(SOBJECT_POJO_OPTIONAL_VM)
                || !engine.resourceExists(SOBJECT_QUERY_RECORDS_OPTIONAL_VM)) {
            throw new MojoExecutionException("Velocity templates not found");
        }

        // connect to Salesforce
        final SalesforceHttpClient httpClient = createHttpClient();
        final SalesforceSession session = httpClient.getSession();

        getLog().info("Salesforce login...");
        try {
            session.login(null);
        } catch (SalesforceException e) {
            String msg = "Salesforce login error " + e.getMessage();
            throw new MojoExecutionException(msg, e);
        }
        getLog().info("Salesforce login successful");

        // create rest client
        RestClient restClient;
        try {
            restClient = new DefaultRestClient(httpClient,
                    version, PayloadFormat.JSON, session);
            // remember to start the active client object
            ((DefaultRestClient) restClient).start();
        } catch (Exception e) {
            final String msg = "Unexpected exception creating Rest client: " + e.getMessage();
            throw new MojoExecutionException(msg, e);
        }

        try {
            // use Jackson json
            final ObjectMapper mapper = JsonUtils.createObjectMapper();

            // call getGlobalObjects to get all SObjects
            final Set<String> objectNames = new TreeSet<String>();
            final SyncResponseCallback callback = new SyncResponseCallback();
            try {
                getLog().info("Getting Salesforce Objects...");
                restClient.getGlobalObjects(callback);
                if (!callback.await(responseTimeout, TimeUnit.MILLISECONDS)) {
                    throw new MojoExecutionException("Timeout waiting for getGlobalObjects!");
                }
                final SalesforceException ex = callback.getException();
                if (ex != null) {
                    throw ex;
                }
                final GlobalObjects globalObjects = mapper.readValue(callback.getResponse(),
                        GlobalObjects.class);

                // create a list of object names
                for (SObject sObject : globalObjects.getSobjects()) {
                    objectNames.add(sObject.getName());
                }
            } catch (Exception e) {
                String msg = "Error getting global Objects: " + e.getMessage();
                throw new MojoExecutionException(msg, e);
            }

            // check if we are generating POJOs for all objects or not
            if ((includes != null && includes.length > 0)
                    || (excludes != null && excludes.length > 0)
                    || ObjectHelper.isNotEmpty(includePattern)
                    || ObjectHelper.isNotEmpty(excludePattern)) {

                filterObjectNames(objectNames);

            } else {
                getLog().warn(String.format("Generating Java classes for all %s Objects, this may take a while...", objectNames.size()));
            }

            // for every accepted name, get SObject description
            final Set<SObjectDescription> descriptions = new HashSet<SObjectDescription>();

            getLog().info("Retrieving Object descriptions...");
            for (String name : objectNames) {
                try {
                    callback.reset();
                    restClient.getDescription(name, callback);
                    if (!callback.await(responseTimeout, TimeUnit.MILLISECONDS)) {
                        throw new MojoExecutionException("Timeout waiting for getDescription for sObject " + name);
                    }
                    final SalesforceException ex = callback.getException();
                    if (ex != null) {
                        throw ex;
                    }
                    final SObjectDescription description = mapper.readValue(callback.getResponse(), SObjectDescription.class);

                    // remove some of the unused used metadata
                    // properties in order to minimize the code size
                    // for CAMEL-11310
                    final SObjectDescription descriptionToAdd = description.prune();

                    descriptions.add(descriptionToAdd);
                } catch (Exception e) {
                    String msg = "Error getting SObject description for '" + name + "': " + e.getMessage();
                    throw new MojoExecutionException(msg, e);
                }
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

            if (!jsonSchema) {

                getLog().info("Generating Java Classes...");
                // generate POJOs for every object description
                final GeneratorUtility utility = new GeneratorUtility(useStringsForPicklists);
                // should we provide a flag to control timestamp generation?
                final String generatedDate = new Date().toString();
                for (SObjectDescription description : descriptions) {
                    if (IGNORED_OBJECTS.contains(description.getName())) {
                        continue;
                    }
                    try {
                        processDescription(pkgDir, description, utility, generatedDate);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Unable to generate source files for: " + description.getName(), e);
                    }
                }

                getLog().info(String.format("Successfully generated %s Java Classes", descriptions.size() * 2));

            } else {

                getLog().info("Generating JSON Schema...");
                // generate JSON schema for every object description
                final ObjectMapper schemaObjectMapper = JsonUtils.createSchemaObjectMapper();
                final Set<Object> allSchemas = new HashSet<>();
                for (SObjectDescription description : descriptions) {
                    if (IGNORED_OBJECTS.contains(description.getName())) {
                        continue;
                    }
                    try {
                        allSchemas.add(JsonUtils.getSObjectJsonSchema(schemaObjectMapper, description, jsonSchemaId, true));
                    } catch (IOException e) {
                        throw new MojoExecutionException("Unable to generate JSON Schema types for: " + description.getName(), e);
                    }
                }

                final Path schemaFilePath = outputDirectory.toPath().resolve(jsonSchemaFilename);
                try {
                    Files.write(schemaFilePath, JsonUtils.getJsonSchemaString(schemaObjectMapper, allSchemas, jsonSchemaId).getBytes("UTF-8"));
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to generate JSON Schema source file: " + schemaFilePath, e);
                }

                getLog().info(String.format("Successfully generated %s JSON Types in file %s", descriptions.size() * 2, schemaFilePath));
            }

        } finally {
            // remember to stop the client
            try {
                ((DefaultRestClient) restClient).stop();
            } catch (Exception ignore) {
            }

            // Salesforce session stop
            try {
                session.stop();
            } catch (Exception ignore) {
            }

            // release HttpConnections
            try {
                httpClient.stop();
            } catch (Exception ignore) {
            }
        }
    }

    static VelocityEngine createVelocityEngine() {
        // initialize velocity to load resources from class loader and use Log4J
        final Properties velocityProperties = new Properties();
        velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
        velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, Log4JLogChute.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM + ".log4j.logger", LOG.getName());

        final VelocityEngine engine = new VelocityEngine(velocityProperties);

        return engine;
    }

    protected void filterObjectNames(Set<String> objectNames) throws MojoExecutionException {
        getLog().info("Looking for matching Object names...");
        // create a list of accepted names
        final Set<String> includedNames = new HashSet<String>();
        if (includes != null && includes.length > 0) {
            for (String name : includes) {
                name = name.trim();
                if (name.isEmpty()) {
                    throw new MojoExecutionException("Invalid empty name in includes");
                }
                includedNames.add(name);
            }
        }

        final Set<String> excludedNames = new HashSet<String>();
        if (excludes != null && excludes.length > 0) {
            for (String name : excludes) {
                name = name.trim();
                if (name.isEmpty()) {
                    throw new MojoExecutionException("Invalid empty name in excludes");
                }
                excludedNames.add(name);
            }
        }

        // check whether a pattern is in effect
        Pattern incPattern;
        if (includePattern != null && !includePattern.trim().isEmpty()) {
            incPattern = Pattern.compile(includePattern.trim());
        } else if (includedNames.isEmpty()) {
            // include everything by default if no include names are set
            incPattern = MATCH_EVERYTHING_PATTERN;
        } else {
            // include nothing by default if include names are set
            incPattern = MATCH_NOTHING_PATTERN;
        }

        // check whether a pattern is in effect
        Pattern excPattern;
        if (excludePattern != null && !excludePattern.trim().isEmpty()) {
            excPattern = Pattern.compile(excludePattern.trim());
        } else {
            // exclude nothing by default
            excPattern = MATCH_NOTHING_PATTERN;
        }

        final Set<String> acceptedNames = new HashSet<String>();
        for (String name : objectNames) {
            // name is included, or matches include pattern
            // and is not excluded and does not match exclude pattern
            if ((includedNames.contains(name) || incPattern.matcher(name).matches())
                    && !excludedNames.contains(name) && !excPattern.matcher(name).matches()) {
                acceptedNames.add(name);
            }
        }
        objectNames.clear();
        objectNames.addAll(acceptedNames);

        getLog().info(String.format("Found %s matching Objects", objectNames.size()));
    }

    protected SalesforceHttpClient createHttpClient() throws MojoExecutionException {

        final SalesforceHttpClient httpClient;

        // set ssl context parameters
        try {

            final SSLContextParameters contextParameters = sslContextParameters != null
                ? sslContextParameters : new SSLContextParameters();
            final SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setSslContext(contextParameters.createSSLContext());

            httpClient = new SalesforceHttpClient(sslContextFactory);

        } catch (GeneralSecurityException e) {
            throw new MojoExecutionException("Error creating default SSL context: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating default SSL context: " + e.getMessage(), e);
        }

        // default settings
        httpClient.setConnectTimeout(DEFAULT_TIMEOUT);
        httpClient.setTimeout(DEFAULT_TIMEOUT);

        // enable redirects, no need for a RedirectListener class in Jetty 9
        httpClient.setFollowRedirects(true);

        // set HTTP client parameters
        if (httpClientProperties != null && !httpClientProperties.isEmpty()) {
            try {
                IntrospectionSupport.setProperties(httpClient, new HashMap<String, Object>(httpClientProperties));
            } catch (Exception e) {
                throw new MojoExecutionException("Error setting HTTP client properties: " + e.getMessage(), e);
            }
        }

        // wait for 1 second longer than the HTTP client response timeout
        responseTimeout = httpClient.getTimeout() + 1000L;

        // set http proxy settings
        // set HTTP proxy settings
        if (this.httpProxyHost != null && httpProxyPort != null) {
            Origin.Address proxyAddress = new Origin.Address(this.httpProxyHost, this.httpProxyPort);
            ProxyConfiguration.Proxy proxy;
            if (isHttpProxySocks4) {
                proxy = new Socks4Proxy(proxyAddress, isHttpProxySecure);
            } else {
                proxy = new HttpProxy(proxyAddress, isHttpProxySecure);
            }
            if (httpProxyIncludedAddresses != null && !httpProxyIncludedAddresses.isEmpty()) {
                proxy.getIncludedAddresses().addAll(httpProxyIncludedAddresses);
            }
            if (httpProxyExcludedAddresses != null && !httpProxyExcludedAddresses.isEmpty()) {
                proxy.getExcludedAddresses().addAll(httpProxyExcludedAddresses);
            }
            httpClient.getProxyConfiguration().getProxies().add(proxy);
        }
        if (this.httpProxyUsername != null && httpProxyPassword != null) {

            ObjectHelper.notEmpty(httpProxyAuthUri, "httpProxyAuthUri");
            ObjectHelper.notEmpty(httpProxyRealm, "httpProxyRealm");

            final Authentication authentication;
            if (httpProxyUseDigestAuth) {
                authentication = new DigestAuthentication(URI.create(httpProxyAuthUri),
                    httpProxyRealm, httpProxyUsername, httpProxyPassword);
            } else {
                authentication = new BasicAuthentication(URI.create(httpProxyAuthUri),
                    httpProxyRealm, httpProxyUsername, httpProxyPassword);
            }
            httpClient.getAuthenticationStore().addAuthentication(authentication);
        }

        // set session before calling start()
        final SalesforceSession session = new SalesforceSession(new DefaultCamelContext(), httpClient,
            httpClient.getTimeout(),
            new SalesforceLoginConfig(loginUrl, clientId, clientSecret, userName, password, false));
        httpClient.setSession(session);

        try {
            httpClient.start();
        } catch (Exception e) {
            throw new MojoExecutionException("Error creating HTTP client: " + e.getMessage(), e);
        }
        return httpClient;
    }

    void processDescription(File pkgDir, SObjectDescription description, GeneratorUtility utility, String generatedDate) throws IOException {
        // generate a source file for SObject
        final VelocityContext context = new VelocityContext();
        context.put("packageName", packageName);
        context.put("utility", utility);
        context.put("esc", StringEscapeUtils.class);
        context.put("desc", description);
        context.put("generatedDate", generatedDate);
        context.put("useStringsForPicklists", useStringsForPicklists);

        final String pojoFileName = description.getName() + JAVA_EXT;
        final File pojoFile = new File(pkgDir, pojoFileName);
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
        for (SObjectField field : description.getFields()) {
            if (utility.isPicklist(field) || utility.isMultiSelectPicklist(field)) {
                final String enumName = description.getName() + "_" + utility.enumTypeName(field.getName());
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

    public static class GeneratorUtility {

        private static final Set<String> BASE_FIELDS;
        private static final Map<String, String> LOOKUP_MAP;

        static {
            BASE_FIELDS = new HashSet<String>();
            for (Field field : AbstractSObjectBase.class.getDeclaredFields()) {
                BASE_FIELDS.add(field.getName());
            }

            // create a type map
            // using JAXB mapping, for the most part
            // uses Joda time instead of XmlGregorianCalendar
            // TODO do we need support for commented types???
            final String[][] typeMap = new String[][]{
                {"ID", "String"}, // mapping for tns:ID SOAP type
                {"string", "String"},
                {"integer", "java.math.BigInteger"},
                {"int", "Integer"},
                {"long", "Long"},
                {"short", "Short"},
                {"decimal", "java.math.BigDecimal"},
                {"float", "Float"},
                {"double", "Double"},
                {"boolean", "Boolean"},
                {"byte", "Byte"},
//                {"QName", "javax.xml.namespace.QName"},

                {"dateTime", "java.time.ZonedDateTime"},

                    // the blob base64Binary type is mapped to String URL for retrieving the blob
                {"base64Binary", "String"},
//                {"hexBinary", "byte[]"},

                {"unsignedInt", "Long"},
                {"unsignedShort", "Integer"},
                {"unsignedByte", "Short"},

//                {"time", "javax.xml.datatype.XMLGregorianCalendar"},
                {"time", "java.time.ZonedDateTime"},
//                {"date", "javax.xml.datatype.XMLGregorianCalendar"},
                {"date", "java.time.ZonedDateTime"},
//                {"g", "javax.xml.datatype.XMLGregorianCalendar"},
                {"g", "java.time.ZonedDateTime"},

                    // Salesforce maps any types like string, picklist, reference, etc. to string
                {"anyType", "String"},
/*
                {"anySimpleType", "java.lang.Object"},
                {"anySimpleType", "java.lang.String"},
                {"duration", "javax.xml.datatype.Duration"},
                {"NOTATION", "javax.xml.namespace.QName"}
*/
                {"address", "org.apache.camel.component.salesforce.api.dto.Address"},
                {"location", "org.apache.camel.component.salesforce.api.dto.GeoLocation"}
            };
            LOOKUP_MAP = new HashMap<String, String>();
            for (String[] entry : typeMap) {
                LOOKUP_MAP.put(entry[0], entry[1]);
            }
        }

        private static final String BASE64BINARY = "base64Binary";
        private static final String MULTIPICKLIST = "multipicklist";
        private static final String PICKLIST = "picklist";
        private static final List<String> BLACKLISTED_PROPERTIES = Arrays.asList("PicklistValues", "ChildRelationships");
        private boolean useStringsForPicklists;
        private final Map<String, AtomicInteger> varNames = new HashMap<>();
        private Stack<String> stack;

        public GeneratorUtility(Boolean useStringsForPicklists) {
            this.useStringsForPicklists = Boolean.TRUE.equals(useStringsForPicklists);
        }

        public boolean isBlobField(SObjectField field) {
            final String soapType = field.getSoapType();
            return BASE64BINARY.equals(soapType.substring(soapType.indexOf(':') + 1));
        }

        public boolean notBaseField(String name) {
            return !BASE_FIELDS.contains(name);
        }

        public String getFieldType(SObjectDescription description, SObjectField field) throws MojoExecutionException {
            // check if this is a picklist
            if (isPicklist(field)) {
                if (useStringsForPicklists) {
                    return String.class.getName();
                } else {
                    // use a pick list enum, which will be created after generating the SObject class
                    return description.getName() + "_" + enumTypeName(field.getName());
                }
            } else if (isMultiSelectPicklist(field)) {
                if (useStringsForPicklists) {
                    return String.class.getName() + "[]";
                } else {
                    // use a pick list enum array, enum will be created after generating the SObject class
                    return description.getName() + "_" + enumTypeName(field.getName()) + "[]";
                }
            } else {
                // map field to Java type
                final String soapType = field.getSoapType();
                final String type = LOOKUP_MAP.get(soapType.substring(soapType.indexOf(':') + 1));
                if (type == null) {
                    throw new MojoExecutionException(
                            String.format("Unsupported type %s for field %s", soapType, field.getName()));
                }
                return type;
            }
        }

        public boolean isMultiSelectPicklist(SObjectField field) {
            return MULTIPICKLIST.equals(field.getType());
        }

        public boolean hasPicklists(SObjectDescription desc) {
            for (SObjectField field : desc.getFields()) {
                if (isPicklist(field)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasMultiSelectPicklists(SObjectDescription desc) {
            for (SObjectField field : desc.getFields()) {
                if (isMultiSelectPicklist(field)) {
                    return true;
                }
            }
            return false;
        }

        public List<PickListValue> getUniqueValues(SObjectField field) {
            if (field.getPicklistValues().isEmpty()) {
                return field.getPicklistValues();
            }
            final List<PickListValue> result = new ArrayList<PickListValue>();
            final Set<String> literals = new HashSet<String>();
            for (PickListValue listValue : field.getPicklistValues()) {
                final String value = listValue.getValue();
                if (!literals.contains(value)) {
                    literals.add(value);
                    result.add(listValue);
                }
            }
            literals.clear();
            Collections.sort(result, new Comparator<PickListValue>() {
                @Override
                public int compare(PickListValue o1, PickListValue o2) {
                    return o1.getValue().compareTo(o2.getValue());
                }
            });
            return result;
        }

        public boolean isPicklist(SObjectField field) {
            return PICKLIST.equals(field.getType());
        }

        public String enumTypeName(String name) {
            name = name.endsWith("__c") ? name.substring(0, name.length() - 3) : name;
            return name + "Enum";
        }

        public String getEnumConstant(String value) {

            // TODO add support for supplementary characters
            final StringBuilder result = new StringBuilder();
            boolean changed = false;
            if (!Character.isJavaIdentifierStart(value.charAt(0))) {
                result.append("_");
                changed = true;
            }
            for (char c : value.toCharArray()) {
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

        public boolean includeList(final List<?> list, final String propertyName) {
            return !list.isEmpty() && !BLACKLISTED_PROPERTIES.contains(propertyName);
        }
        public boolean notNull(final Object val) {
            return val != null;
        }

        public Set<Map.Entry<String, Object>> propertiesOf(final Object object) {
            final Map<String, Object> properties = new HashMap<>();
            IntrospectionSupport.getProperties(object, properties, null, false);

            return properties.entrySet().stream()
                .collect(Collectors.toMap(e -> StringUtils.capitalize(e.getKey()), Map.Entry::getValue)).entrySet();
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

        public boolean isPrimitiveOrBoxed(final Object object) {
            final Class<?> clazz = object.getClass();

            final boolean isWholeNumberWrapper = Byte.class.equals(clazz) || Short.class.equals(clazz)
                || Integer.class.equals(clazz) || Long.class.equals(clazz);

            final boolean isFloatingPointWrapper = Double.class.equals(clazz) || Float.class.equals(clazz);

            final boolean isWrapper = isWholeNumberWrapper || isFloatingPointWrapper || Boolean.class.equals(clazz)
                || Character.class.equals(clazz);

            final boolean isPrimitive = clazz.isPrimitive();

            return isPrimitive || isWrapper;
        }

        public void start(final String initial) {
            stack = new Stack<>();
            stack.push(initial);
            varNames.clear();
        }

        public String current() {
            return stack.peek();
        }

        public void push(final String additional) {
            stack.push(additional);
        }

        public void pop() {
            stack.pop();
        }
    }

}
