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
package org.apache.camel.component.file.remote;

import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

import com.jcraft.jsch.JSch;
import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.util.StringHelper;

/**
 * Secure FTP Component
 */
@Component("sftp")
@ManagedResource(description = "Managed SFTP Component")
public class SftpComponent extends RemoteFileComponent<SftpRemoteFile> {

    @Metadata(label = "security", defaultValue = "no", enums = "no,yes", security = "insecure:ssl",
              description = "Sets whether to use strict host key checking globally for all endpoints. "
                            + "Setting this to 'no' (the default) disables host key verification and makes SFTP connections "
                            + "vulnerable to man-in-the-middle attacks. Use 'yes' in production environments.")
    private String strictHostKeyChecking = "no";
    @Metadata(label = "security", security = "secret",
              description = "Sets the known_hosts file globally, so that the SFTP endpoints can do host key verification.")
    private String knownHostsFile;
    @Metadata(label = "security", security = "secret",
              description = "Sets the known_hosts file (loaded from classpath by default) globally, so that the SFTP endpoints can do host key verification.")
    private String knownHostsUri;
    @Metadata(label = "security", security = "secret",
              description = "Sets the known_hosts from the byte array globally, so that the SFTP endpoints can do host key verification.")
    private byte[] knownHosts;
    @Metadata(label = "security", defaultValue = "true",
              description = "If knownHostFile has not been explicit configured then use the host file from System.getProperty(user.home)/.ssh/known_hosts")
    private boolean useUserKnownHostsFile = true;
    @Metadata(label = "security", defaultValue = "false",
              description = "If knownHostFile does not exist, then attempt to auto-create the path and file (beware that the file will be created by the current user of the running Java process, which may not have file permission).")
    private boolean autoCreateKnownHostsFile;

    public SftpComponent() {
    }

    public SftpComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected GenericFileEndpoint<SftpRemoteFile> buildFileEndpoint(
            String uri, String remaining, Map<String, Object> parameters)
            throws Exception {
        // get the base uri part before the options as they can be non URI valid
        // such as the expression using $ chars
        // and the URI constructor will regard $ as an illegal character and we
        // dont want to enforce end users to
        // to escape the $ for the expression (file language)
        String baseUri = StringHelper.before(uri, "?", uri);

        // lets make sure we create a new configuration as each endpoint can
        // customize its own version
        SftpConfiguration config = new SftpConfiguration(new URI(baseUri));

        // apply component-level host key verification settings as defaults
        // (endpoint URI parameters will override these)
        if (strictHostKeyChecking != null) {
            config.setStrictHostKeyChecking(strictHostKeyChecking);
        }
        if (knownHostsFile != null) {
            config.setKnownHostsFile(knownHostsFile);
        }
        if (knownHostsUri != null) {
            config.setKnownHostsUri(knownHostsUri);
        }
        if (knownHosts != null) {
            config.setKnownHosts(knownHosts);
        }
        config.setUseUserKnownHostsFile(useUserKnownHostsFile);
        config.setAutoCreateKnownHostsFile(autoCreateKnownHostsFile);

        FtpUtils.ensureRelativeFtpDirectory(this, config);

        return new SftpEndpoint(uri, this, config);
    }

    @Override
    protected void afterPropertiesSet(GenericFileEndpoint<SftpRemoteFile> endpoint) throws Exception {
        // noop
    }

    public String getStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    /**
     * Sets whether to use strict host key checking globally for all endpoints. Setting this to 'no' (the default)
     * disables host key verification and makes SFTP connections vulnerable to man-in-the-middle attacks. Use 'yes' in
     * production environments.
     */
    public void setStrictHostKeyChecking(String strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public String getKnownHostsFile() {
        return knownHostsFile;
    }

    /**
     * Sets the known_hosts file globally, so that the SFTP endpoints can do host key verification.
     */
    public void setKnownHostsFile(String knownHostsFile) {
        this.knownHostsFile = knownHostsFile;
    }

    public String getKnownHostsUri() {
        return knownHostsUri;
    }

    /**
     * Sets the known_hosts file (loaded from classpath by default) globally, so that the SFTP endpoints can do host key
     * verification.
     */
    public void setKnownHostsUri(String knownHostsUri) {
        this.knownHostsUri = knownHostsUri;
    }

    public byte[] getKnownHosts() {
        return knownHosts;
    }

    /**
     * Sets the known_hosts from the byte array globally, so that the SFTP endpoints can do host key verification.
     */
    public void setKnownHosts(byte[] knownHosts) {
        this.knownHosts = knownHosts;
    }

    public boolean isUseUserKnownHostsFile() {
        return useUserKnownHostsFile;
    }

    /**
     * If knownHostFile has not been explicit configured then use the host file from
     * System.getProperty(user.home)/.ssh/known_hosts
     */
    public void setUseUserKnownHostsFile(boolean useUserKnownHostsFile) {
        this.useUserKnownHostsFile = useUserKnownHostsFile;
    }

    public boolean isAutoCreateKnownHostsFile() {
        return autoCreateKnownHostsFile;
    }

    /**
     * If knownHostFile does not exist, then attempt to auto-create the path and file (beware that the file will be
     * created by the current user of the running Java process, which may not have file permission).
     */
    public void setAutoCreateKnownHostsFile(boolean autoCreateKnownHostsFile) {
        this.autoCreateKnownHostsFile = autoCreateKnownHostsFile;
    }

    @ManagedOperation(description = "Dump JSCH Configuration")
    public String dumpConfiguration() {
        StringBuilder sb = new StringBuilder();

        Map<String, String> map = new TreeMap<>(String::compareToIgnoreCase);
        map.putAll(JSch.getConfig());
        for (var e : map.entrySet()) {
            String v = e.getValue() != null ? e.getValue() : "";
            sb.append(String.format("%s = %s%n", e.getKey(), v));
        }

        return sb.toString();
    }

}
