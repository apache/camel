package org.wordpress4j.service.spi;

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.wordpress4j.model.Context;
import org.wordpress4j.model.DeletedModel;
import org.wordpress4j.model.Order;
import org.wordpress4j.model.User;
import org.wordpress4j.model.UserOrderBy;

/**
 * Describes the Users Wordpress API
 * 
 * @see <a href=
 *      "https://developer.wordpress.org/rest-api/reference/users/">Users API
 *      Reference</a>
 * @since 0.0.1
 */
@Path("/wp")
public interface UsersSPI {

    // @formatter:off
    @GET
    @Path("/v{apiVersion}/users")
    @Produces(MediaType.APPLICATION_JSON)
    List<User> list(@PathParam("apiVersion") String apiVersion,
                    @QueryParam("context") Context context,
                    @QueryParam("page") Integer page,
                    @QueryParam("per_page") Integer perPage,
                    @QueryParam("search") String search,
                    @QueryParam("exclude") List<Integer> exclude,
                    @QueryParam("include") List<Integer> include,
                    @QueryParam("offset") List<Integer> offset,
                    @QueryParam("order") Order order,
                    @QueryParam("orderby") UserOrderBy orderBy,
                    @QueryParam("slug") List<String> slug,
                    @QueryParam("roles") List<String> roles);
    
    @GET
    @Path("/v{apiVersion}/users/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    User retrieve(@PathParam("apiVersion") String apiVersion,
                 @PathParam("id") Integer id,
                 @QueryParam("context") Context context);
    
    @POST
    @Path("/v{apiVersion}/users")
    User create(@PathParam("apiVersion") String apiVersion,
               User user);
    
    @POST
    @Path("/v{apiVersion}/users/{id}")
    User update(@PathParam("apiVersion") String apiVersion,
               @PathParam("id") Integer id,
               User tag);
    
    /**
     * 
     * @param apiVersion
     * @param id  Unique identifier for the user. 
     * @param force  Required to be true, as users do not support trashing. 
     * @param reassignId  Reassign the deleted user's posts and links to this user ID.
     */
    @DELETE
    @Path("/v{apiVersion}/users/{id}")
    DeletedModel<User> delete(@PathParam("apiVersion") String apiVersion,
               @PathParam("id") Integer id,
               @QueryParam("force") boolean force,
               @QueryParam("reassign") Integer reassignId);
}
