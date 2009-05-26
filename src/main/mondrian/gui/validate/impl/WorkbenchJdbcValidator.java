/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2008-2009 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.gui.validate.impl;

import mondrian.gui.JdbcMetaData;
import mondrian.gui.validate.JdbcValidator;

import java.util.List;

/**
 * Implementation of <code>JdbcValidator</code> for Workbench.
 *
 * @author mlowery
 */
public class WorkbenchJdbcValidator implements JdbcValidator {

    private JdbcMetaData jdbcMetadata;

    public WorkbenchJdbcValidator(JdbcMetaData jdbcMetadata) {
        super();
        this.jdbcMetadata = jdbcMetadata;
    }

    public int getColumnDataType(
        String schemaName, String tableName, String colName)
    {
        return jdbcMetadata.getColumnDataType(schemaName, tableName, colName);
    }

    public boolean isColExists(
        String schemaName, String tableName, String colName)
    {
        return jdbcMetadata.isColExists(schemaName, tableName, colName);
    }

    public boolean isInitialized() {
        return jdbcMetadata.getErrMsg() == null;
    }

    public boolean isTableExists(String schemaName, String tableName) {
        return jdbcMetadata.isTableExists(schemaName, tableName);
    }

    public boolean isSchemaExists(String schemaName) {
        List<String> theSchemas = jdbcMetadata.getAllSchemas();
        return theSchemas != null && theSchemas.contains(schemaName);
    }
}

// End WorkbenchJdbcValidator.java
