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
package org.apache.camel.itest.springboot.arquillian.container;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.itest.springboot.arquillian.container.jmx.CustomJMXProtocol;
import org.apache.camel.itest.springboot.arquillian.container.util.Await;
import org.apache.camel.itest.springboot.arquillian.container.util.FileDeploymentUtils;
import org.jboss.arquillian.container.se.api.ClassPath;
import org.jboss.arquillian.container.se.api.ClassPathDirectory;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.protocol.jmx.JMXTestRunnerMBean;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.asset.ArchiveAsset;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ClassAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

public class ManagedSEDeployableContainer implements DeployableContainer<ManagedSEContainerConfiguration> {

    private static final Logger LOGGER = Logger.getLogger(ManagedSEDeployableContainer.class.getName());
    private static final String SYSPROP_KEY_JAVA_HOME = "java.home";
    private static final String DEBUG_AGENT_STRING = "-agentlib:jdwp=transport=dt_socket,address=8787,server=y,suspend=y";
    private static final String TARGET = "target";
    private static final String SERVER_MAIN_CLASS_FQN = "org.jboss.arquillian.container.se.server.Main";
    private static final String SYSTEM_PROPERTY_SWITCH = "-D";
    private static final String EQUALS = "=";

    private boolean debugModeEnabled;
    private boolean keepDeploymentArchives;
    private Process process;
    private List<File> materializedFiles;
    private List<File> classpathDependencies;
    private String host;
    private int port;
    private String librariesPath;
    private List<String> additionalJavaOpts;
    private int waitTime;

    @Override
    public Class<ManagedSEContainerConfiguration> getConfigurationClass() {
        return ManagedSEContainerConfiguration.class;
    }

    public void setup(ManagedSEContainerConfiguration configuration) {
        debugModeEnabled = configuration.isDebug();
        host = configuration.getHost();
        port = configuration.getPort();
        materializedFiles = new ArrayList<>();
        classpathDependencies = new ArrayList<>();
        librariesPath = configuration.getLibrariesPath();
        keepDeploymentArchives = configuration.isKeepDeploymentArchives();
        additionalJavaOpts = initAdditionalJavaOpts(configuration.getAdditionalJavaOpts());
        configureLogging(configuration);
        waitTime = configuration.getWaitTime() > 0 ? configuration.getWaitTime() : 10;
    }

    private List<String> initAdditionalJavaOpts(String opts) {
        if (opts == null || opts.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> additionalOpts = new ArrayList<>();

        for (String option : opts.split("\\s+")) {
            additionalOpts.add(option);
        }
        return additionalOpts;
    }

    private void configureLogging(ManagedSEContainerConfiguration configuration) {
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(configuration.getLogLevel());
        LOGGER.setUseParentHandlers(false);
        LOGGER.addHandler(consoleHandler);
        LOGGER.setLevel(configuration.getLogLevel());
    }

    @Override
    public void start() throws LifecycleException {
    }

    @Override
    public void stop() throws LifecycleException {
    }

    @Override
    public ProtocolDescription getDefaultProtocol() {
        return new ProtocolDescription(CustomJMXProtocol.NAME);
    }

    @Override
    public void deploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void undeploy(Descriptor descriptor) throws DeploymentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void undeploy(Archive<?> archive) throws DeploymentException {
        LOGGER.info("Undeploying " + archive.getName());
        if (!keepDeploymentArchives) {
            for (File materializedFile : materializedFiles) {
                if (materializedFile.isDirectory()) {
                    try {
                        FileDeploymentUtils.deleteRecursively(materializedFile.toPath());
                    } catch (IOException e) {
                        LOGGER.warning("Could not delete materialized directory: " + materializedFile);
                    }
                } else {
                    materializedFile.delete();
                }
            }
        }
        // Kill the subprocess (test JVM)
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (final InterruptedException e) {
                Thread.interrupted();
                throw new RuntimeException("Interrupted while awaiting server daemon process termination", e);
            }
        }
    }

    @Override
    public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException {
        LOGGER.info("Deploying " + archive.getName());

        // First of all clear the list of previously materialized deployments - otherwise the class path would grow indefinitely
        materializedFiles.clear();

        // Create a new classpath
        classpathDependencies.clear();

        if (ClassPath.isRepresentedBy(archive)) {
            for (Node child : archive.get(ClassPath.ROOT_ARCHIVE_PATH).getChildren()) {
                Asset asset = child.getAsset();
                if (asset instanceof ArchiveAsset) {
                    Archive<?> assetArchive = ((ArchiveAsset) asset).getArchive();
                    if (ClassPathDirectory.isRepresentedBy(assetArchive)) {
                        materializeDirectory(assetArchive);
                    } else {
                        materializeArchive(assetArchive);
                    }
                }
            }
        } else {
            materializeArchive(archive);
        }

        Properties systemProperties = getSystemProperties(archive);
        readJarFilesFromDirectory();
        addTestResourcesDirectory(systemProperties);

        List<String> processCommand = buildProcessCommand(systemProperties);
        logExecutedCommand(processCommand);
        // Launch the process
        final ProcessBuilder processBuilder = new ProcessBuilder(processCommand);

        String path = systemProperties.getProperty("container.user.dir");
        if (path != null) {
            processBuilder.directory(new File(path));
        }

        processBuilder.environment().put("JAVA_HOME", new File(System.getProperty(SYSPROP_KEY_JAVA_HOME)).getAbsolutePath());

        processBuilder.redirectErrorStream(true);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        try {
            process = processBuilder.start();
        } catch (final IOException e) {
            throw new DeploymentException("Could not start process", e);
        }

        int finalWaitTime = debugModeEnabled ? (3 * waitTime) : waitTime;

        // Wait for socket connection
        if (!isServerStarted(host, port, finalWaitTime)) {
            throw new DeploymentException("Child JVM process failed to start within " + finalWaitTime + " seconds.");
        }
        if (!isJMXTestRunnerMBeanRegistered(host, port, finalWaitTime)) {
            throw new DeploymentException("JMXTestRunnerMBean not registered within " + finalWaitTime + " seconds.");
        }

        ProtocolMetaData protocolMetaData = new ProtocolMetaData();
        protocolMetaData.addContext(new JMXContext(host, port));
        return protocolMetaData;
    }

    private Properties getSystemProperties(final Archive<?> archive) throws DeploymentException {
        Node systemPropertiesNode = archive.get(ClassPath.SYSTEM_PROPERTIES_ARCHIVE_PATH);
        if (systemPropertiesNode != null) {
            try (InputStream in = systemPropertiesNode.getAsset().openStream()) {
                Properties systemProperties = new Properties();
                systemProperties.load(in);
                return systemProperties;
            } catch (IOException e) {
                throw new DeploymentException("Could not load system properties", e);
            }
        }
        return null;
    }

    private boolean isServerStarted(final String host, final int port, int waitTime) {
        return new Await(waitTime, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                try (Socket ignored = new Socket(host, port)) {
                    return true;
                }
            }
        }).start();
    }

    private boolean isJMXTestRunnerMBeanRegistered(final String host, final int port, int waitTime) throws DeploymentException {
        // Taken from org.jboss.arquillian.container.spi.client.protocol.metadata.JMXContext
        final String jmxServiceUrl = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
        try (JMXConnector jmxc = JMXConnectorFactory.connect(new JMXServiceURL(jmxServiceUrl), null)) {
            final MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
            return new Await(waitTime, new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    mbsc.getObjectInstance(new ObjectName(JMXTestRunnerMBean.OBJECT_NAME));
                    LOGGER.fine("JMXTestRunnerMBean registered with the remote MBean server at: " + jmxServiceUrl);
                    return true;
                }
            }).start();
        } catch (IOException e) {
            throw new DeploymentException("Could not verify JMXTestRunnerMBean registration", e);
        }
    }

    private void materializeArchive(Archive<?> archive) {
        File deploymentFile = new File(TARGET.concat(File.separator).concat(archive.getName()));
        archive.as(ZipExporter.class).exportTo(deploymentFile, true);
        materializedFiles.add(deploymentFile);
    }

    private void materializeDirectory(Archive<?> archive) throws DeploymentException {
        if (archive.getContent().isEmpty()) {
            // Do not materialize an empty directory
            return;
        }
        File entryDirectory = new File(TARGET.concat(File.separator).concat(archive.getName()));
        try {
            if (entryDirectory.exists()) {
                // Always delete previous content
                FileDeploymentUtils.deleteContent(entryDirectory.toPath());
            } else {
                if (!entryDirectory.mkdirs()) {
                    throw new DeploymentException("Could not create class path directory: " + entryDirectory);
                }
            }
            for (Node child : archive.get(ClassPath.ROOT_ARCHIVE_PATH).getChildren()) {
                Asset asset = child.getAsset();
                if (asset instanceof ClassAsset) {
                    FileDeploymentUtils.materializeClass(entryDirectory, (ClassAsset) asset);
                } else if (asset == null) {
                    FileDeploymentUtils.materializeSubdirectories(entryDirectory, child);
                }
            }
        } catch (IOException e) {
            throw new DeploymentException("Could not materialize class path directory: " + archive.getName(), e);
        }
        materializedFiles.add(entryDirectory);
    }

    private List<String> buildProcessCommand(Properties properties) {
        final List<String> command = new ArrayList<String>();
        final File javaHome = new File(System.getProperty(SYSPROP_KEY_JAVA_HOME));
        command.add(javaHome.getAbsolutePath() + File.separator + "bin" + File.separator + "java");
        command.add("-cp");
        StringBuilder builder = new StringBuilder();
        Set<File> classPathEntries = new HashSet<>(materializedFiles);
        classPathEntries.addAll(classpathDependencies);
        for (Iterator<File> iterator = classPathEntries.iterator(); iterator.hasNext();) {
            builder.append(iterator.next().getPath());
            if (iterator.hasNext()) {
                builder.append(File.pathSeparator);
            }
        }
        command.add(builder.toString());
        command.add("-Dcom.sun.management.jmxremote");
        command.add("-Dcom.sun.management.jmxremote.port=" + port);
        command.add("-Dcom.sun.management.jmxremote.authenticate=false");
        command.add("-Dcom.sun.management.jmxremote.ssl=false");

        if (debugModeEnabled) {
            command.add(DEBUG_AGENT_STRING);
        }
        for (String option : additionalJavaOpts) {
            command.add(option);
        }
        if (properties != null) {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                addSystemProperty(command, entry.getKey().toString(), entry.getValue().toString());
            }
        }
        command.add(SERVER_MAIN_CLASS_FQN);
        return command;
    }

    private void addSystemProperty(List<String> command, String key, String value) {
        command.add(SYSTEM_PROPERTY_SWITCH + key + EQUALS + value);
    }

    private void readJarFilesFromDirectory() throws DeploymentException {
        if (librariesPath == null) {
            return;
        }
        File lib = new File(librariesPath);
        if (!lib.exists() || lib.isFile()) {
            throw new DeploymentException("Cannot read files from " + librariesPath);
        }

        File[] dep = lib.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar");
            }
        });
        classpathDependencies.addAll(Arrays.asList(dep));

    }

    private void addTestResourcesDirectory(Properties properties) throws DeploymentException {
        String testResources = properties.getProperty("container.test.resources.dir");
        if (testResources != null) {
            File testDir = new File(testResources);
            if (testDir.exists() && testDir.isDirectory()) {
                classpathDependencies.add(testDir);
            }
        }
    }

    private void logExecutedCommand(List<String> processCommand) {
        if (LOGGER.isLoggable(Level.FINE)) {
            StringBuilder builder = new StringBuilder();
            for (String s : processCommand) {
                builder.append(s);
                builder.append(" ");
            }
            LOGGER.log(Level.FINE, "Executing command: " + builder);
        }
    }

}
