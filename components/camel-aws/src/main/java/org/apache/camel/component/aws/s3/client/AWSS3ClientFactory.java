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
package org.apache.camel.component.aws.s3.client;

import org.apache.camel.component.aws.s3.S3Configuration;
import org.apache.camel.component.aws.s3.client.impl.StandardAWSS3ClientImpl;
import org.apache.camel.component.aws.s3.client.impl.IAMOptimizedAWSS3ClientImpl;

/**
 * Factory class to return the correct type of AWS S3 aws.
 */
public final class AWSS3ClientFactory {

	/**
	 * Return the correct aws s3 client (based on remote vs local).
	 * @param maxConnections max connections
	 * @return AWSS3Client
	 */
	public static AWSS3Client getAWSS3Client(S3Configuration configuration, int maxConnections) {
		return configuration.isUseIAMCredentials() ? new IAMOptimizedAWSS3ClientImpl(configuration, maxConnections)
				: new StandardAWSS3ClientImpl(configuration, maxConnections);
	}
}
