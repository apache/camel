/**
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

package org.apache.camel.component.aws.s3;

import org.apache.camel.component.aws.s3.client.AWSS3Client;
import org.apache.camel.component.aws.s3.client.AWSS3ClientFactory;
import org.apache.camel.component.aws.s3.client.impl.IAMOptimizedAWSS3ClientImpl;
import org.apache.camel.component.aws.s3.client.impl.StandardAWSS3ClientImpl;
import org.junit.Assert;
import org.junit.Test;

public class AWSS3ClientFactoryTest {
	private static final int maxConnections = 1;

	@Test
	public void getStandardS3ClientDefault() {
		S3Configuration s3Configuration = new S3Configuration();
		AWSS3Client awss3Client = AWSS3ClientFactory.getAWSS3Client(s3Configuration, maxConnections);
		Assert.assertTrue(awss3Client instanceof StandardAWSS3ClientImpl);
	}

	@Test
	public void getStandardS3Client() {
		S3Configuration s3Configuration = new S3Configuration();
		s3Configuration.setUseIAMCredentials(false);
		AWSS3Client awss3Client = AWSS3ClientFactory.getAWSS3Client(s3Configuration, maxConnections);
		Assert.assertTrue(awss3Client instanceof StandardAWSS3ClientImpl);
	}

	@Test
	public void getIAMOptimizedS3Client() {
		S3Configuration s3Configuration = new S3Configuration();
		s3Configuration.setUseIAMCredentials(true);
		AWSS3Client awss3Client = AWSS3ClientFactory.getAWSS3Client(s3Configuration, maxConnections);
		Assert.assertTrue(awss3Client instanceof IAMOptimizedAWSS3ClientImpl);
	}
}
