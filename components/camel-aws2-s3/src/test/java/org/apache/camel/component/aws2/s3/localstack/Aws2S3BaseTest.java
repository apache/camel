package org.apache.camel.component.aws2.s3.localstack;

import org.apache.camel.CamelContext;
import org.apache.camel.component.aws2.s3.AWS2S3Component;
import org.apache.camel.test.testcontainers.junit5.ContainerAwareTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Aws2S3BaseTest extends ContainerAwareTestSupport {

    public static final String CONTAINER_IMAGE = "localstack:0.11.4";
    public static final String CONTAINER_NAME = "s3";

    @Override
    protected GenericContainer<?> createContainer() {
        return localstackContainer();
    }

    public static LocalStackContainer localstackContainer() {
        return new LocalStackContainer()
                .withNetworkAliases(CONTAINER_NAME)
                .withServices(Service.S3);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        AWS2S3Component s3 = context.getComponent("aws2-s3", AWS2S3Component.class);
        S3Client s3Client = S3Client
                .builder()
                .endpointOverride(localstackContainer().getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        localstackContainer().getAccessKey(), localstackContainer().getSecretKey())))
                .region(Region.of(localstackContainer().getRegion()))
                .build();
        s3.getConfiguration().setAmazonS3Client(s3Client);
        return context;
    }
}
