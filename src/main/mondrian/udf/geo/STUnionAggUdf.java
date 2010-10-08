/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009 GeoSOA research group, Laval University
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/

package mondrian.udf.geo;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;

import mondrian.olap.Evaluator;
import mondrian.olap.Member;
import mondrian.olap.Syntax;
import mondrian.olap.Util;
import mondrian.olap.type.GeometryType;
import mondrian.olap.type.MemberType;
import mondrian.olap.type.SetType;
import mondrian.olap.type.StringType;
import mondrian.olap.type.Type;
import mondrian.spi.UserDefinedFunction;

/**
 * experimental
 * 
 * @author etdub
 *
 */
public class STUnionAggUdf implements UserDefinedFunction {

    public Object execute(Evaluator evaluator, Argument[] arguments) {
        Object arg0 = arguments[0].evaluateScalar(evaluator);
        Object arg1 = arguments[1].evaluateScalar(evaluator);

        if (arg0 instanceof List<?>) {
            List<Member> memberSet = (List<Member>) arg0;
            if ( arg1 instanceof String ) {
                String propName = (String) arg1;

                Geometry agg = null;

                try {
                    
                    List<Geometry> geoms = new ArrayList<Geometry>();

                    for (Member m : memberSet) {
                        Object o = m.getPropertyValue(propName);
                        if(o instanceof Geometry) {
                            geoms.add((Geometry) o);
                        }
                    }

                    agg = UnaryUnionOp.union(geoms);
                    
                    return agg == null ? Util.nullValue : agg;
                    
                }
                catch (TopologyException te) {
                    Logger LOGGER =
                        Logger.getLogger(STIntersectsUdf.class);
                    Member[] members = evaluator.getMembers();
                    LOGGER.warn("TopologyException occured: " + te.toString() + "\n" +
                            "  current context members: " + members.toString());
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("TopologyException stack trace", te);
                    };
                    return Util.nullValue;
                }
                catch (IllegalArgumentException iae) {

                }

                Object retval = Util.nullValue;
                return retval;
            }
        }

        return Util.nullValue;
    }

    public Type[] getParameterTypes() {
        return new Type[] {
                new SetType(MemberType.Unknown),
                new StringType()
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
        return "union";
    }

    public String getName() {
        return "ST_UnionAgg";
    }

}
