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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.camel.component.salesforce.SalesforceEndpointConfig;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.PickListValue;
import org.apache.camel.component.salesforce.api.dto.SObject;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.component.salesforce.internal.SalesforceSession;
import org.apache.camel.component.salesforce.internal.client.DefaultRestClient;
import org.apache.camel.component.salesforce.internal.client.RestClient;
import org.apache.camel.component.salesforce.internal.client.SyncResponseCallback;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
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
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.client.Address;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.RedirectListener;
import org.eclipse.jetty.client.security.ProxyAuthorization;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Goal to generate DTOs for Salesforce SObjects
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class CamelSalesforceMojo extends AbstractMojo {

    // default connect and call timeout
    protected static final int DEFAULT_TIMEOUT = 60000;

    private static final String JAVA_EXT = ".java";
    private static final String PACKAGE_NAME_PATTERN = "^[a-z]+(\\.[a-z][a-z0-9]*)*$";

    private static final String SOBJECT_POJO_VM = "/sobject-pojo.vm";
    private static final String SOBJECT_QUERY_RECORDS_VM = "/sobject-query-records.vm";
    private static final String SOBJECT_PICKLIST_VM = "/sobject-picklist.vm";

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

    private VelocityEngine engine;
    private long responseTimeout;

    /**
     * Execute the mojo to generate SObject DTOs
     *
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {
        // initialize velocity to load resources from class loader and use Log4J
        Properties velocityProperties = new Properties();
        velocityProperties.setProperty(RuntimeConstants.RESOURCE_LOADER, "cloader");
        velocityProperties.setProperty("cloader.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, Log4JLogChute.class.getName());
        velocityProperties.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM + ".log4j.logger", LOG.getName());
        engine = new VelocityEngine(velocityProperties);
        engine.init();

        // make sure we can load both templates
        if (!engine.resourceExists(SOBJECT_POJO_VM) || !engine.resourceExists(SOBJECT_QUERY_RECORDS_VM)) {
            throw new MojoExecutionException("Velocity templates not found");
        }

        // connect to Salesforce
        final HttpClient httpClient = createHttpClient();

        final SalesforceSession session = new SalesforceSession(httpClient,
                new SalesforceLoginConfig(loginUrl, clientId, clientSecret, userName, password, false));

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
            final ObjectMapper mapper = new ObjectMapper();

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
                String msg = "Error getting global Objects " + e.getMessage();
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
                    descriptions.add(mapper.readValue(callback.getResponse(), SObjectDescription.class));
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
            final File pkgDir = new File(outputDirectory, packageName.trim().replace('.', File.separatorChar));
            if (!pkgDir.exists()) {
                if (!pkgDir.mkdirs()) {
                    throw new MojoExecutionException("Unable to create " + pkgDir);
                }
            }

            getLog().info("Generating Java Classes...");
            // generate POJOs for every object description
            final GeneratorUtility utility = new GeneratorUtility();
            // should we provide a flag to control timestamp generation?
            final String generatedDate = new Date().toString();
            for (SObjectDescription description : descriptions) {
                processDescription(pkgDir, description, utility, generatedDate);
            }
            getLog().info(String.format("Successfully generated %s Java Classes", descriptions.size() * 2));

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
            incPattern = Pattern.compile(".*");
        } else {
            // include nothing by default if include names are set
            incPattern = Pattern.compile("^$");
        }

        // check whether a pattern is in effect
        Pattern excPattern;
        if (excludePattern != null && !excludePattern.trim().isEmpty()) {
            excPattern = Pattern.compile(excludePattern.trim());
        } else {
            // exclude nothing by default
            excPattern = Pattern.compile("^$");
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

    protected HttpClient createHttpClient() throws MojoExecutionException {

        final HttpClient httpClient = new HttpClient();

        // default settings
        httpClient.registerListener(RedirectListener.class.getName());
        httpClient.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        httpClient.setConnectTimeout(DEFAULT_TIMEOUT);
        httpClient.setTimeout(DEFAULT_TIMEOUT);

        // set ssl context parameters
        try {
            final SSLContextParameters contextParameters = sslContextParameters != null
                ? sslContextParameters : new SSLContextParameters();
            final SslContextFactory sslContextFactory = httpClient.getSslContextFactory();
            sslContextFactory.setSslContext(contextParameters.createSSLContext());
        } catch (GeneralSecurityException e) {
            throw new MojoExecutionException("Error creating default SSL context: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating default SSL context: " + e.getMessage(), e);
        }

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
        if (this.httpProxyHost != null && httpProxyPort != null) {
            httpClient.setProxy(new Address(this.httpProxyHost, this.httpProxyPort));
        }
        if (this.httpProxyUsername != null && httpProxyPassword != null) {
            try {
                httpClient.setProxyAuthentication(new ProxyAuthorization(this.httpProxyUsername, this.httpProxyPassword));
            } catch (IOException e) {
                throw new MojoExecutionException("Error configuring proxy authorization: " + e.getMessage(), e);
            }
        }

        // add redirect listener to handle Salesforce redirects
        // this is ok to do since the RedirectListener is in the same classloader as Jetty client
        String listenerClass = RedirectListener.class.getName();
        if (httpClient.getRegisteredListeners() == null
            || !httpClient.getRegisteredListeners().contains(listenerClass)) {
            httpClient.registerListener(listenerClass);
        }

        try {
            httpClient.start();
        } catch (Exception e) {
            throw new MojoExecutionException("Error creating HTTP client: " + e.getMessage(), e);
        }
        return httpClient;
    }

    private void processDescription(File pkgDir, SObjectDescription description, GeneratorUtility utility, String generatedDate) throws MojoExecutionException {
        // generate a source file for SObject
        String fileName = description.getName() + JAVA_EXT;
        BufferedWriter writer = null;
        try {
            final File pojoFile = new File(pkgDir, fileName);
            writer = new BufferedWriter(new FileWriter(pojoFile));

            VelocityContext context = new VelocityContext();
            context.put("packageName", packageName);
            context.put("utility", utility);
            context.put("desc", description);
            context.put("generatedDate", generatedDate);

            Template pojoTemplate = engine.getTemplate(SOBJECT_POJO_VM);
            pojoTemplate.merge(context, writer);
            // close pojoFile
            writer.close();

            // write required Enumerations for any picklists
            for (SObjectField field : description.getFields()) {
                if (utility.isPicklist(field) || utility.isMultiSelectPicklist(field)) {
                    fileName = utility.enumTypeName(field.getName()) + JAVA_EXT;
                    File enumFile = new File(pkgDir, fileName);
                    writer = new BufferedWriter(new FileWriter(enumFile));

                    context = new VelocityContext();
                    context.put("packageName", packageName);
                    context.put("utility", utility);
                    context.put("field", field);
                    context.put("generatedDate", generatedDate);

                    Template queryTemplate = engine.getTemplate(SOBJECT_PICKLIST_VM);
                    queryTemplate.merge(context, writer);

                    // close Enum file
                    writer.close();
                }
            }

            // write the QueryRecords class
            fileName = "QueryRecords" + description.getName() + JAVA_EXT;
            File queryFile = new File(pkgDir, fileName);
            writer = new BufferedWriter(new FileWriter(queryFile));

            context = new VelocityContext();
            context.put("packageName", packageName);
            context.put("desc", description);
            context.put("generatedDate", generatedDate);

            Template queryTemplate = engine.getTemplate(SOBJECT_QUERY_RECORDS_VM);
            queryTemplate.merge(context, writer);

            // close QueryRecords file
            writer.close();

        } catch (Exception e) {
            String msg = "Error creating " + fileName + ": " + e.getMessage();
            throw new MojoExecutionException(msg, e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignore) {
                }
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

//                {"dateTime", "javax.xml.datatype.XMLGregorianCalendar"},
                {"dateTime", "org.joda.time.DateTime"},

                    // the blob base64Binary type is mapped to String URL for retrieving the blob
                {"base64Binary", "String"},
//                {"hexBinary", "byte[]"},

                {"unsignedInt", "Long"},
                {"unsignedShort", "Integer"},
                {"unsignedByte", "Short"},

//                {"time", "javax.xml.datatype.XMLGregorianCalendar"},
                {"time", "org.joda.time.DateTime"},
//                {"date", "javax.xml.datatype.XMLGregorianCalendar"},
                {"date", "org.joda.time.DateTime"},
//                {"g", "javax.xml.datatype.XMLGregorianCalendar"},
                {"g", "org.joda.time.DateTime"},

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

        public boolean isBlobField(SObjectField field) {
            final String soapType = field.getSoapType();
            return BASE64BINARY.equals(soapType.substring(soapType.indexOf(':') + 1));
        }

        public boolean notBaseField(String name) {
            return !BASE_FIELDS.contains(name);
        }

        public String getFieldType(SObjectField field) throws MojoExecutionException {
            // check if this is a picklist
            if (isPicklist(field)) {
                // use a pick list enum, which will be created after generating the SObject class
                return enumTypeName(field.getName());
            } else if (isMultiSelectPicklist(field)) {
                // use a pick list enum array, enum will be created after generating the SObject class
                return enumTypeName(field.getName()) + "[]";
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
//            return field.getPicklistValues() != null && !field.getPicklistValues().isEmpty();
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
    }

}
