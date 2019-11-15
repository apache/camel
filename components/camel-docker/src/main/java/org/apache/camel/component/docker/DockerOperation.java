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
 * Operations the Docker Component supports
 */
public enum DockerOperation {

    /**
     * Events *
     */
    EVENTS("events", false, true, false, 
            DockerConstants.DOCKER_INITIAL_RANGE, Long.class),

    /**
     * Stats *
     */
    STATS("stats", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, Long.class),

    /**
     * General *
     */
    AUTH("auth", false, true, false,
            DockerConstants.DOCKER_USERNAME, String.class,
            DockerConstants.DOCKER_PASSWORD, String.class,
            DockerConstants.DOCKER_EMAIL, String.class,
            DockerConstants.DOCKER_SERVER_ADDRESS, String.class),
    INFO("info", false, true, false),
    PING("ping", false, true, false),
    VERSION("version", false, true, false),

    /**
     * Images *
     */
    BUILD_IMAGE("imagebuild", false, true, true,
            DockerConstants.DOCKER_NO_CACHE, Boolean.class,
            DockerConstants.DOCKER_REMOVE, Boolean.class,
            DockerConstants.DOCKER_QUIET, Boolean.class),
    CREATE_IMAGE("imagecreate", false, true, false,
            DockerConstants.DOCKER_REPOSITORY, String.class),
    INSPECT_IMAGE("imageinspect", false, true, false,
            DockerConstants.DOCKER_IMAGE_ID, String.class,
            DockerConstants.DOCKER_NO_PRUNE, Boolean.class,
            DockerConstants.DOCKER_FORCE, Boolean.class),
    LIST_IMAGES("imagelist", false, true, false,
            DockerConstants.DOCKER_FILTER, String.class,
            DockerConstants.DOCKER_SHOW_ALL, Boolean.class),
    PULL_IMAGE("imagepull", false, true, true,
            DockerConstants.DOCKER_REGISTRY, String.class,
            DockerConstants.DOCKER_TAG, String.class,
            DockerConstants.DOCKER_REPOSITORY, String.class),
    PUSH_IMAGE("imagepush", false, true, true,
            DockerConstants.DOCKER_NAME, String.class,
            DockerConstants.DOCKER_TAG, String.class),
    REMOVE_IMAGE("imageremove", false, true, false,
            DockerConstants.DOCKER_IMAGE_ID, String.class,
            DockerConstants.DOCKER_FORCE, Boolean.class,
            DockerConstants.DOCKER_NO_PRUNE, String.class),
    SEARCH_IMAGES("imagesearch", false, true, false,
            DockerConstants.DOCKER_TERM, String.class),
    TAG_IMAGE("imagetag", false, true, false,
            DockerConstants.DOCKER_FORCE, Boolean.class,
            DockerConstants.DOCKER_IMAGE_ID, String.class,
            DockerConstants.DOCKER_REPOSITORY, String.class),

    /**
     * Container *
     */
    ATTACH_CONTAINER("containerattach", false, true, true, 
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_FOLLOW_STREAM, Boolean.class,
            DockerConstants.DOCKER_LOGS, Boolean.class,
            DockerConstants.DOCKER_STD_OUT, Boolean.class,
            DockerConstants.DOCKER_STD_ERR, Boolean.class,
            DockerConstants.DOCKER_TIMESTAMPS, Boolean.class),
    COMMIT_CONTAINER("containercommit", false, true, false,
            DockerConstants.DOCKER_ATTACH_STD_ERR, Boolean.class,
            DockerConstants.DOCKER_ATTACH_STD_IN, Boolean.class,
            DockerConstants.DOCKER_ATTACH_STD_OUT, Boolean.class,
            DockerConstants.DOCKER_AUTHOR, String.class,
            DockerConstants.DOCKER_CMD, String.class,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_DISABLE_NETWORK, String.class,
            DockerConstants.DOCKER_ENV, String.class,
            DockerConstants.DOCKER_EXPOSED_PORTS, String.class,
            DockerConstants.DOCKER_HOSTNAME, String.class,
            DockerConstants.DOCKER_MEMORY, Integer.class,
            DockerConstants.DOCKER_MEMORY_SWAP, Integer.class,
            DockerConstants.DOCKER_MESSAGE, String.class,
            DockerConstants.DOCKER_OPEN_STD_IN, Boolean.class,
            DockerConstants.DOCKER_PAUSE, Boolean.class,
            DockerConstants.DOCKER_PORT_SPECS, String.class,
            DockerConstants.DOCKER_REPOSITORY, String.class,
            DockerConstants.DOCKER_STD_IN_ONCE, Boolean.class,
            DockerConstants.DOCKER_TAG, String.class,
            DockerConstants.DOCKER_TTY, Boolean.class,
            DockerConstants.DOCKER_USER, String.class,
            DockerConstants.DOCKER_VOLUMES, String.class,
            DockerConstants.DOCKER_WORKING_DIR, String.class),
    COPY_FILE_CONTAINER("containercopyfile", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_HOST_PATH, String.class,
            DockerConstants.DOCKER_RESOURCE, String.class),
    CREATE_CONTAINER("containercreate", false, true, false,
            DockerConstants.DOCKER_ATTACH_STD_ERR, Boolean.class,
            DockerConstants.DOCKER_ATTACH_STD_IN, Boolean.class,
            DockerConstants.DOCKER_ATTACH_STD_OUT, Boolean.class,
            DockerConstants.DOCKER_CAP_ADD, String.class,
            DockerConstants.DOCKER_CAP_DROP, String.class,
            DockerConstants.DOCKER_CMD, String.class,
            DockerConstants.DOCKER_CPU_SHARES, Integer.class,
            DockerConstants.DOCKER_DISABLE_NETWORK, Boolean.class,
            DockerConstants.DOCKER_DNS, String.class,
            DockerConstants.DOCKER_DOMAIN_NAME, String.class,
            DockerConstants.DOCKER_ENTRYPOINT, String.class,
            DockerConstants.DOCKER_ENV, String.class,
            DockerConstants.DOCKER_EXPOSED_PORTS, String.class,
            DockerConstants.DOCKER_HOST_CONFIG, String.class,
            DockerConstants.DOCKER_HOSTNAME, String.class,
            DockerConstants.DOCKER_IMAGE, String.class,
            DockerConstants.DOCKER_NAME, String.class,
            DockerConstants.DOCKER_PORT_SPECS, String.class,
            DockerConstants.DOCKER_STD_IN_OPEN, Boolean.class,
            DockerConstants.DOCKER_STD_IN_ONCE, Boolean.class,
            DockerConstants.DOCKER_TTY, Boolean.class,
            DockerConstants.DOCKER_USER, String.class,
            DockerConstants.DOCKER_VOLUMES, String.class,
            DockerConstants.DOCKER_VOLUMES_FROM, String.class,
            DockerConstants.DOCKER_WORKING_DIR, String.class),
    DIFF_CONTAINER("containerdiff", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class),
    INSPECT_CONTAINER("inspectcontainer", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class),
    KILL_CONTAINER("containerkill", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_SIGNAL, String.class),
    LIST_CONTAINERS("containerlist", false, true, false,
            DockerConstants.DOCKER_BEFORE, String.class,
            DockerConstants.DOCKER_LIMIT, String.class,
            DockerConstants.DOCKER_SHOW_ALL, Boolean.class,
            DockerConstants.DOCKER_SHOW_SIZE, Boolean.class,
            DockerConstants.DOCKER_SINCE, String.class),
    LOG_CONTAINER("containerlog", false, true, true, 
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_FOLLOW_STREAM, Boolean.class,
            DockerConstants.DOCKER_STD_ERR, Boolean.class,
            DockerConstants.DOCKER_STD_OUT, Boolean.class,
            DockerConstants.DOCKER_TAIL, Integer.class,
            DockerConstants.DOCKER_TAIL_ALL, Boolean.class,
            DockerConstants.DOCKER_TIMESTAMPS, Boolean.class),
    PAUSE_CONTAINER("containerpause", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class),
    RESTART_CONTAINER("containerrestart", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_TIMEOUT, Integer.class),
    REMOVE_CONTAINER("containerremove", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_FORCE, Boolean.class,
            DockerConstants.DOCKER_REMOVE_VOLUMES, Boolean.class),
    START_CONTAINER("containerstart", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class),
    STOP_CONTAINER("containerstop", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_TIMEOUT, Integer.class),
    TOP_CONTAINER("containertop", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class,
            DockerConstants.DOCKER_PS_ARGS, String.class),
    UNPAUSE_CONTAINER("containerunpause", false, true, false,
            DockerConstants.DOCKER_CONTAINER_ID, String.class),
    CREATE_NETWORK("networkcreate", false, true, false,
        DockerConstants.DOCKER_NETWORK, String.class),
    REMOVE_NETWORK("networkremove", false, true, false,
        DockerConstants.DOCKER_NETWORK, String.class),
    CONNECT_NETWORK("networkconnect", false, true, false,
        DockerConstants.DOCKER_NETWORK, String.class,
        DockerConstants.DOCKER_CONTAINER_ID, String.class),
    WAIT_CONTAINER("containerwait", false, true, true, 
            DockerConstants.DOCKER_CONTAINER_ID, String.class),


    /**
     * Exec *
     */
    EXEC_CREATE("execcreate", false, true, false,
            DockerConstants.DOCKER_ATTACH_STD_ERR, Boolean.class,
            DockerConstants.DOCKER_ATTACH_STD_IN, Boolean.class,
            DockerConstants.DOCKER_ATTACH_STD_OUT, Boolean.class,
            DockerConstants.DOCKER_TTY, Boolean.class),
    EXEC_START("execstart", false, true, true, 
            DockerConstants.DOCKER_DETACH, Boolean.class,
            DockerConstants.DOCKER_EXEC_ID, String.class,
            DockerConstants.DOCKER_TTY, Boolean.class);


    private String text;
    private boolean canConsume;
    private boolean canProduce;
    private boolean async;
    private Map<String, Class<?>> parameters;


    DockerOperation(String text, boolean canConsume, boolean canProduce, boolean async, Object... params) {

        this.text = text;
        this.canConsume = canConsume;
        this.canProduce = canProduce;
        this.async = async;

        parameters = new HashMap<>();

        if (params.length > 0) {

            if (params.length % 2 != 0) {
                throw new IllegalArgumentException("Invalid parameter list, "
                        + "must be of the form 'String name1, Class class1, String name2, Class class2...");
            }

            int nParameters = params.length / 2;

            for (int i = 0; i < nParameters; i++) {
                parameters.put((String) params[i * 2], (Class<?>) params[i * 2 + 1]);
            }
        }

    }

    @Override
    public String toString() {
        return text;
    }

    public boolean canConsume() {
        return canConsume;
    }

    public boolean canProduce() {
        return canProduce;
    }

    public boolean isAsync() {
        return async;
    }
    
    public Map<String, Class<?>> getParameters() {
        return parameters;
    }

    public static DockerOperation getDockerOperation(String name) {
        for (DockerOperation dockerOperation : DockerOperation.values()) {
            if (dockerOperation.toString().equals(name)) {
                return dockerOperation;
            }
        }

        return null;
    }

}
