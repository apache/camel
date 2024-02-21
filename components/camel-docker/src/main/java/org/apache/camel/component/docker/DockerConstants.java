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
package org.apache.camel.component.docker;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.Metadata;

/**
 * Docker Component constants
 */
public final class DockerConstants {

    public static final String DOCKER_PREFIX = "CamelDocker";

    public static final Map<String, Class<?>> DOCKER_DEFAULT_PARAMETERS = new HashMap<>();

    /**
     * Endpoint configuration defaults
     */
    public static final String DEFAULT_CMD_EXEC_FACTORY = "com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory";

    /**
     * Connectivity *
     */
    public static final String DOCKER_CLIENT_PROFILE = "CamelDockerClientProfile";

    /**
     * Connectivity *
     */
    @Metadata(description = "The request timeout for response (in seconds)", javaType = "Integer")
    public static final String DOCKER_API_REQUEST_TIMEOUT = "CamelDockerRequestTimeout";
    @Metadata(description = "The location containing the SSL certificate chain", javaType = "String")
    public static final String DOCKER_CERT_PATH = "CamelDockerCertPath";
    @Metadata(description = "The docker host", javaType = "String")
    public static final String DOCKER_HOST = "CamelDockerHost";
    @Metadata(description = "The docker port", javaType = "Integer")
    public static final String DOCKER_PORT = "CamelDockerPort";
    @Metadata(description = "The maximum route connections", javaType = "Integer")
    public static final String DOCKER_MAX_PER_ROUTE_CONNECTIONS = "CamelDockerMaxPerRouteConnections";
    @Metadata(description = "The maximum total connections", javaType = "Integer")
    public static final String DOCKER_MAX_TOTAL_CONNECTIONS = "CamelDockerMaxTotalConnections";
    @Metadata(description = "Use HTTPS communication", javaType = "Boolean", defaultValue = "false")
    public static final String DOCKER_SECURE = "CamelDockerSecure";
    public static final String DOCKER_FOLLOW_REDIRECT_FILTER = "CamelDockerFollowRedirectFilter";
    public static final String DOCKER_LOGGING_FILTER = "CamelDockerLoggingFilter";
    @Metadata(description = "Check TLS", javaType = "Boolean", defaultValue = "false")
    public static final String DOCKER_TLSVERIFY = "CamelDockerTlsVerify";
    @Metadata(description = "Socket connection mode", javaType = "Boolean", defaultValue = "true")
    public static final String DOCKER_SOCKET_ENABLED = "CamelDockerSocketEnabled";
    @Metadata(description = "The fully qualified class name of the DockerCmdExecFactory implementation to use",
              javaType = "String")
    public static final String DOCKER_CMD_EXEC_FACTORY = "CamelDockerCmdExecFactory";

    /**
     * List Images *
     */
    @Metadata(description = "With label filter", javaType = "String")
    public static final String DOCKER_FILTER = "CamelDockerFilter";
    @Metadata(description = "With show all flag", javaType = "Boolean")
    public static final String DOCKER_SHOW_ALL = "CamelDockerShowAll";

    /**
     * Common *
     */
    @Metadata(description = "The id of the container", javaType = "String")
    public static final String DOCKER_CONTAINER_ID = "CamelDockerContainerId";
    @Metadata(description = "The Image ID", javaType = "String")
    public static final String DOCKER_IMAGE_ID = "CamelDockerImageId";

    /**
     * Auth *
     */
    @Metadata(description = "The email address associated with the user", javaType = "String")
    public static final String DOCKER_EMAIL = "CamelDockerEmail";
    @Metadata(description = "The password to authenticate with", javaType = "String")
    public static final String DOCKER_PASSWORD = "CamelDockerPassword";
    @Metadata(description = "The server address for docker registry", javaType = "String")
    public static final String DOCKER_SERVER_ADDRESS = "CamelDockerServerAddress";
    @Metadata(description = "The user name to authenticate with", javaType = "String")
    public static final String DOCKER_USERNAME = "CamelDockerUsername";

    /**
     * Pull *
     */
    @Metadata(description = "The registry", javaType = "String")
    public static final String DOCKER_REGISTRY = "CamelDockerRegistry";
    @Metadata(description = "The repository", javaType = "String")
    public static final String DOCKER_REPOSITORY = "CamelDockerRepository";
    @Metadata(description = "The tag", javaType = "String")
    public static final String DOCKER_TAG = "CamelDockerTag";

    /**
     * Push *
     */
    @Metadata(description = "The image name", javaType = "String")
    public static final String DOCKER_NAME = "CamelDockerName";

    /**
     * Search *
     */
    @Metadata(description = "The term to search", javaType = "String")
    public static final String DOCKER_TERM = "CamelDockerTerm";

    /**
     * Remove *
     */
    @Metadata(description = "With force flag", javaType = "Boolean")
    public static final String DOCKER_FORCE = "CamelDockerForce";
    @Metadata(description = "With no prune flag", javaType = "Boolean")
    public static final String DOCKER_NO_PRUNE = "CamelDockerNoPrune";

    /**
     * Events *
     */
    @Metadata(description = "The initial range", javaType = "Long")
    public static final String DOCKER_INITIAL_RANGE = "CamelDockerInitialRange";

    /**
     * List Container *
     */
    @Metadata(description = "With before", javaType = "String")
    public static final String DOCKER_BEFORE = "CamelDockerBefore";
    @Metadata(description = "With limit", javaType = "Integer")
    public static final String DOCKER_LIMIT = "CamelDockerLimit";
    @Metadata(description = "With show size flag", javaType = "Boolean")
    public static final String DOCKER_SHOW_SIZE = "CamelDockerShowSize";
    @Metadata(description = "With since", javaType = "String")
    public static final String DOCKER_SINCE = "CamelDockerSince";

    /**
     * Remove Container *
     */
    @Metadata(description = "With remove volumes flag", javaType = "Boolean")
    public static final String DOCKER_REMOVE_VOLUMES = "CamelDockerRemoveVolumes";

    /**
     * Attach Container *
     */
    @Metadata(description = "With follow stream flag", javaType = "Boolean")
    public static final String DOCKER_FOLLOW_STREAM = "CamelDockerFollowStream";
    @Metadata(description = "With logs flag", javaType = "Boolean")
    public static final String DOCKER_LOGS = "CamelDockerLogs";
    @Metadata(description = "With stdErr flag", javaType = "Boolean")
    public static final String DOCKER_STD_ERR = "CamelDockerStdErr";
    @Metadata(description = "With stdOut flag", javaType = "Boolean")
    public static final String DOCKER_STD_OUT = "CamelDockerStdOut";
    @Metadata(description = "With timestamps flag", javaType = "Boolean")
    public static final String DOCKER_TIMESTAMPS = "CamelDockerTimestamps";

    /**
     * Logs *
     */
    @Metadata(description = "With Tail", javaType = "Integer")
    public static final String DOCKER_TAIL = "CamelDockerTail";
    @Metadata(description = "With tail all flag", javaType = "Boolean")
    public static final String DOCKER_TAIL_ALL = "CamelDockerTailAll";

    /**
     * Copy *
     */
    @Metadata(description = "The host path", javaType = "String")
    public static final String DOCKER_HOST_PATH = "CamelDockerHostPath";
    @Metadata(description = "The resource", javaType = "String")
    public static final String DOCKER_RESOURCE = "CamelDockerResource";

    /**
     * Diff Container *
     */
    @Metadata(description = "With container id for diff container request", javaType = "String")
    public static final String DOCKER_CONTAINER_ID_DIFF = "CamelDockerContainerIdDiff";

    /**
     * Stop Container *
     */
    @Metadata(description = "With timeout", javaType = "Integer")
    public static final String DOCKER_TIMEOUT = "CamelDockerTimeout";

    /**
     * Kill Container *
     */
    @Metadata(description = "With signal", javaType = "String")
    public static final String DOCKER_SIGNAL = "CamelDockerSignal";

    /**
     * Top Container *
     */
    @Metadata(description = "With ps args", javaType = "String")
    public static final String DOCKER_PS_ARGS = "CamelDockerPsArgs";

    /**
     * Build Image *
     */
    @Metadata(description = "With no cache flag", javaType = "Boolean")
    public static final String DOCKER_NO_CACHE = "CamelDockerNoCache";
    @Metadata(description = "With quiet flag", javaType = "Boolean")
    public static final String DOCKER_QUIET = "CamelDockerQuiet";
    @Metadata(description = "With remove flag", javaType = "Boolean")
    public static final String DOCKER_REMOVE = "CamelDockerRemove";
    public static final String DOCKER_TAR_INPUT_STREAM = "CamelDockerTarInputStream";

    /**
     * Commit Container *
     */
    @Metadata(description = "With attach StdErr flag", javaType = "Boolean")
    public static final String DOCKER_ATTACH_STD_ERR = "CamelDockerAttachStdErr";
    @Metadata(description = "With attach StdIn flag", javaType = "Boolean")
    public static final String DOCKER_ATTACH_STD_IN = "CamelDockerAttachStdIn";
    @Metadata(description = "With attach StdOut flag", javaType = "Boolean")
    public static final String DOCKER_ATTACH_STD_OUT = "CamelDockerAttachStdOut";
    @Metadata(description = "The author", javaType = "String")
    public static final String DOCKER_AUTHOR = "CamelDockerAuthor";
    @Metadata(description = "With cmd", javaType = "String or String[]")
    public static final String DOCKER_CMD = "CamelDockerCmd";
    public static final String DOCKER_COMMENT = "CamelDockerComment";
    @Metadata(description = "With disable network flag", javaType = "Boolean")
    public static final String DOCKER_DISABLE_NETWORK = "CamelDockerDisableNetwork";
    @Metadata(description = "With env", javaType = "String or String[]")
    public static final String DOCKER_ENV = "CamelDockerEnv";
    @Metadata(description = "The exposed ports", javaType = "ExposedPorts or ExposedPorts[]")
    public static final String DOCKER_EXPOSED_PORTS = "CamelDockerExposedPorts";
    @Metadata(description = "The hostname", javaType = "String")
    public static final String DOCKER_HOSTNAME = "CamelDockerHostname";
    @Metadata(description = "The message", javaType = "String")
    public static final String DOCKER_MESSAGE = "CamelDockerMessage";
    @Metadata(description = "With memory", javaType = "Integer")
    public static final String DOCKER_MEMORY = "CamelDockerMemory";
    @Metadata(description = "With memory swap", javaType = "Long or Integer")
    public static final String DOCKER_MEMORY_SWAP = "CamelDockerMemorySwap";
    @Metadata(description = "With open StdIn flag", javaType = "Boolean")
    public static final String DOCKER_OPEN_STD_IN = "CamelDockerOpenStdIn";
    @Metadata(description = "With pause flag", javaType = "Boolean")
    public static final String DOCKER_PAUSE = "CamelDockerPause";
    @Metadata(description = "With port specs", javaType = "String or String[]")
    public static final String DOCKER_PORT_SPECS = "CamelDockerPortSpecs";
    @Metadata(description = "With StdIn in once flag", javaType = "Boolean")
    public static final String DOCKER_STD_IN_ONCE = "CamelDockerStdInOnce";
    @Metadata(description = "With TTY flag", javaType = "Boolean")
    public static final String DOCKER_TTY = "CamelDockerTty";
    @Metadata(description = "With user", javaType = "String")
    public static final String DOCKER_USER = "CamelDockerUser";
    @Metadata(description = "With volumes", javaType = "Volume or Volume[]")
    public static final String DOCKER_VOLUMES = "CamelDockerVolumes";
    @Metadata(description = "With working directory", javaType = "String")
    public static final String DOCKER_WORKING_DIR = "CamelDockerWorkingDir";

    /**
     * Create Container *
     */
    @Metadata(description = "With CPU shares", javaType = "Integer")
    public static final String DOCKER_CPU_SHARES = "CamelDockerCpuShares";
    @Metadata(description = "With dns", javaType = "String or String[]")
    public static final String DOCKER_DNS = "CamelDockerDns";
    @Metadata(description = "With entrypoint", javaType = "String or String[]")
    public static final String DOCKER_ENTRYPOINT = "CamelDockerEntryPoint";
    @Metadata(description = "With host config", javaType = "com.github.dockerjava.api.model.HostConfig")
    public static final String DOCKER_HOST_CONFIG = "CamelDockerHostConfig";
    @Metadata(description = "The docker image", javaType = "String")
    public static final String DOCKER_IMAGE = "CamelDockerImage";
    @Metadata(description = "With memory limit", javaType = "Long")
    public static final String DOCKER_MEMORY_LIMIT = "CamelDockerMemoryLimit";
    @Metadata(description = "With StdIn in open flag", javaType = "Boolean")
    public static final String DOCKER_STD_IN_OPEN = "CamelDockerStdInOpen";
    @Metadata(description = "With volumes from", javaType = "VolumesFrom or VolumesFrom[]")
    public static final String DOCKER_VOLUMES_FROM = "CamelDockerVolumesFrom";
    @Metadata(description = "With domain name", javaType = "String")
    public static final String DOCKER_DOMAIN_NAME = "CamelDockerDomainName";
    @Metadata(description = "With binds", javaType = "Bind or Bind[]")
    public static final String DOCKER_BINDS = "CamelDockerBinds";

    /**
     * Start Container *
     */
    @Metadata(description = "With cap add", javaType = "Capability or Capability[]")
    public static final String DOCKER_CAP_ADD = "CamelDockerCapAdd";
    @Metadata(description = "With cap drop", javaType = "Capability or Capability[]")
    public static final String DOCKER_CAP_DROP = "CamelDockerCapDrop";
    public static final String DOCKER_DEVICES = "CamelDockeDevices";
    public static final String DOCKER_DNS_SEARCH = "CamelDockerDnsSearch";
    public static final String DOCKER_LINKS = "CamelDockerLinks";
    public static final String DOCKER_LXC_CONF = "CamelDockerLxcConf";
    public static final String DOCKER_NETWORK_MODE = "CamelNetworkMode";
    public static final String DOCKER_PORT_BINDINGS = "CamelDockerPortBinding";
    public static final String DOCKER_PORTS = "CamelDockerPorts";
    public static final String DOCKER_PRIVILEGED = "CamelDockerDnsPrivileged";
    public static final String DOCKER_PUBLISH_ALL_PORTS = "CamelDockerPublishAllPorts";
    public static final String DOCKER_RESTART_POLICY = "CamelDockerRestartPolicy";

    /**
     * Create Network * Attach to Network * Remove Network *
     */
    @Metadata(description = "The network name", javaType = "String")
    public static final String DOCKER_NETWORK = "CamelDockerNetwork";

    /**
     * Exec *
     */
    @Metadata(description = "With detach flag", javaType = "Boolean")
    public static final String DOCKER_DETACH = "CamelDockerDetach";
    @Metadata(description = "The Exec ID", javaType = "String")
    public static final String DOCKER_EXEC_ID = "CamelDockerExecId";

    static {
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_CERT_PATH, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_CLIENT_PROFILE, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_EMAIL, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_HOST, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_PASSWORD, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_PORT, Integer.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_SECURE, Boolean.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_SERVER_ADDRESS, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_USERNAME, String.class);
        DOCKER_DEFAULT_PARAMETERS.put(DOCKER_CMD_EXEC_FACTORY, String.class);
    }

    private DockerConstants() {
        // Helper class
    }

}
