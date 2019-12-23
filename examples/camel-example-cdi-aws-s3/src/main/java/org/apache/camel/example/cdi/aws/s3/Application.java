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
package org.apache.camel.example.cdi.aws.s3;

import java.io.File;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;

public class Application {

    @ApplicationScoped
    static class AwsS3Route extends RouteBuilder {

        @Override
        public void configure() {
            from("aws-s3://bucket-name?deleteAfterRead=false&maxMessagesPerPoll=25&delay=5000")
            .log(LoggingLevel.INFO, "consuming", "Consumer Fired!")
            .idempotentConsumer(header("CamelAwsS3ETag"),
                    FileIdempotentRepository.fileIdempotentRepository(new File("target/file.data"), 250, 512000))
            .log(LoggingLevel.INFO, "Replay Message Sent to file:s3out ${in.header.CamelAwsS3Key}")
                .to("file:target/s3out?fileName=${in.header.CamelAwsS3Key}");
        }
        
        @Produces
        @Named("amazonS3Client")
        AmazonS3 amazonS3Client() {
            AWSCredentials credentials = new BasicAWSCredentials("XXXXX", "XXXXX");
            AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(credentials);
            AmazonS3ClientBuilder clientBuilder = AmazonS3ClientBuilder.standard().withRegion(Regions.US_WEST_1).withCredentials(credentialsProvider);
            return clientBuilder.build();
        }
    }
}
