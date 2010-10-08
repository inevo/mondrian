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
import mondrian.olap.type.NumericType;
import mondrian.olap.type.Type;
import mondrian.spi.UserDefinedFunction;

/**
 * Implementation of the ST_Length spatial analysis function
 * In the case of LineString and MultiLineString, length is returned
 * For Polygons or MultiPolygons, total perimeter (of all rings) is returned
 * Points have a 0 length
 * @see com.vividsolutions.jts.geom.Geometry.getLength()
 *
 * Returns Util.nullValue by default if one of the arguments isn't a Geometry
 * 
 * @author etdub
 *
 */
public class STLengthUdf implements UserDefinedFunction {

    public Object execute(Evaluator evaluator, Argument[] arguments) {
        Object arg1 = arguments[0].evaluateScalar(evaluator);
        if (arg1 instanceof Geometry) {
            return ((Geometry)arg1).getLength();
        }
        return Util.nullValue;
    }
    
    public Type[] getParameterTypes() {
        return new Type[] {
                new GeometryType()
        };
    }

    public String[] getReservedWords() {
        return null;
    }

    public Type getReturnType(Type[] parameterTypes) {
        return new NumericType();
    }

    public Syntax getSyntax() {
        return Syntax.Function;
    }
    
    public String getDescription() {
        return "Returns the length of a geometry";
    }

    public String getName() {
        return "ST_Length";
    }
    
}
