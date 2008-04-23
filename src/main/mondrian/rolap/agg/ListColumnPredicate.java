/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2006-2007 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.rolap.agg;

import mondrian.olap.Util;
import mondrian.rolap.RolapStar;
import mondrian.rolap.StarPredicate;
import mondrian.rolap.StarColumnPredicate;
import mondrian.rolap.RolapUtil;
import mondrian.rolap.sql.SqlQuery;

import java.util.*;

/**
 * Predicate which is the union of a list of predicates, each of which applies
 * to the same, single column. It evaluates to
 * true if any of the predicates evaluates to true.
 *
 * @see mondrian.rolap.agg.ListColumnPredicate
 *
 * @author jhyde
 * @version $Id$
 * @since Nov 2, 2006
 */
public class ListColumnPredicate extends AbstractColumnPredicate {
    /**
     * List of column predicates.
     */
    private final List<StarColumnPredicate> children;
    
    /**
     * Hash map of children predicates, keyed off of the hash code of each
     * child.  Each entry in the map is a list of predicates matching that
     * hash code.
     */
    private HashMap<Integer, List<StarColumnPredicate>> childrenHashMap;

    /**
     * Creates a ListColumnPredicate
     *
     * @param column Column being constrained
     * @param list List of child predicates
     */
    public ListColumnPredicate(
        RolapStar.Column column, List<StarColumnPredicate> list) {
        super(column);
        this.children = list;
        childrenHashMap = null;
    }

    /**
     * Returns the list of child predicates.
     *
     * @return list of child predicates
     */
    public List<StarColumnPredicate> getPredicates() {
        return children;
    }

    public int hashCode() {
        return children.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof ListColumnPredicate) {
            ListColumnPredicate that = (ListColumnPredicate) obj;
            return this.children.equals(that.children);
        } else {
            return false;
        }
    }

    public void values(Collection<Object> collection) {
        for (StarColumnPredicate child : children) {
            child.values(collection);
        }
    }

    public boolean evaluate(Object value) {
        // NOTE: If we know that every predicate in the list is a
        // ValueColumnPredicate, we could optimize the evaluate method by
        // building a value list at construction time. But it's a tradeoff,
        // considering the extra time and space required.
        for (StarColumnPredicate childPredicate : children) {
            if (childPredicate.evaluate(value)) {
                return true;
            }
        }
        return false;
    }

    public boolean equalConstraint(StarPredicate that) {
        boolean isEqual = 
            that instanceof ListColumnPredicate &&
            getConstrainedColumnBitKey().equals(
                that.getConstrainedColumnBitKey());
        
        if (isEqual) {
            ListColumnPredicate thatPred = (ListColumnPredicate) that;
            if (getPredicates().size() != thatPred.getPredicates().size()) {
                isEqual = false;
            } else {
                // Create a hash map of the children predicates, if not
                // already done
                if (childrenHashMap == null) {
                    childrenHashMap =
                        new HashMap<Integer, List<StarColumnPredicate>>();
                    for (StarColumnPredicate thisChild : getPredicates()) {
                        Integer key = new Integer(thisChild.hashCode());
                        List<StarColumnPredicate> predList =
                            childrenHashMap.get(key);
                        if (predList == null) {
                            predList = new ArrayList<StarColumnPredicate>();
                        }
                        predList.add(thisChild);
                        childrenHashMap.put(key, predList);
                    }
                }
                
                // Loop through thatPred's children predicates.  There needs
                // to be a matching entry in the hash map for each child
                // predicate.
                for (StarColumnPredicate thatChild : thatPred.getPredicates()) {
                    List<StarColumnPredicate> predList =
                        childrenHashMap.get(thatChild.hashCode());
                    if (predList == null) {
                        isEqual = false;
                        break;
                    }
                    boolean foundMatch = false;
                    for (StarColumnPredicate pred : predList) {
                        if (thatChild.equalConstraint(pred)) {
                            foundMatch = true;
                            break;
                        }
                    }
                    if (!foundMatch) {
                        isEqual = false;
                        break;
                    }
                }
            }
        }
        return isEqual;
    }
    
    public void describe(StringBuilder buf) {
        buf.append("={");
        for (int j = 0; j < children.size(); j++) {
            if (j > 0) {
                buf.append(", ");
            }
            buf.append(children.get(j));
        }
        buf.append('}');
    }

    public Overlap intersect(StarColumnPredicate predicate) {
        int matchCount = 0;
        for (StarColumnPredicate flushPredicate : children) {
            final Overlap r2 = flushPredicate.intersect(predicate);
            if (r2.matched) {
                // A hit!
                if (r2.remaining == null) {
                    // Total match.
                    return r2;
                } else {
                    // Partial match.
                    predicate = r2.remaining;
                    ++matchCount;
                }
            }
        }
        if (matchCount == 0) {
            return new Overlap(false, null, 0f);
        } else {
            float selectivity =
                (float) matchCount /
                    (float) children.size();
            return new Overlap(true, predicate, selectivity);
        }
    }

    public boolean mightIntersect(StarPredicate other) {
        if (other instanceof LiteralStarPredicate) {
            return ((LiteralStarPredicate) other).getValue();
        }
        if (other instanceof ValueColumnPredicate) {
            ValueColumnPredicate valueColumnPredicate =
                (ValueColumnPredicate) other;
            return evaluate(valueColumnPredicate.getValue());
        }
        if (other instanceof ListColumnPredicate) {
            final List<Object> thatSet = new ArrayList<Object>();
            ((ListColumnPredicate) other).values(thatSet);
            for (Object o : thatSet) {
                if (evaluate(o)) {
                    return true;
                }
            }
            return false;
        }
        throw Util.newInternal("unknown constraint type " + other);
    }

    public StarColumnPredicate minus(StarPredicate predicate) {
        assert predicate != null;
        if (predicate instanceof LiteralStarPredicate) {
            LiteralStarPredicate literalStarPredicate =
                (LiteralStarPredicate) predicate;
            if (literalStarPredicate.getValue()) {
                // X minus TRUE --> FALSE
                return LiteralStarPredicate.FALSE;
            } else {
                // X minus FALSE --> X
                return this;
            }
        }
        StarColumnPredicate columnPredicate = (StarColumnPredicate) predicate;
        List<StarColumnPredicate> newChildren =
            new ArrayList<StarColumnPredicate>(children);
        int changeCount = 0;
        final Iterator<StarColumnPredicate> iterator = newChildren.iterator();
        while (iterator.hasNext()) {
            ValueColumnPredicate child =
                (ValueColumnPredicate) iterator.next();
            if (columnPredicate.evaluate(child.getValue())) {
                ++changeCount;
                iterator.remove();
            }
        }
        if (changeCount > 0) {
            return new ListColumnPredicate(getConstrainedColumn(), newChildren);
        } else {
            return this;
        }
    }

    public StarColumnPredicate orColumn(StarColumnPredicate predicate) {
        assert predicate.getConstrainedColumn() == getConstrainedColumn();
        if (predicate instanceof ListColumnPredicate) {
            ListColumnPredicate that = (ListColumnPredicate) predicate;
            final List<StarColumnPredicate> list =
                new ArrayList<StarColumnPredicate>(children);
            list.addAll(that.children);
            return new ListColumnPredicate(
                getConstrainedColumn(),
                list);
        } else {
            final List<StarColumnPredicate> list =
                new ArrayList<StarColumnPredicate>(children);
            list.add(predicate);
            return new ListColumnPredicate(
                getConstrainedColumn(),
                list);
        }
    }

    public StarColumnPredicate cloneWithColumn(RolapStar.Column column) {
        return new ListColumnPredicate(
            column,
            cloneListWithColumn(column, children));
    }

    public void toSql(SqlQuery sqlQuery, StringBuilder buf) {
        List<StarColumnPredicate> predicates = getPredicates();
        if (predicates.size() == 1) {
            predicates.get(0).toSql(sqlQuery, buf);
            return;
        }

        int notNullCount = 0;
        final RolapStar.Column column = getConstrainedColumn();
        final String expr = column.generateExprString(sqlQuery);
        final int marker = buf.length(); // to allow backtrack later
        buf.append(expr);
        ValueColumnPredicate firstNotNull = null;
        buf.append(" in (");
        for (StarColumnPredicate predicate1 : predicates) {
            final ValueColumnPredicate predicate2 =
                (ValueColumnPredicate) predicate1;
            Object key = predicate2.getValue();
            if (key == RolapUtil.sqlNullValue) {
                continue;
            }
            if (notNullCount > 0) {
                buf.append(", ");
            } else {
                firstNotNull = predicate2;
            }
            ++notNullCount;
            sqlQuery.getDialect().quote(buf, key, column.getDatatype());
        }
        buf.append(')');

        // If all of the predicates were non-null, return what we've got, for
        // example, "x in (1, 2, 3)".
        if (notNullCount >= predicates.size()) {
            return;
        }

        // There was at least one null. Reset the buffer to how we
        // originally found it, and generate a more concise expression.
        switch (notNullCount) {
        case 0:
            // Special case -- there were no values besides null.
            // Return, for example, "x is null".
            buf.setLength(marker);
            buf.append(expr);
            buf.append(" is null");
            break;

        case 1:
            // Special case -- one not-null value, and null, for
            // example "(x = 1 or x is null)".
            assert firstNotNull != null;
            buf.setLength(marker);
            buf.append('(');
            buf.append(expr);
            buf.append(" = ");
            sqlQuery.getDialect().quote(
                buf,
                firstNotNull.getValue(),
                column.getDatatype());
            buf.append(" or ");
            buf.append(expr);
            buf.append(" is null)");
            break;

        default:
            // Nulls and values, for example,
            // "(x in (1, 2) or x IS NULL)".
            String save = buf.substring(marker);
            buf.setLength(marker); // backtrack
            buf.append('(');
            buf.append(save);
            buf.append(" or ");
            buf.append(expr);
            buf.append(" is null)");
            break;
        }
    }
}

// End ListColumnPredicate.java
