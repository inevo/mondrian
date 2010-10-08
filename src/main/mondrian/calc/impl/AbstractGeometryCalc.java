/*
// $Id: //open/mondrian/src/main/mondrian/calc/impl/AbstractStringCalc.java#2 $
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2006-2006 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.calc.impl;

import mondrian.olap.Evaluator;
import mondrian.olap.Exp;
import mondrian.calc.impl.AbstractCalc;
import mondrian.calc.GeometryCalc;
import mondrian.calc.Calc;

/**
 * Abstract implementation of the {@link mondrian.calc.GeometryCalc} interface.
 *
 * <p>The derived class must
 * implement the {@link #evaluateGeometry(mondrian.olap.Evaluator)} method,
 * and the {@link #evaluate(mondrian.olap.Evaluator)} method will call it.
 *
 * @author etdub
 * @version $Id: $
 * @since Jan 12, 2008
 */
public abstract class AbstractGeometryCalc
        extends AbstractCalc
        implements GeometryCalc {

    protected AbstractGeometryCalc(Exp exp, Calc[] calcs) {
        super(exp, calcs);
    }

    public Object evaluate(Evaluator evaluator) {
        return evaluateGeometry(evaluator);
    }

}

// End AbstractGeometryCalc.java
