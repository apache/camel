package org.apache.camel.component.cxf.jaxrs;

import javax.jws.WebMethod;
import javax.jws.WebService;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.cxf.jaxrs.ext.multipart.Multipart;

@WebService
@Path("/cms")
@Consumes("multipart/form-data")
@Produces("application/xml")
public  class UploadService {
	@WebMethod
	@POST
	@Path("/upload2")
	@Consumes("multipart/form-data")
	public void addVideo(
			@Multipart(value = "contenu", type = "application/octet-stream") java.lang.Number video,
			@Multipart(value = "nom", type = "text/plain") String nom) {
	}

	@WebMethod
	@POST
	@Path("/upload")
	@Consumes("multipart/form-data")
	public void addVideo(
			@Multipart(value = "contenu", type = "application/octet-stream") java.io.InputStream video,
			@Multipart(value = "nom", type = "text/plain") String nom) {
	}

}