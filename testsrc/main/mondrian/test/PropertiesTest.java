/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2005-2006 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.test;

import mondrian.olap.*;

/**
 * Tests intrinsic member and cell properties as specified in OLE DB for OLAP
 * specification.
 *
 * @author anikitin
 * @since 5 July, 2005
 * @version $Id$
 */
public class PropertiesTest extends FoodMartTestCase {

    public PropertiesTest(String name) {
        super(name);
    }

    /**
     * Tests existence and values of mandatory member properties.
     */
    public void testMandatoryMemberProperties() {
        Cube salesCube = getConnection().getSchema().lookupCube("Sales",true);
        SchemaReader scr = salesCube.getSchemaReader(null);
        Member member = scr.getMemberByUniqueName(Id.Segment.toList("Customers", "All Customers", "USA", "CA"), true);
        final boolean caseSensitive =
            MondrianProperties.instance().CaseSensitive.get();

        String stringPropValue;
        Integer intPropValue;

        // I'm not sure this property has to store the same value
        // getConnection().getCatalogName() returns.

        // todo:
//        stringPropValue = (String)member.getPropertyValue("CATALOG_NAME");
//        assertEquals(getConnection().getCatalogName(), stringPropValue);

        stringPropValue = (String)member.getPropertyValue("SCHEMA_NAME");
        assertEquals(getConnection().getSchema().getName(), stringPropValue);

        // todo:
//        stringPropValue = (String)member.getPropertyValue("CUBE_NAME");
//        assertEquals(salesCube.getName(), stringPropValue);

        stringPropValue = (String)member.getPropertyValue("DIMENSION_UNIQUE_NAME");
        assertEquals(member.getDimension().getUniqueName(), stringPropValue);

        // Case sensitivity.
        stringPropValue = (String)member.getPropertyValue("dimension_unique_name", caseSensitive);
        if (caseSensitive) {
            assertNull(stringPropValue);
        } else {
            assertEquals(member.getDimension().getUniqueName(), stringPropValue);
        }

        // Non-existent property.
        stringPropValue = (String)member.getPropertyValue("DIMENSION_UNIQUE_NAME_XXXX");
        assertNull(stringPropValue);

        // Leading spaces.
        stringPropValue = (String)member.getPropertyValue(" DIMENSION_UNIQUE_NAME");
        assertNull(stringPropValue);

        // Trailing spaces.
        stringPropValue = (String)member.getPropertyValue("DIMENSION_UNIQUE_NAME  ");
        assertNull(stringPropValue);

        stringPropValue = (String)member.getPropertyValue("HIERARCHY_UNIQUE_NAME");
        assertEquals(member.getHierarchy().getUniqueName(), stringPropValue);

        // This property works in Mondrian 1.1.5 (due to XMLA support)
        stringPropValue = (String)member.getPropertyValue("LEVEL_UNIQUE_NAME");
        assertEquals(member.getLevel().getUniqueName(), stringPropValue);

        // This property works in Mondrian 1.1.5 (due to XMLA support)
        intPropValue = (Integer)member.getPropertyValue("LEVEL_NUMBER");
        assertEquals(Integer.valueOf(member.getLevel().getDepth()), intPropValue);

        // This property works in Mondrian 1.1.5 (due to XMLA support)
        stringPropValue = (String)member.getPropertyValue("MEMBER_UNIQUE_NAME");
        assertEquals(member.getUniqueName(), stringPropValue);

        stringPropValue = (String)member.getPropertyValue("MEMBER_NAME");
        assertEquals(member.getName(), stringPropValue);

        intPropValue = (Integer)member.getPropertyValue("MEMBER_TYPE");
        assertEquals(Integer.valueOf(member.getMemberType().ordinal()), intPropValue);

        stringPropValue = (String)member.getPropertyValue("MEMBER_GUID");
        assertNull(stringPropValue);

        // This property works in Mondrian 1.1.5 (due to XMLA support)
        stringPropValue = (String)member.getPropertyValue("MEMBER_CAPTION");
        assertEquals(member.getCaption(), stringPropValue);

        stringPropValue = (String)member.getPropertyValue("CAPTION");
        assertEquals(member.getCaption(), stringPropValue);

        // It's worth checking case-sensitivity for CAPTION because it is a
        // synonym, not a true property.
        stringPropValue = (String)member.getPropertyValue("caption", caseSensitive);
        if (caseSensitive) {
            assertNull(stringPropValue);
        } else {
            assertEquals(member.getCaption(), stringPropValue);
        }

        intPropValue = (Integer)member.getPropertyValue("MEMBER_ORDINAL");
        assertEquals(Integer.valueOf(member.getOrdinal()), intPropValue);

//        intPropValue = (Integer)member.getPropertyValue("CHILDREN_CARDINALITY");
//        assertEquals(Integer.valueOf(scr.getMemberChildren(member).length), intPropValue);

        intPropValue = (Integer)member.getPropertyValue("PARENT_LEVEL");
        assertEquals(Integer.valueOf(member.getParentMember().getLevel().getDepth()), intPropValue);

        stringPropValue = (String)member.getPropertyValue("PARENT_UNIQUE_NAME");
        assertEquals(member.getParentUniqueName(), stringPropValue);

        intPropValue = (Integer)member.getPropertyValue("PARENT_COUNT");
        assertEquals(Integer.valueOf(1), intPropValue);

        stringPropValue = (String)member.getPropertyValue("DESCRIPTION");
        assertEquals(member.getDescription(), stringPropValue);

        // Case sensitivity.
        stringPropValue = (String)member.getPropertyValue("desCription", caseSensitive);
        if (caseSensitive) {
            assertNull(stringPropValue);
        } else {
            assertEquals(member.getDescription(), stringPropValue);
        }
    }

    public void testGetChildCardinalityPropertyValue() {
        Cube salesCube = getConnection().getSchema().lookupCube("Sales",true);
        SchemaReader scr = salesCube.getSchemaReader(null);
        Member memberForCardinalityTest =
            scr.getMemberByUniqueName(
                Id.Segment.toList("Marital Status", "All Marital Status"),
                true);
        Integer intPropValue =
            (Integer) memberForCardinalityTest.getPropertyValue(
                "CHILDREN_CARDINALITY");
        assertEquals(Integer.valueOf(111), intPropValue);
    }

    /**
     * Tests the ability of MDX parser to pass requested member properties
     * to Result object.
     */
    public void testPropertiesMDX() {
        Result result = executeQuery(
            "SELECT {[Customers].[All Customers].[USA].[CA]} DIMENSION PROPERTIES "+nl+
                " CATALOG_NAME, SCHEMA_NAME, CUBE_NAME, DIMENSION_UNIQUE_NAME, " + nl +
                " HIERARCHY_UNIQUE_NAME, LEVEL_UNIQUE_NAME, LEVEL_NUMBER, MEMBER_UNIQUE_NAME, " + nl +
                " MEMBER_NAME, MEMBER_TYPE, MEMBER_GUID, MEMBER_CAPTION, MEMBER_ORDINAL, CHILDREN_CARDINALITY," + nl +
                " PARENT_LEVEL, PARENT_UNIQUE_NAME, PARENT_COUNT, DESCRIPTION ON COLUMNS" + nl +
                "FROM [Sales]");
        QueryAxis[] axes = result.getQuery().getAxes();
        Id[] axesProperties = axes[0].getDimensionProperties();
        String[] props = {
        "CATALOG_NAME",
        "SCHEMA_NAME",
        "CUBE_NAME",
        "DIMENSION_UNIQUE_NAME",
        "HIERARCHY_UNIQUE_NAME",
        "LEVEL_UNIQUE_NAME",
        "LEVEL_NUMBER",
        "MEMBER_UNIQUE_NAME",
        "MEMBER_NAME",
        "MEMBER_TYPE",
        "MEMBER_GUID",
        "MEMBER_CAPTION",
        "MEMBER_ORDINAL",
        "CHILDREN_CARDINALITY",
        "PARENT_LEVEL",
        "PARENT_UNIQUE_NAME",
        "PARENT_COUNT",
        "DESCRIPTION"};

        assertEquals(axesProperties.length, props.length);
        int i = 0;
        for (String prop : props) {
            assertEquals("[" + prop + "]", axesProperties[i++].toString());
        }
    }

    public void testMandatoryCellProperties() {
        Connection connection = getConnection();
        Query salesCube = connection.parseQuery(
                "select " + nl +
                " {[Measures].[Store Sales], [Measures].[Unit Sales]} on columns, " + nl +
                " {[Gender].members} on rows " + nl +
                "from [Sales]");
        Result result = connection.execute(salesCube);
        int x = 1;
        int y = 2;
        Cell cell = result.getCell(new int[] {x, y});

        assertNull(cell.getPropertyValue("BACK_COLOR"));
        assertNull(cell.getPropertyValue("CELL_EVALUATION_LIST"));
        assertEquals(y * 2 + x, cell.getPropertyValue("CELL_ORDINAL"));
        assertNull(cell.getPropertyValue("FORE_COLOR"));
        assertNull(cell.getPropertyValue("FONT_NAME"));
        assertNull(cell.getPropertyValue("FONT_SIZE"));
        assertEquals(0, cell.getPropertyValue("FONT_FLAGS"));
        assertEquals("Standard", cell.getPropertyValue("FORMAT_STRING"));
        // FORMAT is a synonym for FORMAT_STRING
        assertEquals("Standard", cell.getPropertyValue("FORMAT"));
        assertEquals("135,215", cell.getPropertyValue("FORMATTED_VALUE"));
        assertNull(cell.getPropertyValue("NON_EMPTY_BEHAVIOR"));
        assertEquals(0, cell.getPropertyValue("SOLVE_ORDER"));
        assertEquals(135215.0, ((Number) cell.getPropertyValue("VALUE")).doubleValue(), 0.1);

        // Case sensitivity.
        if (MondrianProperties.instance().CaseSensitive.get()) {
            assertNull(cell.getPropertyValue("cell_ordinal"));
            assertNull(cell.getPropertyValue("font_flags"));
            assertNull(cell.getPropertyValue("format_string"));
            assertNull(cell.getPropertyValue("format"));
            assertNull(cell.getPropertyValue("formatted_value"));
            assertNull(cell.getPropertyValue("solve_order"));
            assertNull(cell.getPropertyValue("value"));
        } else {
            assertEquals(y * 2 + x, cell.getPropertyValue("cell_ordinal"));
            assertEquals(0, cell.getPropertyValue("font_flags"));
            assertEquals("Standard", cell.getPropertyValue("format_string"));
            assertEquals("Standard", cell.getPropertyValue("format"));
            assertEquals("135,215", cell.getPropertyValue("formatted_value"));
            assertEquals(0, cell.getPropertyValue("solve_order"));
            assertEquals(135215.0, ((Number) cell.getPropertyValue("value")).doubleValue(), 0.1);
        }
    }
}

// End PropertiesTest.java
