/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.constraints;

import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import sirius.kernel.commons.Strings;
import sirius.search.Entity;
import sirius.search.EntityRef;
import sirius.search.Index;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

/**
 * Represents a constraint which checks if the given field has the given value.
 */
public class FieldEqual implements Constraint {
    private final String field;
    private Object value;
    private boolean isFilter;
    private boolean ignoreNull = false;

    /*
     * Use the #on(String, Object) factory method
     */
    private FieldEqual(String field, Object value) {
        // In search queries the id field must be referenced via "_id" not "id..
        if (Entity.ID.equalsIgnoreCase(field)) {
            this.field = Index.ID_FIELD;
        } else {
            this.field = field;
        }
        this.value = transformFilterValue(value);
    }

    /**
     * Converts the given value into an effective value used to filter in ES.
     * <p>
     * For example an entity will be converted into its ID or an Enum into its name.
     *
     * @param value the value to convert.
     * @return the converted value. If there is no conversion appropriate, the original value will be returned
     */
    public static Object transformFilterValue(Object value) {
        if (value != null && value.getClass().isEnum()) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Entity) {
            return ((Entity) value).getId();
        }
        if (value instanceof EntityRef) {
            return ((EntityRef<?>) value).getId();
        }
        if (value instanceof Instant) {
            value = LocalDateTime.ofInstant((Instant) value, ZoneId.systemDefault());
        }
        if (value instanceof TemporalAccessor) {
            if (((TemporalAccessor) value).isSupported(ChronoField.HOUR_OF_DAY)) {
                return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((TemporalAccessor) value);
            } else {
                return DateTimeFormatter.ISO_LOCAL_DATE.format((TemporalAccessor) value);
            }
        }

        return value;
    }

    /**
     * Creates a new constraint for the given field and value.
     *
     * @param field the field to check
     * @param value the expected value to filter for
     * @return a new constraint representing the given filter setting
     */
    public static FieldEqual on(String field, Object value) {
        return new FieldEqual(field, value);
    }

    /**
     * Makes the filter ignore <tt>null</tt> values (no constraint will be created).
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldEqual ignoreNull() {
        this.ignoreNull = true;
        return this;
    }

    /**
     * Forces this constraint to be applied as filter not as query.
     *
     * @return the constraint itself for fluent method calls
     */
    public FieldEqual asFilter() {
        isFilter = true;
        return this;
    }

    @Override
    public QueryBuilder createQuery() {
        if (Strings.isEmpty(value)) {
            // We need a filter in that case...
            return null;
        }
        if (!isFilter) {
            return QueryBuilders.termQuery(field, value);
        }
        return null;
    }

    @Override
    public FilterBuilder createFilter() {
        if (Strings.isEmpty(value)) {
            if (ignoreNull) {
                return null;
            }
            // We need a filter in that case...
            return FilterBuilders.missingFilter(field);
        }
        if (isFilter) {
            return FilterBuilders.termFilter(field, value);
        }
        return null;
    }

    @Override
    public String toString(boolean skipConstraintValues) {
        return field + " = '" + (skipConstraintValues ? "?" : value) + "'";
    }

    @Override
    public String toString() {
        return toString(false);
    }
}
