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

import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

import mondrian.olap.Evaluator;
import mondrian.olap.Member;
import mondrian.olap.Syntax;
import mondrian.olap.Util;
import mondrian.olap.type.GeometryType;
import mondrian.olap.type.StringType;
import mondrian.olap.type.Type;
import mondrian.spi.UserDefinedFunction;

/**
 * Implementation of the ST_Distance spatial analysis function
 *
 * Returns Util.nullValue by default if one of the arguments isn't a Geometry
 * 
 * @author etdub
 *
 */
public class STGeomFromTextUdf implements UserDefinedFunction {

    public Object execute(Evaluator evaluator, Argument[] arguments) {
        Object arg1 = arguments[0].evaluateScalar(evaluator);

        if ( arg1 instanceof String ) {
            String wkt = (String) arg1;

            Object retval = Util.nullValue;

            WKTReader wktReader = new WKTReader(); 
            
            try {
                retval = wktReader.read(wkt); 
            }
            catch(ParseException pe) {
                Logger LOGGER =
                    Logger.getLogger(STIntersectsUdf.class);
                Member[] members = evaluator.getMembers();
                LOGGER.warn("ParseException occured: " + pe.toString() + "\n" +
                        "  current context members: " + members.toString());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("ParseException stack trace", pe);
                };
                retval = Util.nullValue;
            }
            return retval;
        }
        return Util.nullValue;
    }
    
    public Type[] getParameterTypes() {
        return new Type[] {
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
        return "Makes a Geometry from a WKT string";
    }

    public String getName() {
        return "ST_GeomFromText";
    }
    
}
