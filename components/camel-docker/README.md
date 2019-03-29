Docker Camel Component
=======================

Camel component for communicating with Docker.

## Docker Remote API

The Docker Camel component leverages the [docker-java](https://github.com/docker-java/docker-java) via the [Docker Remote API](https://docs.docker.com/reference/api/docker_remote_api/)

## URI Format

    docker://[operation]?[options]

## Header Strategy

All URI option can be passed as Header properties. Values found in a message header take precedence over URI parameters. A header property takes the form of a URI option prefixed with *CamelDocker* as shown below

| URI Option | Header Property  |
| ------------- | ---------------- |
|containerId|CamelDockerContainerId |

## Configuration

Options on the Docker Endpoint mapped to a *DockerConfiguration* POJO. This object contains the values integral to the communication with the Docker sever as first order attributes along with a Map of additional parameters based on the options for each type of interaction.

The following are the primary options for communicating with the Docker server

| Option | Header | Description | Default Value |
|-----------|-----------|-----------------|-------------------|
| host     | CamelDockerHost | Docker host | localhost |
| port      | CamelDockerPort | Docker port | 2375 |
| username | CamelDockerUserName | User name to authenticate with | |
| password | CamelDockerPassword | Password to authenticate with | |
| secure | CamelDockerSecure | Use HTTPS communication | false |
|certPath | CamelDockerCertPath | Location containing the SSL certificate chain | |
| email | CamelDockerEmail | Email address associated with the user | |
| requestTimeout | CamelDockerRequestTimeout | Request timeout for response (in seconds) | 30 |
|serverAddress | CamelDockerServerAddress | Address of the Docker registry server (If not specified, *host* will be used) | https://index.docker.io/v1/ |
|maxTotalConnections | CamelDockerMaxTotalConnections | Maximum number of total connections | 100 |
|maxPerRouteConnections | CamelDockerMaxPerRouteConnections | Maximum number of connections per route | 100 |
| cmdExecFactory | CamelDockerCmdExecFactory | The fully qualified class name of the DockerCmdExecFactory implementation to use |  com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory |


## Consumer Operations

| Operation | Options | Description  | Produces |
| ------------- | ---------------- | ------------- | ---------------- |
|events| initialRange | Amount of time in the past to begin receiving events (Long) | Event |
|statistics| **containerId** | Statistics based on resource usage | Statistics |

## Producer Operations

The following producer operations are available

### General
| Operation | Options | Description  | Returns |
| ------------- | ---------------- | ------------- | ---------------- |
| auth | Values obtained from the component general options| Validate auth configuration | AuthResponse |
| info | | System wide information | Info |
| ping | | Ping the Docker server |  |
| version | | Show the docker version information | Version |

 
### Images

| Operation | Options | Description | Body Content | Returns |
| ------------- | ---------------- | ------------- | ---------------- | ---------------- |
| image/build | noCache, quiet, remove, tag | Build an image from Dockerfile via stdin | **InputStream** or **File** | InputStream |
| image/create | **repository** | Create an image | **InputStream** |CreateImageResponse |
| image/inspect | **imageId** | Inspect an image | | InspectImageResponse |
| image/list | filter, showAll | List images | | List&lt;Image&gt; |
| image/pull |  **repository**, registry, tag | Pull an image from the registry | |InputStream |
| image/push | **name**, tag | Push an image on the registry | | InputStream |
| image/remove | **imageId**, force, noPrune | Remove an image | | |
| image/search | **term** | Search for images | | List&lt;SearchItem&gt; |
| image/tag | **imageId**, **repository**, **tag**, **force** | Tag an image into a repository | | |	

### Containers

| Operation | Options | Description  | Body Content | Returns |
| ------------- | ---------------- | ------------- | ---------------- |
| container/attach | **containerId**, followStream, logs, stdErr, stdOut, timestamps  | Attach to a container | | InputStream |
| container/commit | **containerId**, author, attachStdErr, attachStdIn, attachStdOut, cmd, disableNetwork, env, exposedPorts, hostname, memory, memorySwap, message, openStdIn, pause, portSpecs, repository, stdInOnce, tag, tty, user, volumes, workingDir | Create a new image from a container's changes | | String |
| container/copyfile | **containerId**, **resource**, hostPath | Copy files or folders from a container | | InputStream |
| container/create | **image**, attachStdErr, attachStdIn, attachStdOut, capAdd, capDrop, cmd, cpuShares, disableNetwork, dns, domanName, entrypoint, env, exposedPorts, hostConfig, hostname, memoryLimit, memorySwap, name, portSpecs, stdInOnce, stdInOpen, tty, user, volumes, volumesFrom, workingDir | Create a container |  |CreateContainerResponse |
| container/diff | **containerId**, containerIdDiff | Differences on the container filesystem | | List&lt;ChangeLog&gt; |
| container/inspect | **containerId** | Inspect a container  | | InspectContainerResponse |
| container/kill | **containerId**, signal | Kill a container | | |
| container/list | before, limit, showSize, showAll, since | List containers | | List&lt;Container&gt; |
| container/log | **containerId**, followStream, stdErr, stdOut, tail, tailAll, timestamps | Get container logs | | InputStream |
| container/pause | **containerId** | Pause a container | | |
| container/remove | **containerId**, force, removeVolumes | Remove a container | | |
| container/restart | **containerId**, timeout | Restart a container | |
| container/start | **containerId** | Start a container | | |
| container/stop | **containerId**, timeout | Stop a container | |
| container/top | **containerId**, psArgs | List processes running in a container | |TopContainerResponse |
| container/unpause | **containerId** | Unpause a container | | |
| container/wait | **containerId** | Blocks until a container is stopped | | |

### Exec

| Operation | Options | Description  | Body Content | Returns |
| ------------- | ---------------- | ------------- | ---------------- |
| exec/create | **containerId**, attachStdErr, attachStdIn, attachStdOut, cmd, tty | Setup an *exec* instance on a running container | | ExecCreateCmdResponse |
| exec/start | **containerId**, detach, execId, tty | Starts a previously created *exec* instance | |InputStream |


## Examples

The following example consumes events from Docker 

    docker://events?host=192.168.59.103&port=2375

The following example queries Docker for system wide information

    docker://info?host=192.168.59.103&port=2375