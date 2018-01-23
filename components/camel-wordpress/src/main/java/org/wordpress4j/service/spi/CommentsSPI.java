package org.wordpress4j.service.spi;

import java.util.Date;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.wordpress4j.model.Comment;
import org.wordpress4j.model.CommentOrderBy;
import org.wordpress4j.model.CommentStatus;
import org.wordpress4j.model.Context;
import org.wordpress4j.model.Order;

/**
 * Describes the Wordpress Comments API.
 * 
 * @see <a href=
 *      "https://developer.wordpress.org/rest-api/reference/comments/">Comments
 *      API Reference</a>
 * @since 0.0.1
 */
@Path("/wp")
public interface CommentsSPI  {

    //@formatter:off
    @GET
    @Path("/v{apiVersion}/comments")
    @Produces(MediaType.APPLICATION_JSON)
    List<Comment> list(@PathParam("apiVersion") String apiVersion,
                         @QueryParam("context") Context context, 
                         @QueryParam("page") Integer page, 
                         @QueryParam("per_page") Integer perPage, 
                         @QueryParam("search") String search, 
                         @QueryParam("after") Date after,       
                         @QueryParam("author") List<Integer> author,
                         @QueryParam("author_exclude") List<Integer> authorExclude,
                         @QueryParam("author_email") String authorEmail,
                         @QueryParam("before") Date before,
                         @QueryParam("exclude") List<Integer> exclude, 
                         @QueryParam("include") List<Integer> include,
                         @QueryParam("karma") Integer karma,
                         @QueryParam("offset") List<Integer> offset,
                         @QueryParam("order") Order order,
                         @QueryParam("orderby") CommentOrderBy orderBy,
                         @QueryParam("parent") List<Integer> parent,
                         @QueryParam("parent_exclude") List<Integer> parentExclude,
                         @QueryParam("post") List<Integer> post,
                         @QueryParam("status") CommentStatus status,
                         @QueryParam("type") String type);

    @GET
    @Path("/v{apiVersion}/comments/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Comment retrieve(@PathParam("apiVersion") String apiVersion, 
                     @PathParam("id") Integer id, 
                     @QueryParam("context") Context context);
    
    //@formatter:on
    @POST
    @Path("/v{apiVersion}/comments")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Comment create(@PathParam("apiVersion") String apiVersion, Comment comment);

    //@formatter:off
    @POST
    @Path("/v{apiVersion}/comments/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    Comment update(@PathParam("apiVersion") String apiVersion, 
                @PathParam("id") int id, 
                Comment post);

    @DELETE
    @Path("/v{apiVersion}/comments/{id}")
    Comment delete(@PathParam("apiVersion") String apiVersion, 
                @PathParam("id") int id, 
                @QueryParam("force") boolean force);    

}
