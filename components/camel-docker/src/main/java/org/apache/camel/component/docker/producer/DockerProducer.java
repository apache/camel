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
package org.apache.camel.component.docker.producer;

import java.io.InputStream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.AuthCmd;
import com.github.dockerjava.api.command.CommitCmd;
import com.github.dockerjava.api.command.ContainerDiffCmd;
import com.github.dockerjava.api.command.CopyFileFromContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateImageCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PauseContainerCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.command.SearchImagesCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.command.TagImageCmd;
import com.github.dockerjava.api.command.TopContainerCmd;
import com.github.dockerjava.api.command.UnpauseContainerCmd;
import com.github.dockerjava.api.command.VersionCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.ExposedPorts;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.Volumes;
import com.github.dockerjava.api.model.VolumesFrom;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.docker.DockerClientFactory;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerEndpoint;
import org.apache.camel.component.docker.DockerHelper;
import org.apache.camel.component.docker.DockerOperation;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Docker producer.
 */
public class DockerProducer extends DefaultProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerProducer.class);
    private DockerConfiguration configuration;
    private DockerComponent component;

    public DockerProducer(DockerEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.component = (DockerComponent)endpoint.getComponent();
    }

    public void process(Exchange exchange) throws Exception {

        Message message = exchange.getIn();
        DockerClient client = DockerClientFactory.getDockerClient(component, configuration, message);

        DockerOperation operation = configuration.getOperation();

        Object result = null;

        switch (operation) {

        /** General **/
        case AUTH:
            result = executeAuthRequest(client, message).exec();
            break;
        case INFO:
            result = executeInfoRequest(client, message).exec();
            break;
        case PING:
            result = executePingRequest(client, message).exec();
            break;
        case VERSION:
            result = executeVersionRequest(client, message).exec();
            break;
        case CREATE_IMAGE:
            result = executeCreateImageRequest(client, message).exec();
            break;
        case INSPECT_IMAGE:
            result = executeInspectImageRequest(client, message).exec();
            break;
        case LIST_IMAGES:
            result = executeListImagesRequest(client, message).exec();
            break;
        case REMOVE_IMAGE:
            result = executeRemoveImageRequest(client, message).exec();
            break;
        case SEARCH_IMAGES:
            result = executeSearchImageRequest(client, message).exec();
            break;
        case TAG_IMAGE:
            result = executeTagImageRequest(client, message).exec();
            break;
        case COMMIT_CONTAINER:
            result = executeCommitContainerRequest(client, message).exec();
            break;
        case COPY_FILE_CONTAINER:
            result = executeCopyFileContainerRequest(client, message).exec();
            break;
        case CREATE_CONTAINER:
            result = executeCreateContainerRequest(client, message).exec();
            break;
        case DIFF_CONTAINER:
            result = executeDiffContainerRequest(client, message).exec();
            break;
        case INSPECT_CONTAINER:
            result = executeInspectContainerRequest(client, message).exec();
            break;
        case LIST_CONTAINERS:
            result = executeListContainersRequest(client, message).exec();
            break;
        case KILL_CONTAINER:
            result = executeKillContainerRequest(client, message).exec();
            break;
        case PAUSE_CONTAINER:
            result = executePauseContainerRequest(client, message).exec();
            break;
        case REMOVE_CONTAINER:
            result = executeRemoveContainerRequest(client, message).exec();
            break;
        case RESTART_CONTAINER:
            result = executeRestartContainerRequest(client, message).exec();
            break;
        case START_CONTAINER:
            result = executeStartContainerRequest(client, message).exec();
            break;
        case STOP_CONTAINER:
            result = executeStopContainerRequest(client, message).exec();
            break;
        case TOP_CONTAINER:
            result = executeTopContainerRequest(client, message).exec();
            break;
        case UNPAUSE_CONTAINER:
            result = executeUnpauseContainerRequest(client, message).exec();
            break;
        case EXEC_CREATE:
            result = executeExecCreateRequest(client, message).exec();
            break;
        default:
            throw new DockerException("Invalid operation: " + operation);
        }

        // If request included a response, set as body
        if (result != null) {
            exchange.getIn().setBody(result);
        }

    }

    /*********************
     * General Requests
     ********************/

    /**
     * Produces a Authorization request
     *
     * @param client
     * @param message
     * @return
     */
    private AuthCmd executeAuthRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Auth Request");

        AuthCmd authCmd = client.authCmd();

        AuthConfig authConfig = client.authConfig();

        if (authCmd != null) {
            authCmd.withAuthConfig(authConfig);
        }

        return authCmd;
    }

    /**
     * Produces a platform information request
     *
     * @param client
     * @param message
     * @return
     */
    private InfoCmd executeInfoRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Info Request");

        InfoCmd infoCmd = client.infoCmd();

        return infoCmd;

    }

    /**
     * Executes a ping platform request
     *
     * @param client
     * @param message
     * @return
     */
    private PingCmd executePingRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Ping Request");

        PingCmd pingCmd = client.pingCmd();

        return pingCmd;

    }

    /**
     * Executes a platform version request
     *
     * @param client
     * @param message
     * @return
     */
    private VersionCmd executeVersionRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Version Request");

        VersionCmd versionCmd = client.versionCmd();

        return versionCmd;

    }

    /*********************
     * Image Requests
     ********************/

    /**
     * Performs a create image request
     *
     * @param client
     * @param message
     * @return
     */
    private CreateImageCmd executeCreateImageRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Create Image Request");

        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);

        InputStream inputStream = message.getBody(InputStream.class);

        if (repository == null || inputStream == null) {
            throw new IllegalArgumentException("Inputstream must be present on message body and repository must be specified");
        }

        CreateImageCmd createImageCmd = client.createImageCmd(repository, inputStream);

        return createImageCmd;

    }

    /**
     * Produces a inspect image request
     *
     * @param client
     * @param message
     * @return
     */
    private InspectImageCmd executeInspectImageRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Inspect Image Request");

        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);

        ObjectHelper.notNull(imageId, "Image ID must be specified");

        InspectImageCmd inspectImageCmd = client.inspectImageCmd(imageId);

        return inspectImageCmd;

    }

    /**
     * Performs a list images request
     *
     * @param client
     * @param message
     * @return
     */
    private ListImagesCmd executeListImagesRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Images List Request");

        ListImagesCmd listImagesCmd = client.listImagesCmd();

        String filter = DockerHelper.getProperty(DockerConstants.DOCKER_FILTER, configuration, message, String.class);

        if (filter != null) {
            listImagesCmd.withLabelFilter(filter);
        }

        Boolean showAll = DockerHelper.getProperty(DockerConstants.DOCKER_SHOW_ALL, configuration, message, Boolean.class);

        if (showAll != null) {
            listImagesCmd.withShowAll(showAll);
        }

        return listImagesCmd;

    }

    /**
     * Produces a remove image request
     *
     * @param client
     * @param message
     * @return
     */
    private RemoveImageCmd executeRemoveImageRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Remove Image Request");

        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);

        ObjectHelper.notNull(imageId, "Image ID must be specified");

        RemoveImageCmd removeImagesCmd = client.removeImageCmd(imageId);

        Boolean force = DockerHelper.getProperty(DockerConstants.DOCKER_FORCE, configuration, message, Boolean.class);

        if (force != null) {
            removeImagesCmd.withForce(force);
        }

        Boolean noPrune = DockerHelper.getProperty(DockerConstants.DOCKER_NO_PRUNE, configuration, message, Boolean.class);

        if (noPrune != null) {
            removeImagesCmd.withNoPrune(noPrune);
        }

        return removeImagesCmd;

    }

    /**
     * Produces a search image request
     *
     * @param client
     * @param message
     * @return
     */
    private SearchImagesCmd executeSearchImageRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Search Image Request");

        String term = DockerHelper.getProperty(DockerConstants.DOCKER_TERM, configuration, message, String.class);

        ObjectHelper.notNull(term, "Term must be specified");

        SearchImagesCmd searchImagesCmd = client.searchImagesCmd(term);

        return searchImagesCmd;

    }

    /**
     * Produces a tag image request
     *
     * @param client
     * @param message
     * @return
     */
    private TagImageCmd executeTagImageRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Tag Image Request");

        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);

        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);

        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (imageId == null || repository == null || tag == null) {
            throw new IllegalArgumentException("ImageId, repository and tag must be specified");
        }

        TagImageCmd tagImageCmd = client.tagImageCmd(imageId, repository, tag);

        Boolean force = DockerHelper.getProperty(DockerConstants.DOCKER_FORCE, configuration, message, Boolean.class);

        if (force != null) {
            tagImageCmd.withForce(force);
        }

        return tagImageCmd;

    }

    /*********************
     * Container Requests
     ********************/

    /**
     * Produces a commit container request
     *
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private CommitCmd executeCommitContainerRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Commit Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        CommitCmd commitCmd = client.commitCmd(containerId);

        String author = DockerHelper.getProperty(DockerConstants.DOCKER_AUTHOR, configuration, message, String.class);

        if (author != null) {
            commitCmd.withAuthor(author);
        }

        Boolean attachStdErr = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_ERR, configuration, message, Boolean.class);

        if (attachStdErr != null) {
            commitCmd.withAttachStderr(attachStdErr);
        }

        Boolean attachStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_IN, configuration, message, Boolean.class);

        if (attachStdIn != null) {
            commitCmd.withAttachStdin(attachStdIn);
        }

        Boolean attachStdOut = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_OUT, configuration, message, Boolean.class);

        if (attachStdOut != null) {
            commitCmd.withAttachStdout(attachStdOut);
        }

        String[] cmds = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CMD, message);

        if (cmds != null) {
            commitCmd.withCmd(cmds);
        }

        Boolean disableNetwork = DockerHelper.getProperty(DockerConstants.DOCKER_DISABLE_NETWORK, configuration, message, Boolean.class);

        if (disableNetwork != null) {
            commitCmd.withDisableNetwork(disableNetwork);
        }

        String[] envs = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_ENV, message);

        if (envs != null) {
            commitCmd.withEnv(envs);
        }

        ExposedPorts exposedPorts = DockerHelper.getProperty(DockerConstants.DOCKER_EXPOSED_PORTS, configuration, message, ExposedPorts.class);

        if (exposedPorts != null) {
            commitCmd.withExposedPorts(exposedPorts);
        }

        String hostname = DockerHelper.getProperty(DockerConstants.DOCKER_HOSTNAME, configuration, message, String.class);

        if (hostname != null) {
            commitCmd.withHostname(hostname);
        }

        Integer memory = DockerHelper.getProperty(DockerConstants.DOCKER_MEMORY, configuration, message, Integer.class);

        if (memory != null) {
            commitCmd.withMemory(memory);
        }

        Integer memorySwap = DockerHelper.getProperty(DockerConstants.DOCKER_MEMORY_SWAP, configuration, message, Integer.class);

        if (memorySwap != null) {
            commitCmd.withMemorySwap(memorySwap);
        }

        String msg = DockerHelper.getProperty(DockerConstants.DOCKER_MESSAGE, configuration, message, String.class);

        if (msg != null) {
            commitCmd.withMessage(msg);
        }

        Boolean openStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_OPEN_STD_IN, configuration, message, Boolean.class);

        if (openStdIn != null) {
            commitCmd.withOpenStdin(openStdIn);
        }

        Boolean pause = DockerHelper.getProperty(DockerConstants.DOCKER_PAUSE, configuration, message, Boolean.class);

        if (pause != null) {
            commitCmd.withPause(pause);
        }

        String[] portSpecs = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_PORT_SPECS, message);

        if (portSpecs != null) {
            commitCmd.withPortSpecs(portSpecs);
        }

        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);

        if (repository != null) {
            commitCmd.withRepository(repository);
        }

        Boolean stdInOnce = DockerHelper.getProperty(DockerConstants.DOCKER_STD_IN_ONCE, configuration, message, Boolean.class);

        if (stdInOnce != null) {
            commitCmd.withStdinOnce(stdInOnce);
        }

        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (tag != null) {
            commitCmd.withTag(tag);
        }

        Boolean tty = DockerHelper.getProperty(DockerConstants.DOCKER_TTY, configuration, message, Boolean.class);

        if (tty != null) {
            commitCmd.withTty(tty);
        }

        String user = DockerHelper.getProperty(DockerConstants.DOCKER_USER, configuration, message, String.class);

        if (user != null) {
            commitCmd.withUser(user);
        }

        Volumes volumes = DockerHelper.getProperty(DockerConstants.DOCKER_VOLUMES, configuration, message, Volumes.class);

        if (volumes != null) {
            commitCmd.withVolumes(volumes);
        }

        String workingDir = DockerHelper.getProperty(DockerConstants.DOCKER_WORKING_DIR, configuration, message, String.class);

        if (workingDir != null) {
            commitCmd.withWorkingDir(workingDir);
        }

        return commitCmd;

    }

    /**
     * Produces a copy file/folder from container request
     *
     * @param client
     * @param message
     * @return
     */
    private CopyFileFromContainerCmd executeCopyFileContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Copy File/Folder Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        String resource = DockerHelper.getProperty(DockerConstants.DOCKER_RESOURCE, configuration, message, String.class);

        if (containerId == null || resource == null) {
            throw new IllegalArgumentException("Container ID and Resource must be specified");
        }

        CopyFileFromContainerCmd copyFileContainerCmd = client.copyFileFromContainerCmd(containerId, resource);

        String hostPath = DockerHelper.getProperty(DockerConstants.DOCKER_HOST_PATH, configuration, message, String.class);

        if (hostPath != null) {
            copyFileContainerCmd.withHostPath(hostPath);
        }

        return copyFileContainerCmd;

    }

    /**
     * Produce a create container request
     *
     * @param client
     * @param message
     * @return
     */
    private CreateContainerCmd executeCreateContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Create Container Request");

        String image = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE, configuration, message, String.class);

        ObjectHelper.notNull(image, "Image must be specified");

        CreateContainerCmd createContainerCmd = client.createContainerCmd(image);

        Boolean attachStdErr = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_ERR, configuration, message, Boolean.class);

        if (attachStdErr != null) {
            createContainerCmd.withAttachStderr(attachStdErr);
        }

        Boolean attachStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_IN, configuration, message, Boolean.class);

        if (attachStdIn != null) {
            createContainerCmd.withAttachStdin(attachStdIn);
        }

        Boolean attachStdOut = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_OUT, configuration, message, Boolean.class);

        if (attachStdOut != null) {
            createContainerCmd.withAttachStdout(attachStdOut);
        }

        Capability[] capAdd = DockerHelper.getArrayProperty(DockerConstants.DOCKER_CAP_ADD, message, Capability.class);

        if (capAdd != null) {
            createContainerCmd.withCapAdd(capAdd);
        }

        Capability[] capDrop = DockerHelper.getArrayProperty(DockerConstants.DOCKER_CAP_DROP, message, Capability.class);

        if (capDrop != null) {
            createContainerCmd.withCapDrop(capDrop);
        }

        String[] cmd = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CMD, message);

        if (cmd != null) {
            createContainerCmd.withCmd(cmd);
        }

        Integer cpuShares = DockerHelper.getProperty(DockerConstants.DOCKER_CPU_SHARES, configuration, message, Integer.class);

        if (cpuShares != null) {
            createContainerCmd.withCpuShares(cpuShares);
        }

        Boolean disableNetwork = DockerHelper.getProperty(DockerConstants.DOCKER_DISABLE_NETWORK, configuration, message, Boolean.class);

        if (disableNetwork != null) {
            createContainerCmd.withNetworkDisabled(disableNetwork);
        }

        String[] dns = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_DNS, message);

        if (dns != null) {
            createContainerCmd.withDns(dns);
        }

        String domainName = DockerHelper.getProperty(DockerConstants.DOCKER_DOMAIN_NAME, configuration, message, String.class);

        if (domainName != null) {
            createContainerCmd.withDomainName(domainName);
        }

        String[] env = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_ENV, message);

        if (env != null) {
            createContainerCmd.withEnv(env);
        }

        String[] entrypoint = DockerHelper.getArrayProperty(DockerConstants.DOCKER_ENTRYPOINT, message, String.class);

        if (entrypoint != null) {
            createContainerCmd.withEntrypoint(entrypoint);
        }

        ExposedPort[] exposedPorts = DockerHelper.getArrayProperty(DockerConstants.DOCKER_EXPOSED_PORTS, message, ExposedPort.class);

        if (exposedPorts != null) {
            createContainerCmd.withExposedPorts(exposedPorts);
        }

        HostConfig hostConfig = DockerHelper.getProperty(DockerConstants.DOCKER_HOST_CONFIG, configuration, message, HostConfig.class);

        if (hostConfig != null) {
            createContainerCmd.withHostConfig(hostConfig);
        }

        String hostName = DockerHelper.getProperty(DockerConstants.DOCKER_HOSTNAME, configuration, message, String.class);

        if (hostName != null) {
            createContainerCmd.withHostName(hostName);
        }

        Long memoryLimit = DockerHelper.getProperty(DockerConstants.DOCKER_MEMORY_LIMIT, configuration, message, Long.class);

        if (memoryLimit != null) {
            createContainerCmd.withMemory(memoryLimit);
        }

        Long memorySwap = DockerHelper.getProperty(DockerConstants.DOCKER_MEMORY_SWAP, configuration, message, Long.class);

        if (memorySwap != null) {
            createContainerCmd.withMemorySwap(memorySwap);
        }

        String name = DockerHelper.getProperty(DockerConstants.DOCKER_NAME, configuration, message, String.class);

        if (name != null) {
            createContainerCmd.withName(name);
        }

        String[] portSpecs = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_PORT_SPECS, message);

        if (portSpecs != null) {
            createContainerCmd.withPortSpecs(portSpecs);
        }

        Boolean stdInOpen = DockerHelper.getProperty(DockerConstants.DOCKER_STD_IN_OPEN, configuration, message, Boolean.class);

        if (stdInOpen != null) {
            createContainerCmd.withStdinOpen(stdInOpen);
        }

        Boolean stdInOnce = DockerHelper.getProperty(DockerConstants.DOCKER_STD_IN_ONCE, configuration, message, Boolean.class);

        if (stdInOnce != null) {
            createContainerCmd.withStdInOnce(stdInOnce);
        }

        Boolean tty = DockerHelper.getProperty(DockerConstants.DOCKER_TTY, configuration, message, Boolean.class);

        if (tty != null) {
            createContainerCmd.withTty(tty);
        }

        String user = DockerHelper.getProperty(DockerConstants.DOCKER_USER, configuration, message, String.class);

        if (user != null) {
            createContainerCmd.withUser(user);
        }

        Volume[] volume = DockerHelper.getArrayProperty(DockerConstants.DOCKER_VOLUMES, message, Volume.class);

        if (volume != null) {
            createContainerCmd.withVolumes(volume);
        }

        VolumesFrom[] volumesFrom = DockerHelper.getArrayProperty(DockerConstants.DOCKER_VOLUMES_FROM, message, VolumesFrom.class);

        if (volumesFrom != null) {
            createContainerCmd.withVolumesFrom(volumesFrom);
        }

        String workingDir = DockerHelper.getProperty(DockerConstants.DOCKER_WORKING_DIR, configuration, message, String.class);

        if (workingDir != null) {
            createContainerCmd.withWorkingDir(workingDir);
        }

        return createContainerCmd;

    }

    /**
     * Produces a diff container request
     *
     * @param client
     * @param message
     * @return
     */
    private ContainerDiffCmd executeDiffContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Diff Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        ContainerDiffCmd diffContainerCmd = client.containerDiffCmd(containerId);

        String containerIdDiff = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID_DIFF, configuration, message, String.class);

        if (containerIdDiff != null) {
            diffContainerCmd.withContainerId(containerIdDiff);
        }

        return diffContainerCmd;

    }

    /**
     * Produce a inspect container request
     *
     * @param client
     * @param message
     * @return
     */
    private InspectContainerCmd executeInspectContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Inspect Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        InspectContainerCmd inspectContainerCmd = client.inspectContainerCmd(containerId);

        return inspectContainerCmd;

    }

    /**
     * Produces a kill container request
     *
     * @param client
     * @param message
     * @return
     */
    private KillContainerCmd executeKillContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Kill Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        KillContainerCmd killContainerCmd = client.killContainerCmd(containerId);

        String signal = DockerHelper.getProperty(DockerConstants.DOCKER_SIGNAL, configuration, message, String.class);

        if (signal != null) {
            killContainerCmd.withSignal(signal);
        }

        return killContainerCmd;

    }

    /**
     * Produces a list containers request
     *
     * @param client
     * @param message
     * @return
     */
    private ListContainersCmd executeListContainersRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker List Container Request");

        ListContainersCmd listContainersCmd = client.listContainersCmd();

        String before = DockerHelper.getProperty(DockerConstants.DOCKER_BEFORE, configuration, message, String.class);

        if (before != null) {
            listContainersCmd.withBefore(before);
        }

        Integer limit = DockerHelper.getProperty(DockerConstants.DOCKER_LIMIT, configuration, message, Integer.class);

        if (limit != null) {
            listContainersCmd.withLimit(limit);
        }

        Boolean showAll = DockerHelper.getProperty(DockerConstants.DOCKER_SHOW_ALL, configuration, message, Boolean.class);

        if (showAll != null) {
            listContainersCmd.withShowAll(showAll);
        }

        Boolean showSize = DockerHelper.getProperty(DockerConstants.DOCKER_SHOW_SIZE, configuration, message, Boolean.class);

        if (showSize != null) {
            listContainersCmd.withShowSize(showSize);
        }

        String since = DockerHelper.getProperty(DockerConstants.DOCKER_SINCE, configuration, message, String.class);

        if (since != null) {
            listContainersCmd.withSince(since);
        }

        return listContainersCmd;

    }

    /**
     * Produces a pause container request
     *
     * @param client
     * @param message
     * @return
     */
    private PauseContainerCmd executePauseContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Pause Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        PauseContainerCmd pauseContainerCmd = client.pauseContainerCmd(containerId);

        return pauseContainerCmd;

    }

    /**
     * Produces a remove container request
     *
     * @param client
     * @param message
     * @return
     */
    private RemoveContainerCmd executeRemoveContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Remove Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        RemoveContainerCmd removeContainerCmd = client.removeContainerCmd(containerId);

        Boolean force = DockerHelper.getProperty(DockerConstants.DOCKER_FORCE, configuration, message, Boolean.class);

        if (force != null) {
            removeContainerCmd.withForce(force);
        }

        Boolean removeVolumes = DockerHelper.getProperty(DockerConstants.DOCKER_REMOVE_VOLUMES, configuration, message, Boolean.class);

        if (removeVolumes != null) {
            removeContainerCmd.withRemoveVolumes(removeVolumes);
        }

        return removeContainerCmd;

    }

    /**
     * Produces a restart container request
     *
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private RestartContainerCmd executeRestartContainerRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Restart Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        RestartContainerCmd restartContainerCmd = client.restartContainerCmd(containerId);

        Integer timeout = DockerHelper.getProperty(DockerConstants.DOCKER_TIMEOUT, configuration, message, Integer.class);

        if (timeout != null) {
            restartContainerCmd.withtTimeout(timeout);
        }

        return restartContainerCmd;

    }

    /**
     * Produce a start container request
     *
     * @param client
     * @param message
     * @return
     */
    private StartContainerCmd executeStartContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Start Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        StartContainerCmd startContainerCmd = client.startContainerCmd(containerId);

        return startContainerCmd;

    }

    /**
     * Produces a stop container request
     *
     * @param client
     * @param message
     * @return
     */
    private StopContainerCmd executeStopContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Kill Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        StopContainerCmd stopContainerCmd = client.stopContainerCmd(containerId);

        Integer timeout = DockerHelper.getProperty(DockerConstants.DOCKER_TIMEOUT, configuration, message, Integer.class);

        if (timeout != null) {
            stopContainerCmd.withTimeout(timeout);
        }

        return stopContainerCmd;

    }

    /**
     * Produces a top container request
     *
     * @param client
     * @param message
     * @return
     */
    private TopContainerCmd executeTopContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Top Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        TopContainerCmd topContainerCmd = client.topContainerCmd(containerId);

        String psArgs = DockerHelper.getProperty(DockerConstants.DOCKER_PS_ARGS, configuration, message, String.class);

        if (psArgs != null) {
            topContainerCmd.withPsArgs(psArgs);
        }

        return topContainerCmd;

    }

    /**
     * Produces a unpause container request
     *
     * @param client
     * @param message
     * @return
     */
    private UnpauseContainerCmd executeUnpauseContainerRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Unpause Container Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        UnpauseContainerCmd unpauseContainerCmd = client.unpauseContainerCmd(containerId);

        return unpauseContainerCmd;

    }

    /*********************
     * Exec Requests
     ********************/

    /**
     * Produces a exec create request
     *
     * @param client
     * @param message
     * @return
     */
    private ExecCreateCmd executeExecCreateRequest(DockerClient client, Message message) {

        LOGGER.debug("Executing Docker Exec Create Request");

        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, "Container ID must be specified");

        ExecCreateCmd execCreateCmd = client.execCreateCmd(containerId);

        Boolean attachStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_IN, configuration, message, Boolean.class);

        Boolean attachStdErr = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_ERR, configuration, message, Boolean.class);

        if (attachStdErr != null) {
            execCreateCmd.withAttachStderr(attachStdErr);
        }

        if (attachStdIn != null) {
            execCreateCmd.withAttachStdin(attachStdIn);
        }

        Boolean attachStdOut = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_OUT, configuration, message, Boolean.class);

        if (attachStdOut != null) {
            execCreateCmd.withAttachStdout(attachStdOut);
        }

        String[] cmd = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CMD, message);

        if (cmd != null) {
            execCreateCmd.withCmd(cmd);
        }

        Boolean tty = DockerHelper.getProperty(DockerConstants.DOCKER_TTY, configuration, message, Boolean.class);

        if (tty != null) {
            execCreateCmd.withTty(tty);
        }

        return execCreateCmd;

    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

}
