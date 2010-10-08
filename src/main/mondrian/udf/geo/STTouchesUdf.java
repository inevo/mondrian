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

/**
 * Implementation of the ST_Touches spatial predicate
 * 
 * @author etdub
 *
 */
public class STTouchesUdf extends BinarySpatialPredicateBaseUdf {

    protected boolean binaryPredicate(Geometry g1, Geometry g2) {
        return g1.touches(g2);
    }

    public String getDescription() {
        return "Returns true if the 1st geometry touches the 2nd geometry";
    }

    public String getName() {
        return "ST_Touches";
    }

}
