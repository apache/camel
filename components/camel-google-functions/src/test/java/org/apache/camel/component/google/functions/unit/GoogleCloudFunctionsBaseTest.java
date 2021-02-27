package org.apache.camel.component.google.functions.unit;

import java.util.Arrays;
import java.util.UUID;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.testing.LocalChannelProvider;
import com.google.api.gax.grpc.testing.MockGrpcService;
import com.google.api.gax.grpc.testing.MockServiceHelper;
import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import com.google.cloud.functions.v1.CloudFunctionsServiceSettings;
import org.apache.camel.CamelContext;
import org.apache.camel.component.google.functions.GoogleCloudFunctionsComponent;
import org.apache.camel.component.google.functions.mock.MockCloudFunctionsService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;

public class GoogleCloudFunctionsBaseTest extends CamelTestSupport {

    protected static MockServiceHelper mockServiceHelper;
    protected static MockCloudFunctionsService mockCloudFunctionsService;
    protected LocalChannelProvider channelProvider;
    protected CloudFunctionsServiceClient clientMock;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GoogleCloudFunctionsComponent component = context.getComponent("google-functions", GoogleCloudFunctionsComponent.class);

        //init mock
        mockCloudFunctionsService = new MockCloudFunctionsService();
        mockServiceHelper = new MockServiceHelper(
                UUID.randomUUID().toString(),
                Arrays.<MockGrpcService> asList(mockCloudFunctionsService));
        mockServiceHelper.start();
        channelProvider = mockServiceHelper.createChannelProvider();
        CloudFunctionsServiceSettings settings = CloudFunctionsServiceSettings.newBuilder()
                .setTransportChannelProvider(channelProvider).setCredentialsProvider(NoCredentialsProvider.create())
                .build();
        clientMock = CloudFunctionsServiceClient.create(settings);

        component.getConfiguration().setClient(clientMock);
        return context;
    }

    @BeforeEach
    public void restMock() {
        mockServiceHelper.reset();
    }

    @AfterAll
    public static void releaseResources() {
        mockServiceHelper.stop();
    }
}
