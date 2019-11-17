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
package org.apache.camel.example.cdi.kubernetes;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;

/**
 * This example periodically polls the list of pods that are deployed to the
 * configured Kubernetes cluster.
 *
 * It relies on the Camel Kubernetes component and emulates the output of the
 * {@code kubectl get pods} command.
 */
public class Application {

    @ApplicationScoped
    static class KubernetesRoute extends RouteBuilder {

        @Override
        public void configure() {
            from("timer:client?period=10s").routeId("kubernetes-client")
                .onException(KubernetesClientException.class).handled(true)
                    .log(LoggingLevel.ERROR, "${exception.message}")
                    .log("Stopping the Kubernetes route...")
                    // Let's stop the route (we may want to implement a way to exit the container)
                    .to("controlbus:route?routeId=kubernetes-client&action=stop&async=true&loggingLevel=DEBUG")
                    .end()
                .to("kubernetes-pods://{{kubernetes-master-url:{{env:KUBERNETES_MASTER}}}}?oauthToken={{kubernetes-oauth-token:}}&operation=listPods")
                .log("We currently have ${body.size()} pods:")
                .process(exchange -> {
                    List<Pod> pods = exchange.getIn().getBody(List.class);
                    // Compute the length of the longer pod name
                    String tty = "%-" + (pods.stream().mapToInt(pod -> pod.getMetadata().getName().length()).max().orElse(30) + 2) + "s %-9s %-9s %-10s %s";
                    // Emulates the output of 'kubectl get pods'
                    System.out.println(String.format(tty, "NAME", "READY", "STATUS", "RESTARTS", "AGE"));
                    pods.stream()
                        .map(pod -> String.format(tty, pod.getMetadata().getName(),
                            pod.getStatus().getContainerStatuses().stream()
                                .filter(ContainerStatus::getReady)
                                .count() + "/" + pod.getStatus().getContainerStatuses().size(),
                            pod.getStatus().getPhase(),
                            pod.getStatus().getContainerStatuses().stream()
                                .mapToInt(ContainerStatus::getRestartCount).sum(),
                            formatDuration(Duration.between(ZonedDateTime.parse(pod.getStatus().getStartTime()), ZonedDateTime.now()))))
                        .forEach(System.out::println);
                });
        }
    }

    // Let's format duration the kubectl way!
    static String formatDuration(Duration duration) {
        if (Duration.ofDays(1).compareTo(duration) < 0) {
            return duration.toDays() + "d";
        } else if (Duration.ofHours(1).compareTo(duration) < 0) {
            return duration.toHours() + "h";
        } else if (Duration.ofMinutes(1).compareTo(duration) < 0) {
            return duration.toMinutes() + "m";
        } else {
            return duration.getSeconds() + "s";
        }
    }

    @Produces
    @ApplicationScoped
    @Named("properties")
    // "properties" component bean that Camel uses to lookup properties
    PropertiesComponent properties() {
        PropertiesComponent component = new PropertiesComponent();
        component.setLocation("classpath:application.properties");
        return component;
    }
}
