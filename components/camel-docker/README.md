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

## General Options

The following parameters can be used with any invocation of the component

The following options are required

| Option | Header | Description | Default Value |
|-----------|-----------|-----------------|-------------------|
| host     | CamelDockerHost | Docker host | localhost |
| port      | CamelDockerPort | Docker port | 5000 |

The following are additional optional parameters

| Option | Header | Description | Default Value |
|-----------|-----------|-----------------|-------------------|
| username | CamelDockerUserName | User name to authenticate with | |
| password | CamelDockerPassword | Password to authenticate with | |
| email | CamelDockerEmail | Email address associated with the user | |
| secure | CamelDockerSecure | Use HTTPS communication | false |
| requestTimeout | CamelDockerRequestTimeout | Request timeout for response (in seconds) | 30 |
|certPath | CamelDockerCertPath | Location containing the SSL certificate chain | | 


## Consumer Operations

| Operation | Options | Description  | Produces |
| ------------- | ---------------- | ------------- | ---------------- |
|events| initialRange | Monitor Docker events (Streaming) | Event |

## Producer Operations

The following producer operations are available

### Misc
| Operation | Options | Description  | Returns |
| ------------- | ---------------- | ------------- | ---------------- |
| auth | | Check auth configuration | |
| info | | System wide information | Info |
| ping | | Ping the Docker server | | 
| version | | Show the docker version information | Version |

 
### Images

| Operation | Options | Description | Body Content | Returns |
| ------------- | ---------------- | ------------- | ---------------- | ---------------- |
| image/list | filter, showAll | List images | | List&lt;Image&gt; |
| image/create | **repository** | Create an image | InputStream |CreateImageResponse |
| image/build | noCache, quiet, remove, tag | Build an image from Dockerfile via stdin | InputStream or File | InputStream |
| image/pull |  **repository**, registry, tag | Pull an image from the registry | |InputStream |
| image/push | **name** | Push an image on the registry | InputStream |
| image/search | **term** | Search for images | | List&lt;SearchItem&gt; |
| image/remove | **imageId**, force, noPrune | Remove an image | | |
| image/tag | **imageId**, **repository**, **tag**, **force** | Tag an image into a repository | | |	
| image/inspect | **imageId** | Inspect an image | | InspectImageResponse |

### Containers

| Operation | Options | Description  | Body Content |
| ------------- | ---------------- | ------------- | ---------------- |
| container/list | showSize, showAll, before, since, limit, List containers | initialRange | List&lt;Container&gt; |
| container/create | **imageId**, name, exposedPorts, workingDir, disableNetwork, hostname, user, tty, stdInOpen, stdInOnce, memoryLimit, memorySwap, cpuShares, attachStdIn, attachStdOut, attachStdErr, env, cmd, dns, image, volumes, volumesFrom | Create a container | CreateContainerResponse |
| container/start | **containerId**, binds, links, lxcConf, portBindings, privileged, publishAllPorts, dns, dnsSearch, volumesFrom, networkMode, devices, restartPolicy, capAdd, capDrop | Start a container | | 
| container/inspect | **containerId** | Inspect a container  | InspectContainerResponse |
| container/wait | **containerId** | Wait a container | Integer
| container/log | **containerId**, stdOut, stdErr, timestamps, followStream, tailAll, tail | Get container logs | InputStream |
| container/attach | **containerId**, stdOut, stdErr, timestamps, logs, followStream | Attach to a container | InputStream |
| container/stop | **containerId**, timeout | Stop a container | |
| container/restart | **containerId**, timeout | Restart a container | |
| container/diff | **containerId** | Inspect changes on a container | ChangeLog |
| container/kill | **containerId**, signal, | Kill a container | |
| container/top | **containerId**, psArgs | List processes running in a container | TopContainerResponse |
| container/pause | **containerId** | Pause a container | |
| container/unpause | **containerId** | Unpause a container | |
| container/commit | **containerId**, repository, message, tag, attachStdIn, attachStdOut, attachStdErr, cmd, disableNetwork, pause, env, exposedPorts, hostname, memory, memorySwap, openStdIn, portSpecs, stdInOnce, tty, user, volumes, hostname | Create a new image from a container's changes | String |
| container/copyfile | **containerId**, **resource**, hostPath | Copy files or folders from a container | InputStream |
| container/remove | **containerId**, force, removeVolumes | Remove a container | |


## Examples

The following example consumes events from Docker 

    docker://events?host=192.168.59.103&port=2375

The following example queries Docker for system wide information

    docker://info?host=192.168.59.103&port=2375