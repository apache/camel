package org.apache.camel.component.bonita.api.filter;

import java.io.IOException;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

public class JsonClientFilter implements ClientResponseFilter {
 
	
	@Override
	public void filter(ClientRequestContext request, ClientResponseContext response) throws IOException {
		 String contentType = response.getHeaders().getFirst("Content-Type");
	        if (contentType.startsWith("text/plain")) {
	             String newContentType = "application/json" + contentType.substring(10);
	             response.getHeaders().putSingle("Content-Type", newContentType);
	        }
	}
 
}
