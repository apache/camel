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
package org.apache.directory.server.core.integ5;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapUnwillingToPerformException;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.util.Network;
import org.apache.directory.api.util.Strings;
import org.apache.directory.server.core.annotations.AnnotationUtils;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateAuthenticator;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.annotations.LoadSchema;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.authn.AuthenticationInterceptor;
import org.apache.directory.server.core.authn.Authenticator;
import org.apache.directory.server.core.authn.DelegatingAuthenticator;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.core.partition.impl.btree.AbstractBTreePartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.mavibot.MavibotIndex;
import org.apache.directory.server.i18n.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Helper class used to create a DS from the annotations
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public final class DSAnnotationProcessor {

    /**
     * A logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(DSAnnotationProcessor.class);

    private DSAnnotationProcessor() {
    }

    /**
     * Create the DirectoryService
     *
     * @param  dsBuilder The DirectoryService builder
     * @return           an instance of DirectoryService
     * @throws Exception If the DirectoryService cannot be created
     */
    public static DirectoryService createDS(CreateDS dsBuilder)
            throws Exception {
        LOG.debug("Starting DS {}...", dsBuilder.name());
        Class<?> factory = dsBuilder.factory();
        DirectoryServiceFactory dsf = (DirectoryServiceFactory) factory
                .getDeclaredConstructor().newInstance();

        DirectoryService service = dsf.getDirectoryService();
        service.setAccessControlEnabled(dsBuilder.enableAccessControl());
        service.setAllowAnonymousAccess(dsBuilder.allowAnonAccess());
        service.getChangeLog().setEnabled(dsBuilder.enableChangeLog());

        dsf.init(dsBuilder.name());

        for (Class<?> interceptorClass : dsBuilder.additionalInterceptors()) {
            service.addLast((Interceptor) interceptorClass.getDeclaredConstructor().newInstance());
        }

        List<Interceptor> interceptorList = service.getInterceptors();

        if (dsBuilder.authenticators().length != 0) {
            AuthenticationInterceptor authenticationInterceptor = null;

            for (Interceptor interceptor : interceptorList) {
                if (interceptor instanceof AuthenticationInterceptor) {
                    authenticationInterceptor = (AuthenticationInterceptor) interceptor;
                    break;
                }
            }

            if (authenticationInterceptor == null) {
                throw new IllegalStateException(
                        "authentication interceptor not found");
            }

            Set<Authenticator> authenticators = new HashSet<Authenticator>();

            for (CreateAuthenticator createAuthenticator : dsBuilder.authenticators()) {
                Authenticator auth = createAuthenticator.type().getDeclaredConstructor().newInstance();

                if (auth instanceof DelegatingAuthenticator) {
                    DelegatingAuthenticator dauth = (DelegatingAuthenticator) auth;

                    String host = createAuthenticator.delegateHost();

                    if (Strings.isEmpty(host)) {
                        host = Network.LOOPBACK_HOSTNAME;
                    }

                    dauth.setDelegateHost(host);
                    dauth.setDelegatePort(createAuthenticator.delegatePort());
                    dauth.setDelegateSsl(createAuthenticator.delegateSsl());
                    dauth.setDelegateTls(createAuthenticator.delegateTls());
                    dauth.setBaseDn(service.getDnFactory().create(createAuthenticator.baseDn()));
                    dauth.setDelegateSslTrustManagerFQCN(createAuthenticator.delegateSslTrustManagerFQCN());
                    dauth.setDelegateTlsTrustManagerFQCN(createAuthenticator.delegateTlsTrustManagerFQCN());
                }

                authenticators.add(auth);
            }

            authenticationInterceptor.setAuthenticators(authenticators);
            authenticationInterceptor.init(service);
        }

        service.setInterceptors(interceptorList);

        SchemaManager schemaManager = service.getSchemaManager();

        // process the schemas
        for (LoadSchema loadedSchema : dsBuilder.loadedSchemas()) {
            String schemaName = loadedSchema.name();
            Boolean enabled = loadedSchema.enabled();

            // Check if the schema is loaded or not
            boolean isLoaded = schemaManager.isSchemaLoaded(schemaName);

            if (!isLoaded) {
                // We have to load the schema, if it exists
                try {
                    isLoaded = schemaManager.load(schemaName);
                } catch (LdapUnwillingToPerformException lutpe) {
                    // Cannot load the schema, it does not exist
                    LOG.error(lutpe.getMessage());
                    continue;
                }
            }

            if (isLoaded) {
                if (enabled) {
                    schemaManager.enable(schemaName);

                    if (schemaManager.isDisabled(schemaName)) {
                        LOG.error("Cannot enable {}", schemaName);
                    }
                } else {
                    schemaManager.disable(schemaName);

                    if (schemaManager.isEnabled(schemaName)) {
                        LOG.error("Cannot disable {}", schemaName);
                    }
                }
            }

            LOG.debug("Loading schema {}, enabled= {}", schemaName, enabled);
        }

        // Process the Partition, if any.
        for (CreatePartition createPartition : dsBuilder.partitions()) {
            Partition partition;

            // Determine the partition type
            if (createPartition.type() == Partition.class) {
                // The annotation does not specify a specific partition type.
                // We use the partition factory to create partition and index
                // instances.
                PartitionFactory partitionFactory = dsf.getPartitionFactory();
                partition = partitionFactory.createPartition(
                        schemaManager,
                        service.getDnFactory(),
                        createPartition.name(),
                        createPartition.suffix(),
                        createPartition.cacheSize(),
                        new File(service.getInstanceLayout().getPartitionsDirectory(), createPartition.name()));

                CreateIndex[] indexes = createPartition.indexes();

                for (CreateIndex createIndex : indexes) {
                    partitionFactory.addIndex(partition,
                            createIndex.attribute(), createIndex.cacheSize());
                }

                partition.initialize();
            } else {
                // The annotation contains a specific partition type, we use
                // that type.
                Class<?>[] partypes = new Class[] {
                        SchemaManager.class, DnFactory.class };
                Constructor<?> constructor = createPartition.type().getConstructor(partypes);
                partition = (Partition) constructor.newInstance(new Object[] {
                        schemaManager, service.getDnFactory() });
                partition.setId(createPartition.name());
                partition.setSuffixDn(new Dn(schemaManager, createPartition.suffix()));

                if (partition instanceof AbstractBTreePartition) {
                    AbstractBTreePartition btreePartition = (AbstractBTreePartition) partition;
                    btreePartition.setCacheSize(createPartition.cacheSize());
                    btreePartition.setPartitionPath(new File(
                            service
                                    .getInstanceLayout().getPartitionsDirectory(),
                            createPartition.name()).toURI());

                    // Process the indexes if any
                    CreateIndex[] indexes = createPartition.indexes();

                    for (CreateIndex createIndex : indexes) {
                        if (createIndex.type() == JdbmIndex.class) {
                            // JDBM index
                            JdbmIndex index = new JdbmIndex<>(createIndex.attribute(), false);

                            btreePartition.addIndexedAttributes(index);
                        } else if (createIndex.type() == MavibotIndex.class) {
                            // Mavibot index
                            MavibotIndex index = new MavibotIndex<>(createIndex.attribute(), false);

                            btreePartition.addIndexedAttributes(index);
                        } else {
                            // The annotation does not specify a specific index
                            // type.
                            // We use the generic index implementation.
                            JdbmIndex index = new JdbmIndex<>(createIndex.attribute(), false);

                            btreePartition.addIndexedAttributes(index);
                        }
                    }
                }
            }

            partition.setSchemaManager(schemaManager);

            // Inject the partition into the DirectoryService
            service.addPartition(partition);

            // Last, process the context entry
            ContextEntry contextEntry = createPartition.contextEntry();

            if (contextEntry != null) {
                injectEntries(service, contextEntry.entryLdif());
            }
        }

        return service;
    }

    /**
     * Create a DirectoryService from a Unit test annotation
     *
     * @param  description The annotations containing the info from which we will create the DS
     * @return             A valid DirectoryService
     * @throws Exception   If the DirectoryService instance can't be returned
     */
    public static DirectoryService getDirectoryService(Description description)
            throws Exception {
        CreateDS dsBuilder = description.getAnnotation(CreateDS.class);

        if (dsBuilder != null) {
            return createDS(dsBuilder);
        } else {
            LOG.debug("No {} DS.", description.getDisplayName());
            return null;
        }
    }

    /**
     * Create a DirectoryService from an annotation. The @CreateDS annotation must be associated with either the method
     * or the encapsulating class. We will first try to get the annotation from the method, and if there is none, then
     * we try at the class level.
     *
     * @return           A valid DS
     * @throws Exception If the DirectoryService instance can't be returned
     */
    public static DirectoryService getDirectoryService() throws Exception {
        Object instance = AnnotationUtils.getInstance(CreateDS.class);
        CreateDS dsBuilder = null;

        if (instance != null) {
            dsBuilder = (CreateDS) instance;

            // Ok, we have found a CreateDS annotation. Process it now.
            return createDS(dsBuilder);
        }

        throw new LdapException(I18n.err(I18n.ERR_114));
    }

    /**
     * injects an LDIF entry in the given DirectoryService
     *
     * @param  entry     the LdifEntry to be injected
     * @param  service   the DirectoryService
     * @throws Exception If the entry cannot be injected
     */
    private static void injectEntry(LdifEntry entry, DirectoryService service)
            throws LdapException {
        if (entry.isChangeAdd() || entry.isLdifContent()) {
            service.getAdminSession().add(
                    new DefaultEntry(
                            service.getSchemaManager(), entry
                                    .getEntry()));
        } else if (entry.isChangeModify()) {
            service.getAdminSession().modify(entry.getDn(),
                    entry.getModifications());
        } else {
            String message = I18n.err(I18n.ERR_117, entry.getChangeType());
            throw new LdapException(message);
        }
    }

    /**
     * injects the LDIF entries present in a LDIF file
     *
     * @param  clazz     The class which classLoaded will be use to retrieve the resources
     * @param  service   the DirectoryService
     * @param  ldifFiles array of LDIF file names (only )
     * @throws Exception If we weren't able to inject LdifFiles
     */
    public static void injectLdifFiles(
            Class<?> clazz,
            DirectoryService service, String[] ldifFiles)
            throws Exception {
        if (ldifFiles != null && ldifFiles.length > 0) {
            for (String ldifFile : ldifFiles) {
                InputStream is = clazz.getClassLoader().getResourceAsStream(
                        ldifFile);
                if (is == null) {
                    throw new FileNotFoundException(
                            "LDIF file '" + ldifFile
                                                    + "' not found.");
                } else {
                    LdifReader ldifReader = new LdifReader(is);

                    for (LdifEntry entry : ldifReader) {
                        injectEntry(entry, service);
                    }

                    ldifReader.close();
                }
            }
        }
    }

    /**
     * Inject an ldif String into the server. Dn must be relative to the root.
     *
     * @param  service   the directory service to use
     * @param  ldif      the ldif containing entries to add to the server.
     * @throws Exception if there is a problem adding the entries from the LDIF
     */
    public static void injectEntries(DirectoryService service, String ldif)
            throws Exception {
        LdifReader reader = new LdifReader();
        List<LdifEntry> entries = reader.parseLdif(ldif);

        for (LdifEntry entry : entries) {
            injectEntry(entry, service);
        }

        // And close the reader
        reader.close();
    }

    /**
     * Load the schemas, and enable/disable them.
     *
     * @param desc    The description
     * @param service The DirectoryService instance
     */
    public static void loadSchemas(Description desc, DirectoryService service) {
        if (desc == null) {
            return;
        }

        /*for ( Class<?> loadSchema : dsBuilder.additionalInterceptors() )
        {
            service.addLast( ( Interceptor ) interceptorClass.newInstance() );
        }*/
        LoadSchema loadSchema = desc
                .getAnnotation(LoadSchema.class);

        if (loadSchema != null) {
            System.out.println(loadSchema);
        }
    }

    /**
     * Apply the LDIF entries to the given service
     *
     * @param  desc      The description
     * @param  service   The DirectoryService instance
     * @throws Exception If we can't apply the ldifs
     */
    public static void applyLdifs(Description desc, DirectoryService service)
            throws Exception {
        if (desc == null) {
            return;
        }

        ApplyLdifFiles applyLdifFiles = desc
                .getAnnotation(ApplyLdifFiles.class);

        if (applyLdifFiles != null) {
            LOG.debug("Applying {} to {}", applyLdifFiles.value(),
                    desc.getDisplayName());
            injectLdifFiles(applyLdifFiles.clazz(), service, applyLdifFiles.value());
        }

        ApplyLdifs applyLdifs = desc.getAnnotation(ApplyLdifs.class);

        if (applyLdifs != null && applyLdifs.value() != null) {
            String[] ldifs = applyLdifs.value();

            String dnStart = "dn:";

            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < ldifs.length;) {
                String s = ldifs[i++].trim();
                if (s.startsWith(dnStart)) {
                    sb.append(s).append('\n');

                    // read the rest of lines till we encounter Dn again
                    while (i < ldifs.length) {
                        s = ldifs[i++];
                        if (!s.startsWith(dnStart)) {
                            sb.append(s).append('\n');
                        } else {
                            break;
                        }
                    }

                    LOG.debug("Applying {} to {}", sb, desc.getDisplayName());
                    injectEntries(service, sb.toString());
                    sb.setLength(0);

                    i--; // step up a line
                }
            }
        }
    }
}
