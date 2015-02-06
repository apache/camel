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

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.api.command.AuthCmd;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.CommitCmd;
import com.github.dockerjava.api.command.ContainerDiffCmd;
import com.github.dockerjava.api.command.CopyFileFromContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateImageCmd;
import com.github.dockerjava.api.command.DockerCmd;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PauseContainerCmd;
import com.github.dockerjava.api.command.PingCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
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
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.ExposedPorts;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.LxcConf;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.Volumes;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.docker.DockerClientFactory;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.DockerConstants;
import org.apache.camel.component.docker.DockerEndpoint;
import org.apache.camel.component.docker.DockerHelper;
import org.apache.camel.component.docker.DockerOperation;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Docker producer.
 */
public class DockerProducer extends DefaultProducer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerProducer.class);
    private DockerConfiguration configuration;

    public DockerProducer(DockerEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
    }

    public void process(Exchange exchange) throws Exception {
        
        DockerCmd<?> dockerCmd = null;
        
        Message message = exchange.getIn();
        DockerClient client = DockerClientFactory.getDockerClient(configuration, message);
        
        DockerOperation operation = configuration.getOperation();
        
        switch(operation) {
        
        case AUTH:
            dockerCmd = executeAuthRequest(client, message);
            break;
        case INFO:
            dockerCmd = executeInfoRequest(client, message);
            break;
        case LIST_IMAGES:
            dockerCmd = executeListImagesRequest(client, message);
            break;
        case PING:
            dockerCmd = executePingRequest(client, message);
            break;
        case VERSION:
            dockerCmd = executeVersionRequest(client, message);
            break;
        case PULL_IMAGE:
            dockerCmd = executePullImageRequest(client, message);
            break;
        case PUSH_IMAGE:
            dockerCmd = executePushImageRequest(client, message);
            break;
        case CREATE_IMAGE:
            dockerCmd = executeCreateImageRequest(client, message);
            break;
        case SEARCH_IMAGES:
            dockerCmd = executeSearchImageRequest(client, message);
            break;
        case REMOVE_IMAGE:
            dockerCmd = executeRemoveImageRequest(client, message);
            break;
        case INSPECT_IMAGE:
            dockerCmd = executeInspectImageRequest(client, message);
            break;
        case LIST_CONTAINERS:
            dockerCmd = executeListContainersRequest(client, message);
            break;
        case REMOVE_CONTAINER:
            dockerCmd = executeRemoveContainerRequest(client, message);
            break;
        case INSPECT_CONTAINER:
            dockerCmd = executeInspectContainerRequest(client, message);
            break;
        case WAIT_CONTAINER:
            dockerCmd = executeWaitContainerRequest(client, message);
            break;
        case ATTACH_CONTAINER:
            dockerCmd = executeAttachContainerRequest(client, message);
            break;
        case LOG_CONTAINER:
            dockerCmd = executeLogContainerRequest(client, message);
            break;
        case CONTAINER_COPY_FILE:
            dockerCmd = executeCopyFileContainerRequest(client, message);
            break;
        case DIFF_CONTAINER:
            dockerCmd = executeDiffContainerRequest(client, message);
            break; 
        case STOP_CONTAINER:
            dockerCmd = executeStopContainerRequest(client, message);
            break; 
        case KILL_CONTAINER:
            dockerCmd = executeKillContainerRequest(client, message);
            break; 
        case RESTART_CONTAINER:
            dockerCmd = executeRestartContainerRequest(client, message);
            break; 
        case TOP_CONTAINER:
            dockerCmd = executeTopContainerRequest(client, message);
            break; 
        case TAG_IMAGE:
            dockerCmd = executeTagImageRequest(client, message);
            break;
        case PAUSE_CONTAINER:
            dockerCmd = executePauseContainerRequest(client, message);
            break;
        case UNPAUSE_CONTAINER:
            dockerCmd = executeUnpauseContainerRequest(client, message);
            break;
        case BUILD_IMAGE:
            dockerCmd = executeBuildImageRequest(client, message);
            break;
        case COMMIT_CONTAINER:
            dockerCmd = executeCommitContainerRequest(client, message);
            break;
        case CREATE_CONTAINER:
            dockerCmd = executeCreateContainerRequest(client, message);
            break;
        case START_CONTAINER:
            dockerCmd = executeStartContainerRequest(client, message);
            break;
        default:
            throw new DockerException("Invalid operation: " + operation);
        }
        
        Object result = dockerCmd.exec();
        
        // If request included a response, set as body
        if (result != null) {
            exchange.getIn().setBody(result);
        }
    
    }
    
    /*********************
     * Misc Requests
     ********************/
    
    /**
     * Produces a Authorization request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private AuthCmd executeAuthRequest(DockerClient client, Message message) throws DockerException {
     
        LOGGER.debug("Executing Docker Auth Request");
        
        return client.authCmd();
    }
    
    /**
     * Produces a platform information request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private InfoCmd executeInfoRequest(DockerClient client, Message message) throws DockerException {

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
     * @throws DockerException
     */
    private PingCmd executePingRequest(DockerClient client, Message message) throws DockerException {

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
     * @throws DockerException
     */
    private VersionCmd executeVersionRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Version Request");
        
        VersionCmd versionCmd = client.versionCmd();
        
        return versionCmd;

    }
    
    
    /*********************
     * Image Requests
     ********************/
    
    
    
    /**
     * Performs a list images request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private ListImagesCmd executeListImagesRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Images List Request");
        
        ListImagesCmd listImagesCmd = client.listImagesCmd();
        
        String filter = DockerHelper.getProperty(DockerConstants.DOCKER_FILTER, configuration, message, String.class);
        
        if (filter != null) {
            listImagesCmd.withFilters(filter);
        }
        
        Boolean showAll = DockerHelper.getProperty(DockerConstants.DOCKER_SHOW_ALL, configuration, message, Boolean.class);
        
        if (showAll != null && showAll) {
            listImagesCmd.withShowAll(showAll);
        }

        return listImagesCmd;

    }
    
    /**
     * Performs a create image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private CreateImageCmd executeCreateImageRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Create Image Request");
        
        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);
        
        InputStream inputStream = message.getBody(InputStream.class);
        
        CreateImageCmd createImageCmd = client.createImageCmd(repository, inputStream);
        
        return createImageCmd;

    }
    
    /**
     * Produces a build image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private BuildImageCmd executeBuildImageRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Build Image Request");
        
        Object body = message.getBody();
        
        BuildImageCmd buildImageCmd;
        
        if (body != null && body instanceof InputStream) {
            buildImageCmd = client.buildImageCmd((InputStream) body);
        } else if (body != null && body instanceof File) {
            buildImageCmd = client.buildImageCmd((File) body);
        } else {
            throw new DockerException("Unable to location source Image");
        }
    
        Boolean noCache = DockerHelper.getProperty(DockerConstants.DOCKER_NO_CACHE, configuration, message, Boolean.class);

        if (noCache != null && noCache) {
            buildImageCmd.withNoCache();
        }
        
        Boolean quiet = DockerHelper.getProperty(DockerConstants.DOCKER_QUIET, configuration, message, Boolean.class);

        if (quiet != null && quiet) {
            buildImageCmd.withQuiet();
        }
        
        Boolean remove = DockerHelper.getProperty(DockerConstants.DOCKER_REMOVE, configuration, message, Boolean.class);

        if (remove != null && remove) {
            buildImageCmd.withRemove();
        }
        
        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (tag != null) {
            buildImageCmd.withTag(tag);
        }
        
        return buildImageCmd;

    }
    
    
    /**
     * Produces a pull image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private PullImageCmd executePullImageRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Pull Image Request");
        
        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);
                
        PullImageCmd pullImageCmd = client.pullImageCmd(repository);
        
        String registry = DockerHelper.getProperty(DockerConstants.DOCKER_REGISTRY, configuration, message, String.class);
        if (registry != null) {
            pullImageCmd.withRegistry(registry);
        }
        
        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);
        if (tag != null) {
            pullImageCmd.withTag(tag);
        }
        
        return pullImageCmd;

    }
    
    /**
     * Produces a push image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private PushImageCmd executePushImageRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Push Image Request");
        
        String name = DockerHelper.getProperty(DockerConstants.DOCKER_NAME, configuration, message, String.class);
        
        PushImageCmd pushImageCmd = client.pushImageCmd(name);  
   
        AuthConfig authConfig = getAuthConfig(client);
    
        if (authConfig != null) {
            pushImageCmd.withAuthConfig(authConfig);
        }
        
        return pushImageCmd;
    
    }
    
    
    /**
     * Produces a search image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private SearchImagesCmd executeSearchImageRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Search Image Request");
        
        String term = DockerHelper.getProperty(DockerConstants.DOCKER_TERM, configuration, message, String.class);
        
        SearchImagesCmd searchImagesCmd = client.searchImagesCmd(term);
        
        return searchImagesCmd;

    }
    
    
    /**
     * Produces a remove image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private RemoveImageCmd executeRemoveImageRequest(DockerClient client, Message message) throws DockerException {

        LOGGER.debug("Executing Docker Remove Image Request");
        
        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);
        
        RemoveImageCmd removeImagesCmd = client.removeImageCmd(imageId);
        
        Boolean force = DockerHelper.getProperty(DockerConstants.DOCKER_FORCE, configuration, message, Boolean.class);
        
        if (force != null && force) {
            removeImagesCmd.withForce();
        }
        
        Boolean prune = DockerHelper.getProperty(DockerConstants.DOCKER_NO_PRUNE, configuration, message, Boolean.class);
        
        if (prune != null && prune) {
            removeImagesCmd.withNoPrune();
        }
        
        return removeImagesCmd;

    }
    
    /**
     * Produces a tag image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private TagImageCmd executeTagImageRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Tag Image Request");
        
        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);
        
        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);
   
        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);
       
        TagImageCmd tagImageCmd = client.tagImageCmd(imageId, repository, tag);

        Boolean force = DockerHelper.getProperty(DockerConstants.DOCKER_FORCE, configuration, message, Boolean.class);
        
        if (force != null && force) {
            tagImageCmd.withForce();
        }
             
        return tagImageCmd;

    }

   
    /**
     * Produces a inspect image request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private InspectImageCmd executeInspectImageRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Inspect Image Request");

        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);
        
        InspectImageCmd inspectImageCmd = client.inspectImageCmd(imageId);
        
        return inspectImageCmd;

    }
    
    /*********************
     * Container Requests
     ********************/
    
    
    /**
     * Produces a list containers request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private ListContainersCmd executeListContainersRequest(DockerClient client, Message message) throws DockerException {
  
        LOGGER.debug("Executing Docker List Container Request");
        
        ListContainersCmd listContainersCmd = client.listContainersCmd();
 
        Boolean showSize = DockerHelper.getProperty(DockerConstants.DOCKER_SHOW_SIZE, configuration, message, Boolean.class);
        if (showSize != null && showSize) {
            listContainersCmd.withShowSize(showSize);
        }
        
        Boolean showAll = DockerHelper.getProperty(DockerConstants.DOCKER_SHOW_ALL, configuration, message, Boolean.class);
        if (showAll != null && showAll) {
            listContainersCmd.withShowAll(showAll);
        }
       
        String before = DockerHelper.getProperty(DockerConstants.DOCKER_BEFORE, configuration, message, String.class);
        if (before != null) {
            listContainersCmd.withBefore(before);
        }
        
        Integer limit = DockerHelper.getProperty(DockerConstants.DOCKER_LIMIT, configuration, message, Integer.class);
        if (limit != null) {
            listContainersCmd.withLimit(limit);
        }
        
        String since = DockerHelper.getProperty(DockerConstants.DOCKER_SINCE, configuration, message, String.class);
        if (since != null) {
            listContainersCmd.withSince(since);
        }

        return listContainersCmd;

    }
    
    /**
     * Produce a create container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private CreateContainerCmd executeCreateContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker List Container Request");
        
        String imageId = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE_ID, configuration, message, String.class);
        
        CreateContainerCmd createContainerCmd = client.createContainerCmd(imageId);
        
        String name = DockerHelper.getProperty(DockerConstants.DOCKER_NAME, configuration, message, String.class);

        if (name != null) {
            createContainerCmd.withName(name);
        }

        ExposedPort[] exposedPorts = DockerHelper.getArrayProperty(DockerConstants.DOCKER_EXPOSED_PORTS, message, ExposedPort.class);

        if (exposedPorts != null) {
            createContainerCmd.withExposedPorts(exposedPorts);
        }
        
        String workingDir = DockerHelper.getProperty(DockerConstants.DOCKER_WORKING_DIR, configuration, message, String.class);

        if (workingDir != null) {
            createContainerCmd.withWorkingDir(workingDir);
        }  
        
        Boolean disabledNetwork = DockerHelper.getProperty(DockerConstants.DOCKER_DISABLE_NETWORK, configuration, message, Boolean.class);

        if (disabledNetwork != null && disabledNetwork) {
            createContainerCmd.withDisableNetwork(disabledNetwork);
        }
        
        String hostName = DockerHelper.getProperty(DockerConstants.DOCKER_HOSTNAME, configuration, message, String.class);

        if (hostName != null) {
            createContainerCmd.withHostName(hostName);
        }  
        
        String[] portSpecs = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_PORT_SPECS, message);

        if (portSpecs != null) {
            createContainerCmd.withPortSpecs(portSpecs);
        }
        
        String user = DockerHelper.getProperty(DockerConstants.DOCKER_USER, configuration, message, String.class);

        if (hostName != null) {
            createContainerCmd.withUser(user);
        }  
        
        Boolean tty = DockerHelper.getProperty(DockerConstants.DOCKER_TTY, configuration, message, Boolean.class);

        if (tty != null && tty) {
            createContainerCmd.withTty(tty);
        }
        
        Boolean stdInOpen = DockerHelper.getProperty(DockerConstants.DOCKER_STD_IN_OPEN, configuration, message, Boolean.class);

        if (stdInOpen != null && stdInOpen) {
            createContainerCmd.withStdinOpen(stdInOpen);
        }
        
        Boolean stdInOnce = DockerHelper.getProperty(DockerConstants.DOCKER_STD_IN_ONCE, configuration, message, Boolean.class);

        if (stdInOnce != null && stdInOnce) {
            createContainerCmd.withStdInOnce(stdInOnce);
        }
        
        Long memoryLimit = DockerHelper.getProperty(DockerConstants.DOCKER_MEMORY_LIMIT, configuration, message, Long.class);

        if (memoryLimit != null) {
            createContainerCmd.withMemoryLimit(memoryLimit);
        }

        Long memorySwap = DockerHelper.getProperty(DockerConstants.DOCKER_MEMORY_SWAP, configuration, message, Long.class);

        if (memorySwap != null) {
            createContainerCmd.withMemorySwap(memorySwap);
        }
        
        Integer cpuShares = DockerHelper.getProperty(DockerConstants.DOCKER_CPU_SHARES, configuration, message, Integer.class);

        if (cpuShares != null) {
            createContainerCmd.withCpuShares(cpuShares);
        }
        
        Boolean attachStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_IN, configuration, message, Boolean.class);

        if (attachStdIn != null && attachStdIn) {
            createContainerCmd.withAttachStdin(attachStdIn);
        }
  
        Boolean attachStdOut = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_OUT, configuration, message, Boolean.class);

        if (attachStdOut != null && attachStdOut) {
            createContainerCmd.withAttachStdout(attachStdOut);
        }
        
        Boolean attachStdErr = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_ERR, configuration, message, Boolean.class);

        if (attachStdErr != null && attachStdErr) {
            createContainerCmd.withAttachStderr(attachStdErr);
        }
        
        String[] env = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_ENV, message);

        if (env != null) {
            createContainerCmd.withEnv(env);
        }
        
        String[] cmd = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CMD, message);

        if (cmd != null) {
            createContainerCmd.withCmd(env);
        }
        
        String[] dns = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_DNS, message);

        if (dns != null) {
            createContainerCmd.withDns(dns);
        }
        
        String image = DockerHelper.getProperty(DockerConstants.DOCKER_IMAGE, configuration, message, String.class);

        if (image != null) {
            createContainerCmd.withImage(image);
        }
        
        Volume[] volume = DockerHelper.getArrayProperty(DockerConstants.DOCKER_VOLUMES, message, Volume.class);

        if (volume != null) {
            createContainerCmd.withVolumes(volume);
        }
        
        String[] volumesFrom = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_VOLUMES_FROM, message);

        if (volumesFrom != null) {
            createContainerCmd.withVolumesFrom(volumesFrom);
        }
        
        return createContainerCmd;

    }
    
    /**
     * Produce a start container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private StartContainerCmd executeStartContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Start Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        StartContainerCmd startContainerCmd = client.startContainerCmd(containerId);
        
        Bind[] binds = DockerHelper.getArrayProperty(DockerConstants.DOCKER_BINDS, message, Bind.class);

        if (binds != null) {
            startContainerCmd.withBinds(binds);
        }
        
        Link[] links = DockerHelper.getArrayProperty(DockerConstants.DOCKER_LINKS, message, Link.class);

        if (links != null) {
            startContainerCmd.withLinks(links);
        }
        
        LxcConf[] lxcConf = DockerHelper.getArrayProperty(DockerConstants.DOCKER_LXC_CONF, message, LxcConf.class);

        if (lxcConf != null) {
            startContainerCmd.withLxcConf(lxcConf);
        }
        
        Ports ports = DockerHelper.getProperty(DockerConstants.DOCKER_PORT_BINDINGS, configuration, message, Ports.class);

        if (ports != null) {
            startContainerCmd.withPortBindings(ports);
        }
        
        Boolean privileged = DockerHelper.getProperty(DockerConstants.DOCKER_PRIVILEGED, configuration, message, Boolean.class);

        if (privileged != null && privileged) {
            startContainerCmd.withPrivileged(privileged);
        }
        
        Boolean publishAllPorts = DockerHelper.getProperty(DockerConstants.DOCKER_PUBLISH_ALL_PORTS, configuration, message, Boolean.class);

        if (publishAllPorts != null && publishAllPorts) {
            startContainerCmd.withPublishAllPorts(publishAllPorts);
        }
        
        String[] dns = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_DNS, message);

        if (dns != null) {
            startContainerCmd.withDns(dns);
        }
        
        String[] dnsSearch = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_DNS_SEARCH, message);

        if (dnsSearch != null) {
            startContainerCmd.withDnsSearch(dnsSearch);
        }
        
        String volumesFrom = DockerHelper.getProperty(DockerConstants.DOCKER_VOLUMES_FROM, configuration, message, String.class);

        if (volumesFrom != null) {
            startContainerCmd.withVolumesFrom(volumesFrom);
        }

        String networkMode = DockerHelper.getProperty(DockerConstants.DOCKER_NETWORK_MODE, configuration, message, String.class);

        if (networkMode != null) {
            startContainerCmd.withNetworkMode(networkMode);
        }

        Device[] devices = DockerHelper.getArrayProperty(DockerConstants.DOCKER_DEVICES, message, Device.class);

        if (devices != null) {
            startContainerCmd.withDevices(devices);
        }
        
        RestartPolicy restartPolicy = DockerHelper.getProperty(DockerConstants.DOCKER_RESTART_POLICY, configuration, message, RestartPolicy.class);

        if (restartPolicy != null) {
            startContainerCmd.withRestartPolicy(restartPolicy);
        }
        
        String[] capAdd = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CAP_ADD, message);
        if (capAdd != null) {
            List<Capability> caps = new ArrayList<Capability>();
            for (String s : capAdd) {
                Capability cap = Capability.valueOf(s);
                caps.add(cap);
            }
            Capability[] array = caps.toArray(new Capability[caps.size()]);
            startContainerCmd.withCapAdd(array);
        }
        
        String[] capDrop = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CAP_DROP, message);
        if (capDrop != null) {
            if (capAdd != null) {
                List<Capability> caps = new ArrayList<Capability>();
                for (String s : capDrop) {
                    Capability cap = Capability.valueOf(s);
                    caps.add(cap);
                }
                Capability[] array = caps.toArray(new Capability[caps.size()]);
                startContainerCmd.withCapDrop(array);
            }
        }
        
        return startContainerCmd;

    }
    
    /**
     * Produce a inspect container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private InspectContainerCmd executeInspectContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Inspect Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        InspectContainerCmd inspectContainerCmd = client.inspectContainerCmd(containerId);
        
        return inspectContainerCmd;

    }
    
    /**
     * Produce a wait container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private WaitContainerCmd executeWaitContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Wait Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        WaitContainerCmd waitContainerCmd = client.waitContainerCmd(containerId);
        
        return waitContainerCmd;

    }
    
    /**
     * Produce a log container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private LogContainerCmd executeLogContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Log Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        LogContainerCmd logContainerCmd = client.logContainerCmd(containerId);

        Boolean stdOut = DockerHelper.getProperty(DockerConstants.DOCKER_STD_OUT, configuration, message, Boolean.class);
        
        if (stdOut != null && stdOut) {
            logContainerCmd.withStdOut(stdOut);
        }
        
        Boolean stdErr = DockerHelper.getProperty(DockerConstants.DOCKER_STD_ERR, configuration, message, Boolean.class);
        
        if (stdErr != null && stdErr) {
            logContainerCmd.withStdErr(stdErr);
        }
        
        Boolean timestamps = DockerHelper.getProperty(DockerConstants.DOCKER_TIMESTAMPS, configuration, message, Boolean.class);
        
        if (timestamps != null && timestamps) {
            logContainerCmd.withTimestamps(timestamps);
        }       
        
        Boolean followStream = DockerHelper.getProperty(DockerConstants.DOCKER_FOLLOW_STREAM, configuration, message, Boolean.class);
        
        if (followStream != null && followStream) {
            logContainerCmd.withFollowStream(followStream);
        }    
        
        Boolean tailAll = DockerHelper.getProperty(DockerConstants.DOCKER_TAIL_ALL, configuration, message, Boolean.class);
        
        if (tailAll != null && tailAll) {
            logContainerCmd.withTailAll();
        }
        
        Integer tail = DockerHelper.getProperty(DockerConstants.DOCKER_TAIL, configuration, message, Integer.class);
        
        if (tailAll != null) {
            logContainerCmd.withTail(tail);
        }
        
        return logContainerCmd;

    }
    
    
    /**
     * Produce a attach container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private AttachContainerCmd executeAttachContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Attach Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        AttachContainerCmd attachContainerCmd = client.attachContainerCmd(containerId);

        Boolean stdOut = DockerHelper.getProperty(DockerConstants.DOCKER_STD_OUT, configuration, message, Boolean.class);
        
        if (stdOut != null && stdOut) {
            attachContainerCmd.withStdOut(stdOut);
        }
        
        Boolean stdErr = DockerHelper.getProperty(DockerConstants.DOCKER_STD_ERR, configuration, message, Boolean.class);
        
        if (stdErr != null && stdErr) {
            attachContainerCmd.withStdErr(stdErr);
        }
        
        Boolean logs = DockerHelper.getProperty(DockerConstants.DOCKER_LOGS, configuration, message, Boolean.class);
        
        if (logs != null && logs) {
            attachContainerCmd.withLogs(logs);
        }
        
        Boolean timestamps = DockerHelper.getProperty(DockerConstants.DOCKER_TIMESTAMPS, configuration, message, Boolean.class);
        
        if (timestamps != null && timestamps) {
            attachContainerCmd.withTimestamps(timestamps);
        }       
        
        Boolean followStream = DockerHelper.getProperty(DockerConstants.DOCKER_FOLLOW_STREAM, configuration, message, Boolean.class);
        
        if (followStream != null && followStream) {
            attachContainerCmd.withFollowStream(followStream);
        }    
        
        return attachContainerCmd;

    }
    

    /**
     * Produces a stop container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private StopContainerCmd executeStopContainerRequest(DockerClient client, Message message) throws DockerException {
        
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
     * Produces a diff container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private ContainerDiffCmd executeDiffContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Diff Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ContainerDiffCmd diffContainerCmd = client.containerDiffCmd(containerId);
        
        return diffContainerCmd;

    }
    
    /**
     * Produces a kill container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private KillContainerCmd executeKillContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Kill Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        KillContainerCmd killContainerCmd = client.killContainerCmd(containerId);

        String signal = DockerHelper.getProperty(DockerConstants.DOCKER_SIGNAL, configuration, message, String.class);
        
        if (signal != null) {
            killContainerCmd.withSignal(signal);
        }
             
        return killContainerCmd;

    }
    
    /**
     * Produces a top container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private TopContainerCmd executeTopContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Top Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        TopContainerCmd topContainerCmd = client.topContainerCmd(containerId);

        String psArgs = DockerHelper.getProperty(DockerConstants.DOCKER_PS_ARGS, configuration, message, String.class);
        
        if (psArgs != null) {
            topContainerCmd.withPsArgs(psArgs);
        }
             
        return topContainerCmd;

    }
    
    
    /**
     * Produces a pause container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private PauseContainerCmd executePauseContainerRequest(DockerClient client, Message message) throws DockerException {
            
        LOGGER.debug("Executing Docker Pause Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        PauseContainerCmd pauseContainerCmd = client.pauseContainerCmd(containerId);
        
        return pauseContainerCmd;

    }
    
    /**
     * Produces a unpause container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private UnpauseContainerCmd executeUnpauseContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Unpause Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        
        UnpauseContainerCmd unpauseContainerCmd = client.unpauseContainerCmd(containerId);
        
        return unpauseContainerCmd;

    }
    
    
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
        
        CommitCmd commitCmd = client.commitCmd(containerId);
 
        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);

        if (repository != null) {
            commitCmd.withRepository(repository);
        }
        
        String msg = DockerHelper.getProperty(DockerConstants.DOCKER_MESSAGE, configuration, message, String.class);

        if (message != null) {
            commitCmd.withMessage(msg);
        }
        
        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (tag != null) {
            commitCmd.withTag(tag);
        }
        
        String author = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (author != null) {
            commitCmd.withAuthor(tag);
        }       

        Boolean attachStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_IN, configuration, message, Boolean.class);

        if (attachStdIn != null && attachStdIn) {
            commitCmd.withAttachStdin();
        }   
        
        Boolean attachStdOut = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_OUT, configuration, message, Boolean.class);

        if (attachStdOut != null && attachStdOut) {
            commitCmd.withAttachStdout();
        }   

        Boolean attachStdErr = DockerHelper.getProperty(DockerConstants.DOCKER_ATTACH_STD_ERR, configuration, message, Boolean.class);

        if (attachStdErr != null && attachStdErr) {
            commitCmd.withAttachStderr();
        }   
               
        String[] cmds = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_CMD, message);

        if (cmds != null) {
            commitCmd.withCmd(cmds);
        }
        
        Boolean disableNetwork = DockerHelper.getProperty(DockerConstants.DOCKER_DISABLE_NETWORK, configuration, message, Boolean.class);

        if (disableNetwork != null && disableNetwork) {
            commitCmd.withDisableNetwork(disableNetwork);
        }   
        
        Boolean pause = DockerHelper.getProperty(DockerConstants.DOCKER_PAUSE, configuration, message, Boolean.class);

        if (pause != null && pause) {
            commitCmd.withPause(pause);
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
        
        Boolean openStdIn = DockerHelper.getProperty(DockerConstants.DOCKER_OPEN_STD_IN, configuration, message, Boolean.class);

        if (openStdIn != null && openStdIn) {
            commitCmd.withOpenStdin(openStdIn);
        }  
        
        String[] portSpecs = DockerHelper.parseDelimitedStringHeader(DockerConstants.DOCKER_PORT_SPECS, message);

        if (portSpecs != null) {
            commitCmd.withPortSpecs(portSpecs);
        }
        
        Boolean stdInOnce = DockerHelper.getProperty(DockerConstants.DOCKER_STD_IN_ONCE, configuration, message, Boolean.class);

        if (stdInOnce != null && stdInOnce) {
            commitCmd.withStdinOnce(stdInOnce);
        }  
        
        Boolean tty = DockerHelper.getProperty(DockerConstants.DOCKER_TTY, configuration, message, Boolean.class);

        if (tty != null && tty) {
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
        
        String workingDir = DockerHelper.getProperty(DockerConstants.DOCKER_HOSTNAME, configuration, message, String.class);

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
     * @throws DockerException
     */
    private CopyFileFromContainerCmd executeCopyFileContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Copy File/Folder Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);
        String resource = DockerHelper.getProperty(DockerConstants.DOCKER_RESOURCE, configuration, message, String.class);

        CopyFileFromContainerCmd copyFileContainerCmd = client.copyFileFromContainerCmd(containerId, resource);

        String hostPath = DockerHelper.getProperty(DockerConstants.DOCKER_HOST_PATH, configuration, message, String.class);
        
        if (hostPath != null) {
            copyFileContainerCmd.withHostPath(hostPath);
        }
             
        return copyFileContainerCmd;

    }
    
    /**
     * Produces a remove container request
     * 
     * @param client
     * @param message
     * @return
     * @throws DockerException
     */
    private RemoveContainerCmd executeRemoveContainerRequest(DockerClient client, Message message) throws DockerException {
        
        LOGGER.debug("Executing Docker Remove Container Request");
        
        String containerId = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        RemoveContainerCmd removeContainerCmd = client.removeContainerCmd(containerId);

        Boolean force = DockerHelper.getProperty(DockerConstants.DOCKER_FORCE, configuration, message, Boolean.class);
        
        if (force != null && force) {
            removeContainerCmd.withForce(force);
        }
        
        Boolean removeVolumes = DockerHelper.getProperty(DockerConstants.DOCKER_REMOVE_VOLUMES, configuration, message, Boolean.class);
        
        if (removeVolumes != null && removeVolumes) {
            removeContainerCmd.withRemoveVolumes(removeVolumes);
        }
        
        return removeContainerCmd;

    }
   

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }
    
    
    /**
     * Attempt to retrieve authorization details from the client
     * 
     * @param client
     * @return
     */
    private AuthConfig getAuthConfig(DockerClient client) {
        
        AuthConfig authConfig = null;
        
        try {
            authConfig = client.authConfig();
        } catch (Exception e) {
            // Do nothing here
        }
        
        return authConfig;     
        
    }

}
