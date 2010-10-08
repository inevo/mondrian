/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2009 GeoSOA research group
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.olap.type;

/**
 * The type of a geometry expression.
 *
 * @author etdub
 * @since Jan 9, 2008
 * @version $Id: $
 */
public class GeometryType extends ScalarType {

    /**
     * Creates a geometry type.
     */
    public GeometryType() {
        super("GEOMETRY");
    }

    public boolean equals(Object obj) {
        return obj instanceof GeometryType;
    }
}

// End GeometryType.java
