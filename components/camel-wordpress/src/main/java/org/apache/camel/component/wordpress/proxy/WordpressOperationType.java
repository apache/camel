package org.apache.camel.component.wordpress.proxy;

import org.wordpress4j.model.PostSearchCriteria;
import org.wordpress4j.model.SearchCriteria;
import org.wordpress4j.model.UserSearchCriteria;

/**
 * List of supported operations.
 */
public enum WordpressOperationType {

    post(PostSearchCriteria.class), user(UserSearchCriteria.class);

    private final Class<? extends SearchCriteria> criteriaType;

    private WordpressOperationType(Class<? extends SearchCriteria> criteriaType) {
        this.criteriaType = criteriaType;
    }

    public Class<? extends SearchCriteria> getCriteriaType() {
        return criteriaType;
    }

}
