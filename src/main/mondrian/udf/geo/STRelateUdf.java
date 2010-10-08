/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009 GeoSOA research group, Laval University
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/

package mondrian.udf.geo;

import org.apache.log4j.Logger;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.TopologyException;

import mondrian.olap.Evaluator;
import mondrian.olap.Member;
import mondrian.olap.Syntax;
import mondrian.olap.type.BooleanType;
import mondrian.olap.type.GeometryType;
import mondrian.olap.type.StringType;
import mondrian.olap.type.Type;
import mondrian.spi.UserDefinedFunction;

/**
 * Implementation of the ST_Relate spatial predicate
 *
 * Returns FALSE by default if one of the arguments isn't a Geometry
 * 
 * @author etdub
 *
 */
public class STRelateUdf implements UserDefinedFunction {

    public Object execute(Evaluator evaluator, Argument[] arguments) {
        Object arg1 = arguments[0].evaluateScalar(evaluator);
        Object arg2 = arguments[1].evaluateScalar(evaluator);
        Object arg3 = arguments[2].evaluateScalar(evaluator);

        if ( (arg1 instanceof Geometry) && (arg2 instanceof Geometry) ) {
            Geometry g1 = (Geometry) arg1;
            Geometry g2 = (Geometry) arg2;
            String patternMatrix = (String) arg3;
            Boolean retval = Boolean.FALSE;
            try {
                retval = g1.relate(g2, patternMatrix) ? Boolean.TRUE : Boolean.FALSE;
            }
            catch(TopologyException te) {
                Logger LOGGER =
                    Logger.getLogger(STIntersectsUdf.class);
                Member[] members = evaluator.getMembers();
                LOGGER.warn("TopologyException occured: " + te.toString() + "\n" +
                        "  current context members: " + members.toString());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("TopologyException stack trace", te);
                };
                retval = Boolean.FALSE;
            }
            return retval;
        }
        return Boolean.FALSE;
    }
    
    public Type[] getParameterTypes() {
        return new Type[] {
                new GeometryType(),
                new GeometryType(),
                new StringType()
        };
    }

    public String[] getReservedWords() {
        return null;
    }

    public Type getReturnType(Type[] parameterTypes) {
        return new BooleanType();
    }

    public Syntax getSyntax() {
        return Syntax.Function;
    }
    
    public String getDescription() {
        return "Return the result of the spatial relationship specified by the DE-9IM matrix, applied to the two geometries";
    }

    public String getName() {
        return "ST_Relate";
    }
    
}
