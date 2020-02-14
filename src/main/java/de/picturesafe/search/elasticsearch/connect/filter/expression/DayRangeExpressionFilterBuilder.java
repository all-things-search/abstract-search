/*
 * Copyright 2020 picturesafe media/data/bank GmbH
 */

package de.picturesafe.search.elasticsearch.connect.filter.expression;

import de.picturesafe.search.elasticsearch.connect.TimeZoneAware;
import de.picturesafe.search.elasticsearch.connect.util.ElasticDateUtils;
import de.picturesafe.search.expression.DayRangeExpression;
import de.picturesafe.search.expression.Expression;
import org.apache.commons.lang3.time.DateUtils;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;

import java.util.Date;

public class DayRangeExpressionFilterBuilder extends AbstractFieldExpressionFilterBuilder implements TimeZoneAware {

    private String timeZone;

    public DayRangeExpressionFilterBuilder(String timeZone) {
        this.timeZone = timeZone;
    }

    @Override
    public QueryBuilder buildFilter(ExpressionFilterBuilderContext context) {
        if (!(context.getExpression() instanceof DayRangeExpression)) {
            return null;
        }

        final DayRangeExpression dayRangeExpression = (DayRangeExpression) context.getExpression();
        if (dayRangeExpression.getFromDay() != null || dayRangeExpression.getUntilDay() != null) {
            final String fieldName = dayRangeExpression.getName();
            final RangeQueryBuilder rangeFilterBuilder = QueryBuilders.rangeQuery(fieldName);
            rangeFilterBuilder.gte(ElasticDateUtils.formatIso(dayRangeExpression.getFromDay(), timeZone));
            Date untilDate = dayRangeExpression.getUntilDay();
            if (untilDate != null) {
                untilDate = DateUtils.addDays(untilDate, 1);
                rangeFilterBuilder.lt(ElasticDateUtils.formatIso(untilDate, timeZone));
            }
            return rangeFilterBuilder;
        }
        return null;
    }

    @Override
    public boolean canHandleSearch(ExpressionFilterBuilderContext context) {
        final Expression expression = context.getExpression();
        if (expression instanceof DayRangeExpression) {
            return hasFieldConfiguration(context);
        } else {
            return false;
        }
    }
}
