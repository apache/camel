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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.util.FileUtils;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.changelog.ChangeLog;
import org.apache.directory.server.core.factory.DefaultDirectoryServiceFactory;
import org.apache.directory.server.core.factory.DirectoryServiceFactory;
import org.apache.directory.server.core.factory.PartitionFactory;
import org.apache.directory.server.core.security.TlsKeyGenerator;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.ldap.LdapServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The class responsible for running all the tests. t read the annotations, initialize the DirectoryService, call each
 * test and do the cleanup at the end.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class DirectoryExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    /**
     * A logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(DirectoryExtension.class);

    /**
     * The 'service' field in the run tests
     */
    private static final String SET_SERVICE_METHOD_NAME = "setService";

    /**
     * The 'ldapServer' field in the run tests
     */
    private static final String SET_LDAP_SERVER_METHOD_NAME = "setLdapServer";

    /**
     * The 'kdcServer' field in the run tests
     */
    private static final String SET_KDC_SERVER_METHOD_NAME = "setKdcServer";

    public static class State {
        DirectoryService classDirectoryService;
        DirectoryService methodDirectoryService;
        DirectoryService directoryService;
        LdapServer classLdapServer;
        LdapServer methodLdapServer;
        LdapServer ldapServer;
        KdcServer classKdcServer;
        KdcServer methodKdcServer;
        KdcServer kdcServer;
        DirectoryService oldLdapServerDirService;
        DirectoryService oldKdcServerDirService;
        long revision;

        public void beforeAll(ExtensionContext context) throws Exception {
            Description description = new Description(context.getRequiredTestClass());

            // Before running any test, check to see if we must create a class DS
            // Get the LdapServerBuilder, if any

            classDirectoryService = DSAnnotationProcessor.getDirectoryService(description);
            if (classDirectoryService == null) {
                // define a default class DS then
                DirectoryServiceFactory dsf = new DefaultDirectoryServiceFactory();
                classDirectoryService = dsf.getDirectoryService();
                // enable CL explicitly cause we are not using DSAnnotationProcessor
                classDirectoryService.getChangeLog().setEnabled(true);
                dsf.init("default" + UUID.randomUUID().toString());
                // Load the schemas
                DSAnnotationProcessor.loadSchemas(description, classDirectoryService);
            }
            // Apply the class LDIFs
            DSAnnotationProcessor.applyLdifs(description, classDirectoryService);
            updateTlsKey(classDirectoryService);

            // check if it has a LdapServerBuilder, then use the DS created above
            classLdapServer = ServerAnnotationProcessor.createLdapServer(description, classDirectoryService);

            classKdcServer = ServerAnnotationProcessor.getKdcServer(description, classDirectoryService);

            // print out information which partition factory we use
            DirectoryServiceFactory dsFactory = new DefaultDirectoryServiceFactory();
            PartitionFactory partitionFactory = dsFactory.getPartitionFactory();
            LOG.debug("Using partition factory {}", partitionFactory.getClass().getSimpleName());
        }

        public void beforeEach(ExtensionContext context) throws Exception {
            Description classDescription = new Description(context.getRequiredTestClass());
            Description methodDescription = new Description(context.getRequiredTestMethod());

            // Check if this method has a dedicated DSBuilder
            methodDirectoryService = DSAnnotationProcessor.getDirectoryService(methodDescription);
            // give #1 priority to method level DS if present
            if (methodDirectoryService != null) {
                // Apply all the LDIFs
                DSAnnotationProcessor.applyLdifs(classDescription, methodDirectoryService);
                updateTlsKey(methodDirectoryService);
                directoryService = methodDirectoryService;
            } else if (classDirectoryService != null) {
                directoryService = classDirectoryService;
            } else if (classLdapServer != null) {
                directoryService = classLdapServer.getDirectoryService();
            } else if (classKdcServer != null) {
                directoryService = classKdcServer.getDirectoryService();
            }
            // apply the method LDIFs, and tag for reversion
            revision = getCurrentRevision(directoryService);
            DSAnnotationProcessor.applyLdifs(methodDescription, directoryService);

            methodLdapServer = ServerAnnotationProcessor.createLdapServer(methodDescription, directoryService);
            if (methodLdapServer != null) {
                ldapServer = methodLdapServer;
            } else if (classLdapServer != null) {
                ldapServer = classLdapServer;
            }
            if (ldapServer != null) {
                oldLdapServerDirService = ldapServer.getDirectoryService();
                ldapServer.setDirectoryService(directoryService);
            }

            methodKdcServer = ServerAnnotationProcessor.getKdcServer(methodDescription, directoryService);
            if (methodKdcServer != null) {
                kdcServer = methodKdcServer;
            } else if (classKdcServer != null) {
                kdcServer = classKdcServer;
            }
            if (kdcServer != null) {
                oldKdcServerDirService = kdcServer.getDirectoryService();
                kdcServer.setDirectoryService(directoryService);
            }

            // At this point, we know which services to use, so inject them into the test instance
            inject(context, SET_SERVICE_METHOD_NAME, DirectoryService.class, directoryService);
            inject(context, SET_LDAP_SERVER_METHOD_NAME, LdapServer.class, ldapServer);
            inject(context, SET_KDC_SERVER_METHOD_NAME, KdcServer.class, kdcServer);
        }

        public void afterEach(ExtensionContext context) throws Exception {
            if (oldLdapServerDirService != null) {
                ldapServer.setDirectoryService(oldLdapServerDirService);
            }
            if (oldKdcServerDirService != null) {
                kdcServer.setDirectoryService(oldKdcServerDirService);
            }
            if (methodLdapServer != null) {
                methodLdapServer.stop();
            }
            if (methodKdcServer != null) {
                methodKdcServer.stop();
            }
            // Cleanup the methodDS if it has been created
            if (methodDirectoryService != null) {
                LOG.debug("Shuting down DS for {}", methodDirectoryService.getInstanceId());
                methodDirectoryService.shutdown();
                FileUtils.deleteDirectory(methodDirectoryService.getInstanceLayout().getInstanceDirectory());
            } else {
                // We use a class or suite DS, just revert the current test's modifications
                revert(classDirectoryService, revision);
            }
        }

        public void afterAll(ExtensionContext context) throws Exception {
            if (classLdapServer != null) {
                classLdapServer.stop();
            }
            if (classKdcServer != null) {
                classKdcServer.stop();
            }
            if (classDirectoryService != null) {
                LOG.debug("Shuting down DS for {}", classDirectoryService.getInstanceId());
                classDirectoryService.shutdown();
                FileUtils.deleteDirectory(classDirectoryService.getInstanceLayout().getInstanceDirectory());
            }
        }

        private <T> void inject(ExtensionContext context, String name, Class<T> type, T instance)
                throws InvocationTargetException, IllegalAccessException {
            try {
                Method setter = context.getRequiredTestClass().getMethod(name, type);
                setter.invoke(context.getRequiredTestInstance(), instance);
            } catch (NoSuchMethodException nsme) {
                // Do nothing
            }
        }

        private long getCurrentRevision(DirectoryService dirService) throws Exception {
            if (dirService != null && dirService.getChangeLog().isEnabled()) {
                long revision = dirService.getChangeLog().getCurrentRevision();
                LOG.debug("Create revision {}", revision);
                return revision;
            }
            return 0;
        }

        private void revert(DirectoryService dirService, long revision) throws Exception {
            if (dirService == null) {
                return;
            }
            ChangeLog cl = dirService.getChangeLog();
            if (cl.isEnabled() && revision < cl.getCurrentRevision()) {
                LOG.debug("Revert revision {}", revision);
                dirService.revert(revision);
            }
        }

        private void updateTlsKey(DirectoryService ds) throws LdapException {
            // Update TLS key for tests. Newer Java 8 releases consider RSA keys
            // with less than 1024 bits as insecure and such are disabled by default, see
            // http://www.oracle.com/technetwork/java/javase/8-compatibility-guide-2156366.html
            Entry adminEntry = ds.getAdminSession().lookup(new Dn(ServerDNConstants.ADMIN_SYSTEM_DN));
            TlsKeyGenerator.addKeyPair(adminEntry, TlsKeyGenerator.CERTIFICATE_PRINCIPAL_DN,
                    TlsKeyGenerator.CERTIFICATE_PRINCIPAL_DN, "RSA", 1024);
            Modification mod1 = new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE,
                    adminEntry.get(TlsKeyGenerator.PRIVATE_KEY_AT));
            Modification mod2 = new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE,
                    adminEntry.get(TlsKeyGenerator.PUBLIC_KEY_AT));
            Modification mod3 = new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE,
                    adminEntry.get(TlsKeyGenerator.USER_CERTIFICATE_AT));
            ds.getAdminSession().modify(adminEntry.getDn(), mod1, mod2, mod3);
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(State.class).beforeAll(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(State.class).beforeEach(context);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(State.class).afterEach(context);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        context.getStore(NAMESPACE).getOrComputeIfAbsent(State.class).afterAll(context);
    }

}
