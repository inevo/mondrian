/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2008-2009 Pentaho
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.gui.validate.impl;

import mondrian.gui.I18n;
import mondrian.gui.validate.Messages;

/**
 * Implementation of <code>Messages</code> for Workbench.
 *
 * @author mlowery
 */
public class WorkbenchMessages implements Messages {

    private final I18n i18n;

    /**
     * Creates a WorkbenchMessages.
     *
     * @param i18n Resources
     */
    public WorkbenchMessages(I18n i18n) {
        super();
        this.i18n = i18n;
    }

    public String getFormattedString(
        String stringId,
        String defaultValue,
        Object... args)
    {
        return i18n.getFormattedString(stringId, defaultValue, args);
    }

    public String getString(String stringID, String defaultValue) {
        return i18n.getString(stringID, defaultValue);
    }
}

// End WorkbenchMessages.java
