/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009 GeoSOA research group, Laval University
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.calc;

import com.vividsolutions.jts.geom.Geometry;

import mondrian.olap.Evaluator;

/**
 * Compiled expression whose result is a {@link Geometry}.
 *
 * <p>When implementing this interface, it is convenient to extend
 * {@link mondrian.calc.impl.GeometryCalc}, but it is not required.
 *
 * @author etdub
 * @version $Id: $
 * @since Jan 12, 2008
 */
public interface GeometryCalc extends Calc {
    /**
     * Evaluates this expression to yield a {@link Geometry} value.
     *
     * @param evaluator Evaluation context
     * @return evaluation result
     */
    Geometry evaluateGeometry(Evaluator evaluator);
}

// End StringCalc.java
