/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2006-2009 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.calc.impl;

import mondrian.olap.*;
import mondrian.olap.fun.FunUtil;
import mondrian.calc.*;

// -- BEGIN GeoMondrian modification --
import com.vividsolutions.jts.geom.Geometry;
// -- END GeoMondrian modification --

import java.util.*;

/**
 * Adapter which computes a scalar or tuple expression and converts it to any
 * required type.
 *
 * @see mondrian.calc.impl.GenericIterCalc
 *
 * @author jhyde
 * @version $Id$
 * @since Sep 26, 2005
 */
public abstract class GenericCalc
        extends AbstractCalc
        implements TupleCalc,
        StringCalc, IntegerCalc, DoubleCalc, BooleanCalc, DateTimeCalc,
        VoidCalc, MemberCalc, LevelCalc, HierarchyCalc, DimensionCalc
        // -- BEGIN GeoMondrian modification --
        , GeometryCalc
        // -- END GeoMondrian modification
{
    /**
     * Creates a GenericCalc without specifying child calculated expressions.
     *
     * <p>Subclass should override {@link #getCalcs()}.
     *
     * @param exp Source expression
     */
    protected GenericCalc(Exp exp) {
        super(exp, null);
    }

    /**
     * Creates an GenericCalc.
     *
     * @param exp Source expression
     * @param calcs Child compiled expressions
     */
    protected GenericCalc(Exp exp, Calc[] calcs) {
        super(exp, calcs);
    }

    public Member[] evaluateTuple(Evaluator evaluator) {
        return (Member[]) evaluate(evaluator);
    }

    public String evaluateString(Evaluator evaluator) {
        return (String) evaluate(evaluator);
    }

    // -- BEGIN GeoMondrian modification --
    public Geometry evaluateGeometry(Evaluator evaluator) {
        return (Geometry) evaluate(evaluator);
    }
    // -- END GeoMondrian modification --

    public int evaluateInteger(Evaluator evaluator) {
        Object o = evaluate(evaluator);
        final Number number = (Number) o;
        return number == null
                ? FunUtil.IntegerNull
                : number.intValue();
    }

    public double evaluateDouble(Evaluator evaluator) {
        final Object o = evaluate(evaluator);
        final Number number = (Number) o;
        return numberToDouble(number);
    }

    public static double numberToDouble(Number number) {
        return number == null
                ? FunUtil.DoubleNull
                : number.doubleValue();
    }

    public boolean evaluateBoolean(Evaluator evaluator) {
        return (Boolean) evaluate(evaluator);
    }

    public Date evaluateDateTime(Evaluator evaluator) {
        return (Date) evaluate(evaluator);
    }

    public void evaluateVoid(Evaluator evaluator) {
        final Object result = evaluate(evaluator);
        assert result == null;
    }

    public Member evaluateMember(Evaluator evaluator) {
        return (Member) evaluate(evaluator);
    }

    public Level evaluateLevel(Evaluator evaluator) {
        return (Level) evaluate(evaluator);
    }

    public Hierarchy evaluateHierarchy(Evaluator evaluator) {
        return (Hierarchy) evaluate(evaluator);
    }

    public Dimension evaluateDimension(Evaluator evaluator) {
        return (Dimension) evaluate(evaluator);
    }
}

// End GenericCalc.java
