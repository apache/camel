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
package org.apache.camel.component.docker.producer;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback.Adapter;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.AsyncDockerCmd;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.WaitResponse;
import org.apache.camel.AsyncCallback;
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
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Docker producer.
 */
public class AsyncDockerProducer extends DefaultAsyncProducer {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncDockerProducer.class);
    public static final String MISSING_CONTAINER_ID = "Container ID must be specified";
    private DockerConfiguration configuration;
    private DockerComponent component;

    public AsyncDockerProducer(DockerEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfiguration();
        this.component = (DockerComponent) endpoint.getComponent();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {

            Message message = exchange.getIn();
            DockerClient client = DockerClientFactory.getDockerClient(component, configuration, message);

            DockerOperation operation = configuration.getOperation();

            switch (operation) {

                /** Images **/
                case BUILD_IMAGE:
                    runAsyncImageBuild(exchange, message, client);

                    break;
                case PULL_IMAGE:
                    runAsyncPull(message, client, exchange);

                    break;
                case PUSH_IMAGE:
                    runAsyncPush(exchange, message, client);

                    break;
                /** Containers **/
                case ATTACH_CONTAINER:
                    runAsyncAttachContainer(exchange, message, client);

                    break;
                case LOG_CONTAINER:
                    runAsyncLogContainer(exchange, message, client);

                    break;
                case WAIT_CONTAINER:
                    // result contain a status code value
                    runAsyncWaitContainer(exchange, message, client);

                    break;
                case EXEC_START:
                    runAsyncExecStart(exchange, message, client);

                    break;
                default:
                    throw new DockerException("Invalid operation: " + operation);
            }
        } catch (InterruptedException e) {
            LOG.error("Interrupted while processing", e);
            Thread.currentThread().interrupt();
        } catch (DockerException e) {
            LOG.error(e.getMessage(), e);
        }

        callback.done(false);
        return false;
    }

    private void runAsyncImageBuild(Exchange exchange, Message message, DockerClient client)
            throws DockerException, InterruptedException {
        // result contain an image id value
        try (BuildImageCmd cmd = executeBuildImageRequest(client, message)) {

            BuildImageResultCallback item = cmd.exec(new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                    super.onNext(item);

                    LOG.trace("build image callback {}", item);

                    exchange.getIn().setBody(item.getImageId());
                }
            });

            setResponse(exchange, item);
        }
    }

    private void runAsyncWithFrameResponse(Exchange exchange, AsyncDockerCmd<?, Frame> cmd) throws InterruptedException {
        Adapter<Frame> item = cmd.exec(new Adapter<Frame>() {
            @Override
            public void onNext(Frame item) {
                LOG.trace("running framed callback {}", item);
                super.onNext(item);
            }

        });

        setResponse(exchange, item);
    }

    private void runAsyncExecStart(Exchange exchange, Message message, DockerClient client) throws InterruptedException {
        try (ExecStartCmd cmd = executeExecStartRequest(client, message)) {
            runAsyncWithFrameResponse(exchange, cmd);
        }
    }

    private void runAsyncWaitContainer(Exchange exchange, Message message, DockerClient client) throws InterruptedException {
        try (WaitContainerCmd cmd = executeWaitContainerRequest(client, message)) {
            WaitContainerResultCallback item = cmd.exec(new WaitContainerResultCallback() {
                @Override
                public void onNext(WaitResponse item) {
                    super.onNext(item);

                    LOG.trace("wait container callback {}", item);
                }

            });

            setResponse(exchange, item);
        }
    }

    private void setResponse(Exchange exchange, ResultCallbackTemplate item) throws InterruptedException {
        if (item != null) {
            exchange.getIn().setBody(item);
            item.awaitCompletion();
        }
    }

    private void runAsyncLogContainer(Exchange exchange, Message message, DockerClient client) throws InterruptedException {
        try (LogContainerCmd cmd = executeLogContainerRequest(client, message)) {
            runAsyncWithFrameResponse(exchange, cmd);
        }
    }

    private void runAsyncAttachContainer(Exchange exchange, Message message, DockerClient client) throws InterruptedException {
        try (AttachContainerCmd cmd = executeAttachContainerRequest(client, message)) {
            runAsyncWithFrameResponse(exchange, cmd);
        }
    }

    private void runAsyncPush(Exchange exchange, Message message, DockerClient client) throws InterruptedException {
        try (PushImageCmd cmd = executePushImageRequest(client, message)) {
            Adapter<PushResponseItem> item = cmd.exec(new Adapter<PushResponseItem>() {
                @Override
                public void onNext(PushResponseItem item) {
                    super.onNext(item);

                    LOG.trace("push image callback {}", item);
                }
            });

            setResponse(exchange, item);
        }
    }

    private void runAsyncPull(Message message, DockerClient client, Exchange exchange) throws InterruptedException {
        try (PullImageCmd cmd = executePullImageRequest(client, message)) {

            PullImageResultCallback item = cmd.exec(new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    super.onNext(item);

                    LOG.trace("pull image callback {}", item);

                }
            });

            setResponse(exchange, item);
        }
    }

    /**
     * Produces a build image request
     */
    private BuildImageCmd executeBuildImageRequest(DockerClient client, Message message) throws DockerException {

        LOG.debug("Executing Docker Build Image Request");

        Object body = message.getBody();

        BuildImageCmd buildImageCmd;

        if (body instanceof InputStream) {
            buildImageCmd = client.buildImageCmd((InputStream) body);
        } else if (body instanceof File) {
            buildImageCmd = client.buildImageCmd((File) body);
        } else {
            throw new DockerException("Unable to location source Image");
        }

        Boolean noCache = DockerHelper.getProperty(DockerConstants.DOCKER_NO_CACHE, configuration, message, Boolean.class);

        if (noCache != null) {
            buildImageCmd.withNoCache(noCache);
        }

        Boolean quiet = DockerHelper.getProperty(DockerConstants.DOCKER_QUIET, configuration, message, Boolean.class);

        if (quiet != null) {
            buildImageCmd.withQuiet(quiet);
        }

        Boolean remove = DockerHelper.getProperty(DockerConstants.DOCKER_REMOVE, configuration, message, Boolean.class);

        if (remove != null) {
            buildImageCmd.withRemove(remove);
        }

        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (tag != null) {
            buildImageCmd.withTags(Collections.singleton(tag));
        }

        return buildImageCmd;

    }

    /**
     * Produces a pull image request
     */
    private PullImageCmd executePullImageRequest(DockerClient client, Message message) {

        LOG.debug("Executing Docker Pull Image Request");

        String repository = DockerHelper.getProperty(DockerConstants.DOCKER_REPOSITORY, configuration, message, String.class);

        ObjectHelper.notNull(repository, "Repository must be specified");

        PullImageCmd pullImageCmd = client.pullImageCmd(repository);

        String registry = DockerHelper.getProperty(DockerConstants.DOCKER_REGISTRY, configuration, message, String.class);
        if (registry != null) {
            pullImageCmd.withRegistry(registry);
        }

        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);
        if (tag != null) {
            pullImageCmd.withTag(tag);
        }

        AuthConfig authConfig = client.authConfig();

        if (authConfig != null) {
            pullImageCmd.withAuthConfig(authConfig);
        }

        return pullImageCmd;

    }

    /**
     * Produces a push image request
     */
    private PushImageCmd executePushImageRequest(DockerClient client, Message message) {

        LOG.debug("Executing Docker Push Image Request");

        String name = DockerHelper.getProperty(DockerConstants.DOCKER_NAME, configuration, message, String.class);

        ObjectHelper.notNull(name, "Image name must be specified");

        PushImageCmd pushImageCmd = client.pushImageCmd(name);

        String tag = DockerHelper.getProperty(DockerConstants.DOCKER_TAG, configuration, message, String.class);

        if (tag != null) {
            pushImageCmd.withTag(tag);
        }

        AuthConfig authConfig = client.authConfig();

        if (authConfig != null) {
            pushImageCmd.withAuthConfig(authConfig);
        }

        return pushImageCmd;

    }

    /**
     * Produce a attach container request
     */
    private AttachContainerCmd executeAttachContainerRequest(DockerClient client, Message message) {

        LOG.debug("Executing Docker Attach Container Request");

        String containerId
                = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, MISSING_CONTAINER_ID);

        AttachContainerCmd attachContainerCmd = client.attachContainerCmd(containerId);

        Boolean followStream
                = DockerHelper.getProperty(DockerConstants.DOCKER_FOLLOW_STREAM, configuration, message, Boolean.class);

        if (followStream != null) {
            attachContainerCmd.withFollowStream(followStream);
        }

        Boolean logs = DockerHelper.getProperty(DockerConstants.DOCKER_LOGS, configuration, message, Boolean.class);

        if (logs != null) {
            attachContainerCmd.withLogs(logs);
        }

        Boolean stdErr = DockerHelper.getProperty(DockerConstants.DOCKER_STD_ERR, configuration, message, Boolean.class);

        if (stdErr != null) {
            attachContainerCmd.withStdErr(stdErr);
        }

        Boolean stdOut = DockerHelper.getProperty(DockerConstants.DOCKER_STD_OUT, configuration, message, Boolean.class);

        if (stdOut != null) {
            attachContainerCmd.withStdOut(stdOut);
        }

        Boolean timestamps = DockerHelper.getProperty(DockerConstants.DOCKER_TIMESTAMPS, configuration, message, Boolean.class);

        if (timestamps != null) {
            attachContainerCmd.withTimestamps(timestamps);
        }

        return attachContainerCmd;

    }

    /**
     * Produce a log container request
     */
    private LogContainerCmd executeLogContainerRequest(DockerClient client, Message message) {

        LOG.debug("Executing Docker Log Container Request");

        String containerId
                = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, MISSING_CONTAINER_ID);

        LogContainerCmd logContainerCmd = client.logContainerCmd(containerId);

        Boolean followStream
                = DockerHelper.getProperty(DockerConstants.DOCKER_FOLLOW_STREAM, configuration, message, Boolean.class);

        if (followStream != null) {
            logContainerCmd.withFollowStream(followStream);
        }

        Boolean stdErr = DockerHelper.getProperty(DockerConstants.DOCKER_STD_ERR, configuration, message, Boolean.class);

        if (stdErr != null) {
            logContainerCmd.withStdErr(stdErr);
        }

        Boolean stdOut = DockerHelper.getProperty(DockerConstants.DOCKER_STD_OUT, configuration, message, Boolean.class);

        if (stdOut != null) {
            logContainerCmd.withStdOut(stdOut);
        }

        Integer tail = DockerHelper.getProperty(DockerConstants.DOCKER_TAIL, configuration, message, Integer.class);

        if (tail != null) {
            logContainerCmd.withTail(tail);
        }

        Boolean tailAll = DockerHelper.getProperty(DockerConstants.DOCKER_TAIL_ALL, configuration, message, Boolean.class);

        if (tailAll != null && tailAll) {
            logContainerCmd.withTailAll();
        }

        Boolean timestamps = DockerHelper.getProperty(DockerConstants.DOCKER_TIMESTAMPS, configuration, message, Boolean.class);

        if (timestamps != null) {
            logContainerCmd.withTimestamps(timestamps);
        }

        return logContainerCmd;

    }

    /**
     * Produce a wait container request
     */
    private WaitContainerCmd executeWaitContainerRequest(DockerClient client, Message message) {

        LOG.debug("Executing Docker Wait Container Request");

        String containerId
                = DockerHelper.getProperty(DockerConstants.DOCKER_CONTAINER_ID, configuration, message, String.class);

        ObjectHelper.notNull(containerId, MISSING_CONTAINER_ID);

        return client.waitContainerCmd(containerId);
    }

    /**
     * Produces a exec start request
     */
    private ExecStartCmd executeExecStartRequest(DockerClient client, Message message) {

        LOG.debug("Executing Docker Exec Start Request");

        String execId = DockerHelper.getProperty(DockerConstants.DOCKER_EXEC_ID, configuration, message, String.class);

        ObjectHelper.notNull(execId, "Exec ID must be specified");

        ExecStartCmd execStartCmd = client.execStartCmd(execId);

        Boolean detach = DockerHelper.getProperty(DockerConstants.DOCKER_DETACH, configuration, message, Boolean.class);

        if (detach != null) {
            execStartCmd.withDetach(detach);
        }

        Boolean tty = DockerHelper.getProperty(DockerConstants.DOCKER_TTY, configuration, message, Boolean.class);

        if (tty != null) {
            execStartCmd.withTty(tty);
        }

        return execStartCmd;

    }
}
