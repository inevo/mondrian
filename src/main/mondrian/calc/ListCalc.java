/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// (C) Copyright 2006-2006 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.calc;

import mondrian.olap.Evaluator;

import java.util.List;

/**
 * Expression which evaluates a set of members or tuples to a list.
 *
 * @author jhyde
 * @version $Id$
 * @since Sep 27, 2005
 */
public interface ListCalc extends Calc {
    /**
     * Evaluates an expression to yield a list of members or tuples.
     *
     * @param evaluator Evaluation context
     * @return A list of members or tuples, never null.
     */
    List evaluateList(Evaluator evaluator);
}

// End ListCalc.java