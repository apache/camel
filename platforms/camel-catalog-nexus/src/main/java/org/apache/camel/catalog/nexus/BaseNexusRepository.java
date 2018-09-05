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
package org.apache.camel.catalog.nexus;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for scanning and index Maven Nexus repositories for artifacts which can be added to catalogs.
 */
public abstract class BaseNexusRepository {

    final Logger logger = LoggerFactory.getLogger(getClass());
    boolean log;

    private final Set<NexusArtifactDto> indexedArtifacts = new LinkedHashSet<>();

    private volatile ScheduledExecutorService executorService;
    private AtomicBoolean started = new AtomicBoolean();

    private int initialDelay = 10;
    private int delay = 60;
    private String nexusUrl = "http://nexus/service/local/data_index";
    private String classifier;

    public BaseNexusRepository(String classifier) {
        this.classifier = classifier;
    }

    /**
     * Sets whether to log errors and warnings to System.out.
     * By default nothing is logged.
     */
    public void setLog(boolean log) {
        this.log = log;
    }

    public String getNexusUrl() {
        return nexusUrl;
    }

    /**
     * The URL to the Nexus repository to query. The syntax should be <tt>http://nexus/service/local/data_index</tt>, where
     * nexus is the hostname.
     */
    public void setNexusUrl(String nexusUrl) {
        this.nexusUrl = nexusUrl;
    }

    public int getInitialDelay() {
        return initialDelay;
    }

    /**
     * Delay in seconds before the initial (first) scan.
     */
    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getDelay() {
        return delay;
    }

    /**
     * Delay in seconds between scanning.
     */
    public void setDelay(int delay) {
        this.delay = delay;
    }

    public String getClassifier() {
        return classifier;
    }

    /**
     * Classifier to index. Should be either <tt>component</tt>, or <tt>connector</tt>
     */
    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    /**
     * Starts the Nexus indexer.
     */
    public void start() {
        if (nexusUrl == null || nexusUrl.isEmpty()) {
            logger.warn("Nexus service not found. Indexing Nexus is not enabled!");
            return;
        }

        if (!started.compareAndSet(false, true)) {
            logger.info("NexusRepository is already started");
            return;
        }

        logger.info("Starting NexusRepository to scan every {} seconds", delay);

        executorService = Executors.newScheduledThreadPool(1);

        executorService.scheduleWithFixedDelay(() -> {
            try {
                logger.debug("Indexing Nexus {} +++ start +++", nexusUrl);
                indexNexus();
            } catch (Throwable e) {
                if (e.getMessage().contains("UnknownHostException")) {
                    // less noise if its unknown host
                    logger.warn("Error indexing Nexus " + nexusUrl + " due unknown hosts: " + e.getMessage());
                } else {
                    logger.warn("Error indexing Nexus " + nexusUrl + " due " + e.getMessage(), e);
                }
            } finally {
                logger.debug("Indexing Nexus {} +++ end +++", nexusUrl);
            }
        }, initialDelay, delay, TimeUnit.SECONDS);
    }

    /**
     * Stops the Nexus indexer.
     */
    public void stop() {
        logger.info("Stopping NexusRepository");
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
        indexedArtifacts.clear();

        started.set(false);
    }

    /**
     * Callback when new artifacts has been discovered in Nexus
     */
    abstract void onNewArtifacts(Set<NexusArtifactDto> newArtifacts);

    protected URL createNexusUrl() throws MalformedURLException {
        String query = nexusUrl + "?q=" + getClassifier();
        return new URL(query);
    }

    /**
     * Creates the url to download the artifact.
     *
     * @param dto  the artifact
     * @return the url to download
     */
    protected String createArtifactURL(NexusArtifactDto dto) {
        return dto.getArtifactLink();
    }

    /**
     * Runs the task to index nexus for new artifacts
     */
    protected void indexNexus() throws Exception {
        // must have q parameter so use component to find all component

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);

        DocumentBuilder documentBuilder = factory.newDocumentBuilder();

        URL url = createNexusUrl();
        InputStream is = url.openStream();
        try {
            Document dom = documentBuilder.parse(is);

            XPathFactory xpFactory = XPathFactory.newInstance();
            XPath exp = xpFactory.newXPath();
            NodeList list = (NodeList) exp.evaluate("//data/artifact", dom, XPathConstants.NODESET);

            Set<NexusArtifactDto> newArtifacts = new LinkedHashSet<>();
            for (int i = 0; i < list.getLength(); i++) {
                Node node = list.item(i);

                String g = getNodeText(node.getChildNodes(), "groupId");
                String a = getNodeText(node.getChildNodes(), "artifactId");
                String v = getNodeText(node.getChildNodes(), "version");
                String l = getNodeText(node.getChildNodes(), "artifactLink");

                if (g != null & a != null & v != null & l != null) {
                    NexusArtifactDto dto = new NexusArtifactDto();
                    dto.setGroupId(g);
                    dto.setArtifactId(a);
                    dto.setVersion(v);
                    dto.setArtifactLink(l);

                    logger.debug("Found: {}:{}:{}", dto.getGroupId(), dto.getArtifactId(), dto.getVersion());

                    // is it a new artifact
                    boolean newArtifact = true;
                    for (NexusArtifactDto existing : indexedArtifacts) {
                        if (existing.getGroupId().equals(dto.getGroupId())
                            && existing.getArtifactId().equals(dto.getArtifactId())
                            && existing.getVersion().equals(dto.getVersion())) {
                            newArtifact = false;
                            break;
                        }
                    }
                    if (newArtifact) {
                        newArtifacts.add(dto);
                    }
                }
            }

            // if there is any new artifacts then process them
            if (!newArtifacts.isEmpty()) {
                onNewArtifacts(newArtifacts);
            }
        } finally {
            close(is);
        }
    }

    private static String getNodeText(NodeList list, String name) {
        for (int i = 0; i < list.getLength(); i++) {
            Node child = list.item(i);
            if (name.equals(child.getNodeName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private static void close(Closeable... closeables) {
        for (Closeable c : closeables) {
            try {
                if (c != null) {
                    c.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

}
