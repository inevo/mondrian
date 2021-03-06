/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009-2009 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.rolap;

import mondrian.calc.Calc;

import java.util.List;

/**
 * Implementation of {@link mondrian.rolap.RolapCalculation}
 * that changes one or more dimensions, then evaluates a given calculation.
 *
 * <p>It is used to implement sets in slicers, in particular sets of tuples in
 * the slicer.
 *
 * @version $Id$
 * @author jhyde
 * @since May 15, 2009
 */
class RolapTupleCalculation implements RolapCalculation {
    private final List<RolapHierarchy> hierarchyList;
    private final Calc calc;

    /**
     * Creates a RolapTupleCalculation.
     *
     * @param hierarchyList List of hierarchies to be replaced.
     * @param calc Compiled scalar expression to compute cell
     */
    public RolapTupleCalculation(
        List<RolapHierarchy> hierarchyList,
        Calc calc)
    {
        this.hierarchyList = hierarchyList;
        this.calc = calc;
    }

    public RolapEvaluator pushSelf(RolapEvaluator evaluator) {
        final RolapEvaluator evaluator2 = evaluator.push();
        // Restore default member for each hierarchy
        // in the tuple.
        for (RolapHierarchy hierarchy : hierarchyList) {
            final int ordinal = hierarchy.getOrdinalInCube();
            final RolapMember defaultMember =
                evaluator.root.defaultMembers[ordinal];
            evaluator2.setContext(defaultMember);
        }

        evaluator2.removeCalcMember(this);
        return evaluator2;
    }

    public int getSolveOrder() {
        return Integer.MIN_VALUE;
    }

    public int getHierarchyOrdinal() {
        throw new UnsupportedOperationException();
    }

    public Calc getCompiledExpression(RolapEvaluatorRoot root) {
        return calc;
    }

    public boolean containsAggregateFunction() {
        return false;
    }

    public boolean isCalculatedInQuery() {
        return true;
    }
}

// End RolapTupleCalculation.java
