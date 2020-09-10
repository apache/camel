package org.apache.camel.component.aws2.s3.localstack;

import java.net.URI;

import org.apache.camel.CamelContext;
import org.apache.camel.component.aws2.s3.AWS2S3Component;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.apache.camel.test.testcontainers.junit5.Wait;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Aws2S3BaseTest extends ContainerAwareTestSupport {

    public static final String CONTAINER_IMAGE = "localstack/localstack:0.11.4";
    public static final String CONTAINER_NAME = "s3";

    @Override
    protected GenericContainer<?> createContainer() {
        return localstackContainer();
    }

    public static GenericContainer localstackContainer() {
        return new GenericContainer(CONTAINER_IMAGE)
                .withNetworkAliases(CONTAINER_NAME)
                .withEnv("SERVICES", "s3")
                .withExposedPorts(4572)
                .waitingFor(Wait.forListeningPort())
                .waitingFor(Wait.forLogMessageContaining("Ready.", 1));
    }

    public String getS3Url() {
        return String.format(
                "%s:%d",
                getContainerHost(CONTAINER_NAME),
                getContainerPort(CONTAINER_NAME, 4572));
    }

    public S3Client getS3Client() {
        S3Client s3Client = S3Client
                .builder()
                .endpointOverride(URI.create("http://" + getS3Url()))
                .region(Region.EU_WEST_1)
                .build();
        return s3Client;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        AWS2S3Component s3 = context.getComponent("aws2-s3", AWS2S3Component.class);
        s3.getConfiguration().setAmazonS3Client(getS3Client());
        return context;
    }
}
