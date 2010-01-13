/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2001-2002 Kana Software, Inc.
// Copyright (C) 2001-2009 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 10 August, 2001
*/

package mondrian.olap;

import java.util.List;
import org.apache.log4j.Logger;
import java.io.PrintWriter;

/**
 * Skeleton implementation of {@link Result}.
 *
 * @author jhyde
 * @since 10 August, 2001
 * @version $Id$
 */
public abstract class ResultBase implements Result {
    protected final Query query;
    protected final Axis[] axes;
    protected Axis slicerAxis;

    protected ResultBase(Query query, Axis[] axes) {
        this.query = query;
        this.axes = axes;
    }

    protected abstract Logger getLogger();

    public Query getQuery() {
        return query;
    }

    // implement Result
    public Axis[] getAxes() {
        return axes;
    }

    // implement Result
    public Axis getSlicerAxis() {
        return slicerAxis;
    }

    // implement Result
    public void print(PrintWriter pw) {
        for (int i = -1; i < axes.length; i++) {
            pw.println("Axis #" + (i + 1) + ":");
            printAxis(pw, i < 0 ? slicerAxis : axes[i]);
        }
        // Usually there are 3 axes: {slicer, columns, rows}. Position is a
        // {column, row} pair. We call printRows with axis=2. When it recurses
        // to axis=-1, it prints.
        int[] pos = new int[axes.length];
        printRows(pw, axes.length - 1, pos);
    }

    private void printRows(PrintWriter pw, int axis, int[] pos) {
        if (axis < 0) {
            printCell(pw, pos);
        } else {
            Axis _axis = axes[axis];
            List<Position> positions = _axis.getPositions();
            for (int i = 0; i < positions.size(); i++) {
                pos[axis] = i;
                if (axis == 0) {
                    int row =
                        axis + 1 < pos.length
                            ? pos[axis + 1]
                            : 0;
                    pw.print("Row #" + row + ": ");
                }
                printRows(pw, axis - 1, pos);
                if (axis == 0) {
                    pw.println();
                }
            }
        }
    }

    private void printAxis(PrintWriter pw, Axis axis) {
        List<Position> positions = axis.getPositions();
        for (Position position : positions) {
            boolean firstTime = true;
            pw.print("{");
            for (Member member : position) {
                if (member.getDimension().isHighCardinality()) {
                    pw.println(" -- High cardinality dimension --}");
                    return;
                }
                if (! firstTime) {
                    pw.print(", ");
                }
                pw.print(member.getUniqueName());
                firstTime = false;
            }
            pw.println("}");
        }
    }

    private void printCell(PrintWriter pw, int[] pos) {
        Cell cell = getCell(pos);
        pw.print(cell.getFormattedValue());
    }

    /**
     * Returns the current member of a given hierarchy at a given location.
     *
     * @param pos Coordinates in cell set
     * @param hierarchy Hierarchy
     * @return current member of given hierarchy
     */
    public Member getMember(int[] pos, Hierarchy hierarchy) {
        for (int i = -1; i < axes.length; i++) {
            Axis axis = slicerAxis;
            int index = 0;
            if (i >= 0) {
                axis = axes[i];
                index = pos[i];
            }
            List<Position> positions = axis.getPositions();
            Position position = positions.get(index);
            for (Member member : position) {
                if (member.getHierarchy() == hierarchy) {
                    return member;
                }
            }
        }
        return hierarchy.getHierarchy().getDefaultMember();
    }

    public void close() {
    }
}


// End ResultBase.java
