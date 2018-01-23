package org.wordpress4j.service.spi;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.PostRevision;

/**
 * Describes the Wordpress Posts Revision API.
 * 
 * @see <a href=
 *      "https://developer.wordpress.org/rest-api/reference/post-revisions/">Post
 *      Revisions API Reference</a>
 * @since 0.0.1
 */
@Path("/wp")
public interface PostRevisionsSPI {

    //@formatter:off
    @GET
    @Path("/v{apiVersion}/posts/{postId}/revisions")
    @Produces(MediaType.APPLICATION_JSON)
    List<PostRevision> list(@PathParam("apiVersion") String apiVersion, 
                            @PathParam("postId") int postId, 
                            @QueryParam("context") Context context);
    
    @GET
    @Path("/v{apiVersion}/posts/{postId}/revisions/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    PostRevision retrieveRevision(@PathParam("apiVersion") String apiVersion, 
                            @PathParam("postId") int postId,
                            @PathParam("id") int revisionId,
                            @QueryParam("context") Context context); 
    
    @DELETE
    @Path("/v{apiVersion}/posts/{postId}/revisions/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    void delete(@PathParam("apiVersion") String apiVersion, 
                            @PathParam("postId") int postId,
                            @PathParam("id") int revisionId);     

}
