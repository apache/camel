package org.wordpress4j.service.impl.ignored;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.emptyCollectionOf;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.assertThat;

import java.util.List;

import org.apache.camel.component.wordpress.WordpressTestConstants;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.wordpress4j.WordpressServiceProvider;
import org.wordpress4j.model.Category;
import org.wordpress4j.model.CategorySearchCriteria;
import org.wordpress4j.service.WordpressServiceCategories;

@Ignore("Not implemented yet")
public class WordpressServiceCommentsAdapterIT {

    private static WordpressServiceCategories serviceCategories;

    @BeforeClass
    public static void before() {
        final WordpressServiceProvider serviceProvider = WordpressServiceProvider.getInstance();
        serviceProvider.init(WordpressTestConstants.WORDPRESS_DEMO_URL);
        serviceCategories = serviceProvider.getService(WordpressServiceCategories.class);
    }

    @Test
    public void testRetrieve() {
        final Category cat = serviceCategories.retrieve(1, null);
        assertThat(cat, not(nullValue()));
        assertThat(cat.getId(), is(1));
        assertThat(cat.getName(), not(isEmptyOrNullString()));
    }

    @Test
    public void testList() {
        final CategorySearchCriteria criteria = new CategorySearchCriteria();
        criteria.setPage(1);
        criteria.setPerPage(2);
        final List<Category> revisions = serviceCategories.list(criteria);
        assertThat(revisions, is(not(emptyCollectionOf(Category.class))));
        assertThat(revisions.size(), is(2));
    }
}
