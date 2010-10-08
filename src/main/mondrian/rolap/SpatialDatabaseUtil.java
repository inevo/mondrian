/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009 GeoSOA research group, Laval University
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/

package mondrian.rolap.geo;

import org.apache.log4j.Logger;
import org.postgis.PGgeometry;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKTReader;

public class SpatialDatabaseUtil {

    private static final Logger LOGGER =
        Logger.getLogger(SpatialDatabaseUtil.class);
    
    // PostGIS PGgeometry to JTS Geometry
    public static Geometry PostGIStoJTS(org.postgis.PGgeometry pgGeom) {
        
        String wkt = pgGeom.toString();
        
        // strips the SRID= ... prefix, if any (we ignore SRIDs for now)
        if (wkt.startsWith(PGgeometry.SRIDPREFIX)) {
            wkt = wkt.substring(wkt.indexOf(';')+1, wkt.length());
        }
        Geometry geom = null;
        
        WKTReader wktReader = new WKTReader();
        try {
            geom = wktReader.read(wkt);
        } catch (ParseException e) {
            LOGGER.warn("Could not parse the WKT geometry: " + wkt);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("ParseException stack trace", e);
            }
        }
        
        return geom;
        
    }
}
