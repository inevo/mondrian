/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 1999-2002 Kana Software, Inc.
// Copyright (C) 2001-2009 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 2 March, 1999
*/

package mondrian.olap;

import java.util.Map;

/**
 * A <code>Hierarchy</code> is a set of members, organized into levels.
 */
public interface Hierarchy extends OlapElement, Annotated {
    /**
     * Returns the dimension this hierarchy belongs to.
     */
    Dimension getDimension();
    /**
     * Returns the levels in this hierarchy.
     *
     * <p>If a hierarchy is subject to access-control, some of the levels may
     * not be visible; use {@link SchemaReader#getHierarchyLevels} instead.
     *
     * @post return != null
     */
    Level[] getLevels();
    /**
     * Returns the default member of this hierarchy.
     *
     * <p>If a hierarchy is subject to access-control, the default member may
     * not be visible, so use {@link SchemaReader#getHierarchyDefaultMember}.
     *
     * @post return != null
     */
    Member getDefaultMember();
    /**
     * Returns the "All" member of this hierarchy.
     *
     * @post return != null
     */
    Member getAllMember();
    /**
     * Returns a special member representing the "null" value. This never
     * occurs on an axis, but may occur if functions such as <code>Lead</code>,
     * <code>NextMember</code> and <code>ParentMember</code> walk off the end
     * of the hierarchy.
     *
     * @post return != null
     */
    Member getNullMember();

    boolean hasAll();

    /**
     * Creates a member of this hierarchy. If this is the measures hierarchy, a
     * calculated member is created, and <code>formula</code> must not be null.
     */
    Member createMember(
        Member parent, Level level, String name, Formula formula);
}

// End Hierarchy.java
