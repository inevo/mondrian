/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2001-2002 Kana Software, Inc.
// Copyright (C) 2001-2006 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 29 December, 2001
*/

package mondrian.rolap;
import mondrian.olap.Util;

/**
 * <code>StringList</code> makes it easy to build up a comma-separated string.
 *
 * @author jhyde
 * @since 29 December, 2001
 * @version $Id$
 */
class StringList
{
    private final StringBuilder buf;
    private final String first, mid, last;
    private int count;

    StringList(String first, String mid)
    {
        this.buf = new StringBuilder(first);
        this.count = 0;
        this.first = first;
        this.mid = mid;
        this.last = "";
    }
    StringList(String first)
    {
        this(first, ", ");
    }
    int getCount()
    {
        return count;
    }
    boolean isEmpty()
    {
        return count == 0;
    }
    /** Creates a new item. */
    void newItem(String s)
    {
        if (count++ > 0) {
            buf.append(mid);
        }
        buf.append(s);
    }
    /** Appends to an existing item. */
    void append(String s)
    {
        Util.assertTrue(count > 0);
        buf.append(s);
    }
    // override Object
    public String toString()
    {
        buf.append(last);
        return buf.toString();
    }
};


// End StringList.java
