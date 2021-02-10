package org.apache.camel.component.google.storage;

import java.util.Map;

import org.apache.camel.component.extension.verifier.DefaultComponentVerifierExtension;
import org.apache.camel.component.extension.verifier.ResultBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorBuilder;
import org.apache.camel.component.extension.verifier.ResultErrorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleCloudStorageComponentVerifierExtension extends DefaultComponentVerifierExtension {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCloudStorageComponentVerifierExtension.class);

    protected GoogleCloudStorageComponentVerifierExtension() {
        super("google-storage");
    }

    protected GoogleCloudStorageComponentVerifierExtension(String scheme) {
        super(scheme);
    }

    // *********************************
    // Parameters validation
    // *********************************

    @Override
    protected Result verifyParameters(Map<String, Object> parameters) {
        LOG.debug("verifyParameters={}", parameters);
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.PARAMETERS)
                .error(ResultErrorHelper.requiresOption("applicationCredentials", parameters));

        // Validate using the catalog

        super.verifyParametersAgainstCatalog(builder, parameters);

        return builder.build();
    }

    // *********************************
    // Connectivity validation
    // *********************************

    @Override
    protected Result verifyConnectivity(Map<String, Object> parameters) {
        LOG.debug("verifyConnectivity={}", parameters);
        ResultBuilder builder = ResultBuilder.withStatusAndScope(Result.Status.OK, Scope.CONNECTIVITY);

        try {
            GoogleCloudStorageComponentConfiguration configuration
                    = setProperties(new GoogleCloudStorageComponentConfiguration(), parameters);
            /*
            if (!S3Client.serviceMetadata().regions().contains(Region.of(configuration.getRegion()))) {
                ResultErrorBuilder errorBuilder = ResultErrorBuilder.withCodeAndDescription(
                        VerificationError.StandardCode.ILLEGAL_PARAMETER, "The service is not supported in this region");
                return builder.error(errorBuilder.build()).build();
            }
            AwsBasicCredentials cred = AwsBasicCredentials.create(configuration.getAccessKey(), configuration.getSecretKey());
            S3ClientBuilder clientBuilder = S3Client.builder();
            S3Client client = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred))
                    .region(Region.of(configuration.getRegion())).build();
            client.listBuckets();
            */
            /*
            } catch (SdkClientException e) {
            ResultErrorBuilder errorBuilder
                    = ResultErrorBuilder.withCodeAndDescription(VerificationError.StandardCode.AUTHENTICATION, e.getMessage())
                            .detail("aws_s3_exception_message", e.getMessage())
                            .detail(VerificationError.ExceptionAttribute.EXCEPTION_CLASS, e.getClass().getName())
                            .detail(VerificationError.ExceptionAttribute.EXCEPTION_INSTANCE, e);
            
            builder.error(errorBuilder.build());
            */
        } catch (Exception e) {
            builder.error(ResultErrorBuilder.withException(e).build());
        }

        return builder.build();
    }

}
