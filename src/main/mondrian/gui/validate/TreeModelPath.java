/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2008-2009 Pentaho
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.gui.validate;

/**
 * A generalization of <code>javax.swing.tree.TreePath</code>.
 *
 * @author mlowery
 */
public interface TreeModelPath {
    /**
     * Returns the length of this path.
     */
    int getPathCount();

    /**
     * Returns the component of the path at the given index.
     */
    Object getPathComponent(int element);

    /**
     * Returns true if path has no components.
     */
    boolean isEmpty();
}

// End TreeModelPath.java
