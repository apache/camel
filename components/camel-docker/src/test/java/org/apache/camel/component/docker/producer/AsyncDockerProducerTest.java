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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.WaitResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docker.DockerComponent;
import org.apache.camel.component.docker.DockerConfiguration;
import org.apache.camel.component.docker.exception.DockerException;
import org.apache.camel.component.docker.util.DockerTestUtils;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * The body and the failures of the streaming operations of the async producer.
 */
@ExtendWith(MockitoExtension.class)
public class AsyncDockerProducerTest extends CamelTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String host = "localhost";
    private final Integer port = 2375;

    @Mock
    private DockerClient dockerClient;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        DockerConfiguration dockerConfiguration = new DockerConfiguration();
        dockerConfiguration.setParameters(DockerTestUtils.getDefaultParameters(host, port, dockerConfiguration));

        DockerComponent dockerComponent = new DockerComponent(dockerConfiguration);
        dockerComponent.setClient(DockerTestUtils.getClientProfile(host, port, dockerConfiguration), dockerClient);
        camelContext.addComponent("docker", dockerComponent);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                for (String operation : new String[] {
                        "imagebuild", "imagepull", "imagepush", "containerattach", "containerlog", "containerwait",
                        "execstart" }) {
                    from("direct:" + operation)
                            .to("docker://" + operation + "?host=" + host + "&port=" + port
                                + "&containerId=abc&execId=def&repository=busybox&name=busybox");
                }
            }
        };
    }

    @Test
    void imageBuildGivesTheImageIdOfATaggedBuild() throws Exception {
        BuildImageCmd cmd = Mockito.mock(BuildImageCmd.class);
        Mockito.when(dockerClient.buildImageCmd(any(InputStream.class))).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                json("{\"stream\":\"Step 1/1 : FROM busybox\\n\"}", BuildResponseItem.class),
                json("{\"stream\":\"Successfully built 4dbb3f2e9e1a\\n\"}", BuildResponseItem.class),
                json("{\"stream\":\"Successfully tagged camel:1.0\\n\"}", BuildResponseItem.class)));

        // a byte[] body is the tar archive of the build context
        Exchange out = template.send("direct:imagebuild", e -> e.getIn().setBody(new byte[] { 1, 2, 3 }));

        assertNull(out.getException());
        assertEquals("4dbb3f2e9e1a", out.getMessage().getBody());
        Mockito.verify(dockerClient).buildImageCmd(any(InputStream.class));
    }

    @Test
    void imageBuildFailsWithoutBuildContext() {
        Exchange out = template.send("direct:imagebuild", e -> e.getIn().setBody(null));

        DockerException cause = assertInstanceOf(DockerException.class, out.getException());
        assertTrue(cause.getMessage().contains("imagebuild needs the build context"), cause.getMessage());
        Mockito.verify(dockerClient, Mockito.never()).buildImageCmd(any(InputStream.class));
    }

    @Test
    void imageBuildFailsWhenTheBuildFails() throws Exception {
        BuildImageCmd cmd = Mockito.mock(BuildImageCmd.class);
        Mockito.when(dockerClient.buildImageCmd(any(InputStream.class))).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                json("{\"error\":\"The command '/bin/sh -c false' returned a non-zero code: 1\"}",
                        BuildResponseItem.class)));

        Exchange out = template.send("direct:imagebuild", e -> e.getIn().setBody("tar".getBytes()));

        DockerClientException cause = assertInstanceOf(DockerClientException.class, out.getException());
        assertTrue(cause.getMessage().contains("returned a non-zero code: 1"), cause.getMessage());
    }

    @Test
    void containerLogGivesTheOutput() {
        LogContainerCmd cmd = Mockito.mock(LogContainerCmd.class);
        Mockito.when(dockerClient.logContainerCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                new Frame(StreamType.STDOUT, "hello ".getBytes(StandardCharsets.UTF_8)),
                new Frame(StreamType.STDERR, "wørld\n".getBytes(StandardCharsets.UTF_8))));

        Exchange out = template.send("direct:containerlog", e -> e.getIn().setBody("ignored"));

        assertNull(out.getException());
        assertEquals("hello wørld\n", out.getMessage().getBody());
    }

    @Test
    void containerAttachGivesTheOutput() {
        AttachContainerCmd cmd = Mockito.mock(AttachContainerCmd.class);
        Mockito.when(dockerClient.attachContainerCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(new Frame(StreamType.RAW, "attached".getBytes())));

        Exchange out = template.send("direct:containerattach", e -> e.getIn().setBody("ignored"));

        assertNull(out.getException());
        assertEquals("attached", out.getMessage().getBody());
    }

    @Test
    void execStartGivesTheOutput() {
        ExecStartCmd cmd = Mockito.mock(ExecStartCmd.class);
        Mockito.when(dockerClient.execStartCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(new Frame(StreamType.STDOUT, "total 0\n".getBytes())));

        Exchange out = template.send("direct:execstart", e -> e.getIn().setBody("ignored"));

        assertNull(out.getException());
        assertEquals("total 0\n", out.getMessage().getBody());
    }

    @Test
    void execStartFailsWhenTheStreamFails() {
        ExecStartCmd cmd = Mockito.mock(ExecStartCmd.class);
        Mockito.when(dockerClient.execStartCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onError(new IllegalStateException("connection reset"));
            return callback;
        });

        Exchange out = template.send("direct:execstart", e -> e.getIn().setBody("ignored"));

        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, out.getException());
        assertEquals("connection reset", cause.getMessage());
    }

    @Test
    void containerWaitGivesTheStatusCode() throws Exception {
        WaitContainerCmd cmd = Mockito.mock(WaitContainerCmd.class);
        Mockito.when(dockerClient.waitContainerCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(json("{\"StatusCode\":3}", WaitResponse.class)));

        Exchange out = template.send("direct:containerwait", e -> e.getIn().setBody("ignored"));

        assertNull(out.getException());
        assertEquals(3, out.getMessage().getBody());
    }

    @Test
    void imagePullGivesTheLastItem() throws Exception {
        PullImageCmd cmd = Mockito.mock(PullImageCmd.class);
        Mockito.when(dockerClient.pullImageCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                json("{\"status\":\"Pulling from library/busybox\",\"id\":\"latest\"}", PullResponseItem.class),
                json("{\"status\":\"Status: Downloaded newer image for busybox:latest\"}", PullResponseItem.class)));

        Exchange out = template.send("direct:imagepull", e -> e.getIn().setBody("ignored"));

        assertNull(out.getException());
        PullResponseItem item = assertInstanceOf(PullResponseItem.class, out.getMessage().getBody());
        assertEquals("Status: Downloaded newer image for busybox:latest", item.getStatus());
    }

    @Test
    void imagePullFailsWhenThePullFails() throws Exception {
        PullImageCmd cmd = Mockito.mock(PullImageCmd.class);
        Mockito.when(dockerClient.pullImageCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                json("{\"status\":\"Pulling fs layer\",\"id\":\"latest\"}", PullResponseItem.class)));

        Exchange out = template.send("direct:imagepull", e -> e.getIn().setBody("ignored"));

        DockerClientException cause = assertInstanceOf(DockerClientException.class, out.getException());
        assertTrue(cause.getMessage().contains("Could not pull image"), cause.getMessage());
    }

    @Test
    void imagePushGivesTheLastItem() throws Exception {
        PushImageCmd cmd = Mockito.mock(PushImageCmd.class);
        Mockito.when(dockerClient.pushImageCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                json("{\"status\":\"Pushed\",\"id\":\"3d2430473443\"}", PushResponseItem.class),
                json("{\"status\":\"latest: digest: sha256:abc size: 527\"}", PushResponseItem.class)));

        Exchange out = template.send("direct:imagepush", e -> e.getIn().setBody("ignored"));

        assertNull(out.getException());
        PushResponseItem item = assertInstanceOf(PushResponseItem.class, out.getMessage().getBody());
        assertEquals("latest: digest: sha256:abc size: 527", item.getStatus());
    }

    @Test
    void imagePushFailsWhenThePushFails() throws Exception {
        PushImageCmd cmd = Mockito.mock(PushImageCmd.class);
        Mockito.when(dockerClient.pushImageCmd(anyString())).thenReturn(cmd);
        Mockito.when(cmd.exec(any())).thenAnswer(complete(
                json("{\"error\":\"denied: requested access to the resource is denied\"}", PushResponseItem.class)));

        Exchange out = template.send("direct:imagepush", e -> e.getIn().setBody("ignored"));

        DockerException cause = assertInstanceOf(DockerException.class, out.getException());
        assertEquals("Could not push image: denied: requested access to the resource is denied", cause.getMessage());
    }

    /**
     * Gives the items to the callback of the command and completes it, as docker-java does on its own thread.
     */
    @SafeVarargs
    private static <T> Answer<Object> complete(T... items) {
        return invocation -> {
            ResultCallback<T> callback = invocation.getArgument(0);
            for (T item : items) {
                callback.onNext(item);
            }
            callback.onComplete();
            return callback;
        };
    }

    private static <T> T json(String json, Class<T> type) throws Exception {
        return MAPPER.readValue(json, type);
    }
}
