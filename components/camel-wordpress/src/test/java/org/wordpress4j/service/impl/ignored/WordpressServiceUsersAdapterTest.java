package org.wordpress4j.service.impl.ignored;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.wordpress4j.model.User;
import org.wordpress4j.model.UserSearchCriteria;
import org.wordpress4j.service.WordpressServiceUsers;
import org.wordpress4j.test.WordpressMockServerTestSupport;

public class WordpressServiceUsersAdapterTest extends WordpressMockServerTestSupport {

    private static WordpressServiceUsers serviceUsers;

    @BeforeClass
    public static void before() {
        serviceUsers = serviceProvider.getService(WordpressServiceUsers.class);
    }

    @Test
    public void testRetrieveUser() {
        final User user = serviceUsers.retrieve(1);
        assertThat(user, not(nullValue()));
        assertThat(user.getId(), is(greaterThan(0)));
    }
    
    @Test
    public void testCreateUser() {
        final User entity = new User();
        entity.setEmail("bill.denbrough@derry.com");
        entity.setFirstName("Bill");
        entity.setLastName("Denbrough");
        entity.setNickname("Big Bill");
        entity.setUsername("bdenbrough");
        final User user = serviceUsers.create(entity);
        assertThat(user, not(nullValue()));
        assertThat(user.getId(), is(3));
    }
    
    @Test
    public void testListUsers() {
        final UserSearchCriteria criteria = new UserSearchCriteria();
        criteria.setPage(1);
        criteria.setPerPage(10);
        final List<User> users = serviceUsers.list(criteria);
        assertThat(users, is(not(emptyCollectionOf(User.class))));
        assertThat(users.size(), is(3));
    }
}
