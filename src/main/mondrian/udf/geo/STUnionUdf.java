/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009 GeoSOA research group, Laval University
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/

package mondrian.udf.geo;

import com.vividsolutions.jts.geom.Geometry;

import mondrian.olap.Evaluator;
import mondrian.olap.Syntax;
import mondrian.olap.Util;
import mondrian.olap.type.GeometryType;
import mondrian.olap.type.Type;
import mondrian.spi.UserDefinedFunction;

/**
 * Implementation of the ST_Union spatial analysis function
 *
 * Returns Util.nullValue by default if the arguments are not Geometries
 * 
 * @author etdub
 *
 */
public class STUnionUdf implements UserDefinedFunction {

    public Object execute(Evaluator evaluator, Argument[] arguments) {
        Object arg1 = arguments[0].evaluateScalar(evaluator);
        Object arg2 = arguments[0].evaluateScalar(evaluator);
        if (arg1 instanceof Geometry && arg2 instanceof Geometry) {
            return ((Geometry) arg1).union((Geometry) arg2);
        }
        return Util.nullValue;
    }
    
    public Type[] getParameterTypes() {
        return new Type[] {
                new GeometryType(),
                new GeometryType()
        };
    }

    public String[] getReservedWords() {
        return null;
    }

    public Type getReturnType(Type[] parameterTypes) {
        return new GeometryType();
    }

    public Syntax getSyntax() {
        return Syntax.Function;
    }
    
    public String getDescription() {
        return "Returns a geometry that is the point set union of the " +
        		"geometries";
    }

    public String getName() {
        return "ST_Union";
    }
    
}
