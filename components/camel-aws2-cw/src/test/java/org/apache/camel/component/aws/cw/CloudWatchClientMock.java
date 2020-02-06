package org.apache.camel.component.aws.cw;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;

public class CloudWatchClientMock implements CloudWatchClient {

	@Override
	public String serviceName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public PutMetricDataResponse putMetricData(PutMetricDataRequest request)  {
		PutMetricDataResponse.Builder builder = PutMetricDataResponse.builder();
		return builder.build();
	}

}
