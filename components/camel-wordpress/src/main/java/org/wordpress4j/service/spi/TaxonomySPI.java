package org.wordpress4j.service.spi;

import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.Taxonomy;

@Path("/wp")
public interface TaxonomySPI {

    //@formatter:off
    @GET
    @Path("/v{apiVersion}/taxonomies")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Taxonomy> list(@PathParam("apiVersion") String apiVersion,
                               @QueryParam("context") Context context,
                               @QueryParam("type") String postType);
    
    @GET
    @Path("/v{apiVersion}/taxonomies/{taxonomy}")
    @Produces(MediaType.APPLICATION_JSON)
    Taxonomy retrieve(@PathParam("apiVersion") String apiVersion,
                               @QueryParam("context") Context context,
                               @PathParam("taxonomy") String taxonomy);    
}
