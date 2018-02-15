package org.apache.camel.component.aws.s3.client.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3EncryptionClient;
import com.amazonaws.services.s3.model.EncryptionMaterials;
import org.apache.camel.component.aws.s3.S3Configuration;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by cvandehey on 1/25/18.
 */
public class IAMOptimizedAWSS3ClientImplTest {
	private static final int maxConnections = 1;
	private EncryptionMaterials encryptionMaterials = mock(EncryptionMaterials.class);

	@Test
	public void iamOptimizedAWSS3ClientImplNoEncryption() {
		IAMOptimizedAWSS3ClientImpl iamOptimizedAWSS3Client = new IAMOptimizedAWSS3ClientImpl(getS3ConfigurationNoEncryption(), maxConnections);
		AmazonS3 s3Client = iamOptimizedAWSS3Client.getS3Client();
		Assert.assertNotNull(s3Client);
		Assert.assertFalse(s3Client instanceof AmazonS3EncryptionClient);
	}

	@Test
	public void iamOptimizedAWSS3ClientImplUseEncryption() {
		IAMOptimizedAWSS3ClientImpl iamOptimizedAWSS3Client = new IAMOptimizedAWSS3ClientImpl(getS3ConfigurationUseEncryption(), maxConnections);
		AmazonS3 s3Client = iamOptimizedAWSS3Client.getS3Client();
		Assert.assertNotNull(s3Client);
		Assert.assertTrue(s3Client instanceof AmazonS3EncryptionClient);
	}

	private S3Configuration getS3ConfigurationNoEncryption() {
		S3Configuration s3Configuration = mock(S3Configuration.class);
		when(s3Configuration.isUseEncryption()).thenReturn(false);
		return s3Configuration;
	}

	private S3Configuration getS3ConfigurationUseEncryption() {
		S3Configuration s3Configuration = mock(S3Configuration.class);
		when(s3Configuration.isUseEncryption()).thenReturn(true);
		return s3Configuration;
	}
}
