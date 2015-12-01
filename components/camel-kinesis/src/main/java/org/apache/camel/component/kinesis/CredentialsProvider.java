package org.apache.camel.component.kinesis;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.PropertiesFileCredentialsProvider;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by alina on 30.10.15.
 */
public abstract class CredentialsProvider {

    public static AWSCredentialsProvider getAwsSessionCredentialsProvider() {
        String path = CredentialsProvider.class.getClassLoader().getResource("key.properties").getPath();
        PropertiesFileCredentialsProvider credentialsProvider = new PropertiesFileCredentialsProvider(path);
        if (StringUtils.isEmpty(credentialsProvider.getCredentials().getAWSAccessKeyId()) || StringUtils.isEmpty(credentialsProvider.getCredentials().getAWSSecretKey())) {
            throw new AmazonClientException("AWSAccesskeyId or AWSSecretKey have empty value. One possible reason is not overridden values from key.properties file. ");
        }
        return credentialsProvider;
    }
}
