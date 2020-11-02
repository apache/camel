package org.apache.camel.maven.component.vertx.kafka;

import java.util.Map;

import org.apache.camel.maven.component.vertx.kafka.config.ConfigFieldPojo;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "generate-kafka-config", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateKafkaConfigMojo extends AbstractMojo {

    @Parameter
    Map<String, ConfigFieldPojo> additionalConfigs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        additionalConfigs.forEach((s, configFieldPojo) -> {
            System.out.println(configFieldPojo.getName());
        });
    }
}
