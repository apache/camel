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
    public static final String DOCKER_API_REQUEST_TIMEOUT = "CamelDockerRequestTimeout";
    public static final String DOCKER_CERT_PATH = "CamelDockerCertPath";
    public static final String DOCKER_HOST = "CamelDockerHost";
    public static final String DOCKER_PORT = "CamelDockerPort";
    public static final String DOCKER_MAX_PER_ROUTE_CONNECTIONS = "CamelDockerMaxPerRouteConnections";
    public static final String DOCKER_MAX_TOTAL_CONNECTIONS = "CamelDockerMaxTotalConnections";
    public static final String DOCKER_SECURE = "CamelDockerSecure";
    public static final String DOCKER_FOLLOW_REDIRECT_FILTER = "CamelDockerFollowRedirectFilter";
    public static final String DOCKER_LOGGING_FILTER = "CamelDockerLoggingFilter";
    public static final String DOCKER_TLSVERIFY = "CamelDockerTlsVerify";
    public static final String DOCKER_SOCKET_ENABLED = "CamelDockerSocketEnabled";
    public static final String DOCKER_CMD_EXEC_FACTORY = "CamelDockerCmdExecFactory";

    /**
     * List Images *
     */
    public static final String DOCKER_FILTER = "CamelDockerFilter";
    public static final String DOCKER_SHOW_ALL = "CamelDockerShowAll";

    /**
     * Common *
     */
    public static final String DOCKER_CONTAINER_ID = "CamelDockerContainerId";
    public static final String DOCKER_IMAGE_ID = "CamelDockerImageId";

    /**
     * Auth *
     */
    public static final String DOCKER_EMAIL = "CamelDockerEmail";
    public static final String DOCKER_PASSWORD = "CamelDockerPassword";
    public static final String DOCKER_SERVER_ADDRESS = "CamelDockerServerAddress";
    public static final String DOCKER_USERNAME = "CamelDockerUsername";

    /**
     * Pull *
     */
    public static final String DOCKER_REGISTRY = "CamelDockerRegistry";
    public static final String DOCKER_REPOSITORY = "CamelDockerRepository";
    public static final String DOCKER_TAG = "CamelDockerTag";

    /**
     * Push *
     */
    public static final String DOCKER_NAME = "CamelDockerName";

    /**
     * Search *
     */
    public static final String DOCKER_TERM = "CamelDockerTerm";

    /**
     * Remove *
     */
    public static final String DOCKER_FORCE = "CamelDockerForce";
    public static final String DOCKER_NO_PRUNE = "CamelDockerNoPrune";

    /**
     * Events *
     */
    public static final String DOCKER_INITIAL_RANGE = "CamelDockerInitialRange";

    /**
     * List Container *
     */
    public static final String DOCKER_BEFORE = "CamelDockerBefore";
    public static final String DOCKER_LIMIT = "CamelDockerLimit";
    public static final String DOCKER_SHOW_SIZE = "CamelDockerShowSize";
    public static final String DOCKER_SINCE = "CamelDockerSince";


    /**
     * Remove Container *
     */
    public static final String DOCKER_REMOVE_VOLUMES = "CamelDockerRemoveVolumes";

    /**
     * Attach Container *
     */
    public static final String DOCKER_FOLLOW_STREAM = "CamelDockerFollowStream";
    public static final String DOCKER_LOGS = "CamelDockerLogs";
    public static final String DOCKER_STD_ERR = "CamelDockerStdErr";
    public static final String DOCKER_STD_OUT = "CamelDockerStdOut";
    public static final String DOCKER_TIMESTAMPS = "CamelDockerTimestamps";

    /**
     * Logs *
     */
    public static final String DOCKER_TAIL = "CamelDockerTail";
    public static final String DOCKER_TAIL_ALL = "CamelDockerTailAll";

    /**
     * Copy *
     */
    public static final String DOCKER_HOST_PATH = "CamelDockerHostPath";
    public static final String DOCKER_RESOURCE = "CamelDockerResource";

    /**
     * Diff Container *
     */
    public static final String DOCKER_CONTAINER_ID_DIFF = "CamelDockerContainerIdDiff";

    /**
     * Stop Container *
     */
    public static final String DOCKER_TIMEOUT = "CamelDockerTimeout";

    /**
     * Kill Container *
     */
    public static final String DOCKER_SIGNAL = "CamelDockerSignal";

    /**
     * Top Container *
     */
    public static final String DOCKER_PS_ARGS = "CamelDockerPsArgs";

    /**
     * Build Image *
     */
    public static final String DOCKER_NO_CACHE = "CamelDockerNoCache";
    public static final String DOCKER_QUIET = "CamelDockerQuiet";
    public static final String DOCKER_REMOVE = "CamelDockerRemove";
    public static final String DOCKER_TAR_INPUT_STREAM = "CamelDockerTarInputStream";


    /**
     * Commit Container *
     */
    public static final String DOCKER_ATTACH_STD_ERR = "CamelDockerAttachStdErr";
    public static final String DOCKER_ATTACH_STD_IN = "CamelDockerAttachStdIn";
    public static final String DOCKER_ATTACH_STD_OUT = "CamelDockerAttachStdOut";
    public static final String DOCKER_AUTHOR = "CamelDockerAuthor";
    public static final String DOCKER_CMD = "CamelDockerCmd";
    public static final String DOCKER_COMMENT = "CamelDockerComment";
    public static final String DOCKER_DISABLE_NETWORK = "CamelDockerDisableNetwork";
    public static final String DOCKER_ENV = "CamelDockerEnv";
    public static final String DOCKER_EXPOSED_PORTS = "CamelDockerExposedPorts";
    public static final String DOCKER_HOSTNAME = "CamelDockerHostname";
    public static final String DOCKER_MESSAGE = "CamelDockerMessage";
    public static final String DOCKER_MEMORY = "CamelDockerMemory";
    public static final String DOCKER_MEMORY_SWAP = "CamelDockerMemorySwap";
    public static final String DOCKER_OPEN_STD_IN = "CamelDockerOpenStdIn";
    public static final String DOCKER_PAUSE = "CamelDockerPause";
    public static final String DOCKER_PORT_SPECS = "CamelDockerPortSpecs";
    public static final String DOCKER_STD_IN_ONCE = "CamelDockerStdInOnce";
    public static final String DOCKER_TTY = "CamelDockerTty";
    public static final String DOCKER_USER = "CamelDockerUser";
    public static final String DOCKER_VOLUMES = "CamelDockerVolumes";
    public static final String DOCKER_WORKING_DIR = "CamelDockerWorkingDir";

    /**
     * Create Container *
     */
    public static final String DOCKER_CPU_SHARES = "CamelDockerCpuShares";
    public static final String DOCKER_DNS = "CamelDockerDns";
    public static final String DOCKER_ENTRYPOINT = "CamelDockerEntryPoint";
    public static final String DOCKER_HOST_CONFIG = "CamelDockerHostConfig";
    public static final String DOCKER_IMAGE = "CamelDockerImage";
    public static final String DOCKER_MEMORY_LIMIT = "CamelDockerMemoryLimit";
    public static final String DOCKER_STD_IN_OPEN = "CamelDockerStdInOpen";
    public static final String DOCKER_VOLUMES_FROM = "CamelDockerVolumesFrom";
    public static final String DOCKER_DOMAIN_NAME = "CamelDockerDomainName";


    /**
     * Start Container *
     */
    public static final String DOCKER_BINDS = "CamelDockerBinds";
    public static final String DOCKER_CAP_ADD = "CamelDockerCapAdd";
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
     * Create Network *
     * Attach to Network *
     * Remove Network *
     */
    public static final String DOCKER_NETWORK = "CamelDockerNetwork";

    /**
     * Exec *
     */
    public static final String DOCKER_DETACH = "CamelDockerDetach";
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
