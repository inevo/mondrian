/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2002-2002 Kana Software, Inc.
// Copyright (C) 2002-2010 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 21 March, 2002
*/
package mondrian.rolap.agg;

import mondrian.rolap.CellKey;
import mondrian.rolap.SqlStatement;

import java.util.Map;

/**
 * A <code>SegmentDataset</code> holds the values in a segment.
 *
 * @author jhyde
 * @since 21 March, 2002
 * @version $Id$
 */
interface SegmentDataset extends Iterable<Map.Entry<CellKey, Object>> {
    /**
     * Returns the value at a given coordinate, as an {@link Object}.
     *
     * @param pos Coordinate position
     * @return Value
     */
    Object getObject(CellKey pos);

    /**
     * Returns the value at a given coordinate, as an {@code int}.
     *
     * @param pos Coordinate position
     * @return Value
     */
    int getInt(CellKey pos);

    /**
     * Returns the value at a given coordinate, as a {@code double}.
     *
     * @param pos Coordinate position
     * @return Value
     */
    double getDouble(CellKey pos);

    /**
     * Returns whether the cell at a given coordinate is null.
     *
     * @param pos Coordinate position
     * @return Whether cell value is null
     */
    boolean isNull(CellKey pos);

    /**
     * Returns whether there is a value at a given coordinate.
     *
     * @param pos Coordinate position
     * @return Whether there is a value
     */
    boolean exists(CellKey pos);

    /**
     * Returns the number of bytes occupied by this dataset.
     *
     * @return number of bytes
     */
    double getBytes();

    void populateFrom(int[] pos, SegmentDataset data, CellKey key);

    /**
     * Sets the value a given ordinal.
     *
     * @param pos Ordinal
     * @param rowList Row list
     * @param column Column of row list
     */
    void populateFrom(
        int[] pos, SegmentLoader.RowList rowList, int column);

    SqlStatement.Type getType();
}

// End SegmentDataset.java
