/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2003-2010 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, Feb 21, 2003
*/
package mondrian.test;

import junit.framework.Assert;
import mondrian.olap.*;

import java.util.*;

/**
 * <code>AccessControlTest</code> is a set of unit-tests for access-control.
 * For these tests, all of the roles are of type RoleImpl.
 *
 * @see Role
 *
 * @author jhyde
 * @since Feb 21, 2003
 * @version $Id$
 */
public class AccessControlTest extends FoodMartTestCase {

    private static final String BiServer1574Role1 =
        "<Role name=\"role1\">\n"
        + " <SchemaGrant access=\"none\">\n"
        + "  <CubeGrant cube=\"Warehouse\" access=\"all\">\n"
        + "   <HierarchyGrant hierarchy=\"[Store Size in SQFT]\" access=\"custom\" rollupPolicy=\"partial\">\n"
        + "    <MemberGrant member=\"[Store Size in SQFT].[20319]\" access=\"all\"/>\n"
        + "    <MemberGrant member=\"[Store Size in SQFT].[21215]\" access=\"none\"/>\n"
        + "   </HierarchyGrant>\n"
        + "   <HierarchyGrant hierarchy=\"[Store Type]\" access=\"custom\" rollupPolicy=\"partial\">\n"
        + "    <MemberGrant member=\"[Store Type].[Supermarket]\" access=\"all\"/>\n"
        + "   </HierarchyGrant>\n"
        + "  </CubeGrant>\n"
        + " </SchemaGrant>\n"
        + "</Role>";

    public AccessControlTest(String name) {
        super(name);
    }

    public void testSchemaReader() {
        final TestContext testContext = getTestContext();
        final Connection connection = testContext.getConnection();
        Schema schema = connection.getSchema();
        final boolean fail = true;
        Cube cube = schema.lookupCube("Sales", fail);
        final SchemaReader schemaReader =
            cube.getSchemaReader(connection.getRole());
        final SchemaReader schemaReader1 = schemaReader.withoutAccessControl();
        final SchemaReader schemaReader2 = schemaReader1.withoutAccessControl();
    }

    public void testGrantDimensionNone() {
        final Connection connection =
            getTestContext().getFoodMartConnection(false);
        TestContext testContext = getTestContext(connection);
        RoleImpl role = ((RoleImpl) connection.getRole()).makeMutableClone();
        Schema schema = connection.getSchema();
        Cube salesCube = schema.lookupCube("Sales", true);
        // todo: add Schema.lookupDimension
        final SchemaReader schemaReader = salesCube.getSchemaReader(role);
        Dimension genderDimension =
            (Dimension) schemaReader.lookupCompound(
                salesCube, Id.Segment.toList("Gender"), true,
                Category.Dimension);
        role.grant(genderDimension, Access.NONE);
        role.makeImmutable();
        connection.setRole(role);
        testContext.assertAxisThrows(
            "[Gender].children",
            "MDX object '[Gender]' not found in cube 'Sales'");
    }

    public void testRoleMemberAccessNonExistentMemberFails() {
        final TestContext testContext = TestContext.create(
            null, null, null, null, null,
            "<Role name=\"Role1\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"partial\">\n"
            + "        <MemberGrant member=\"[Store].[USA].[Non Existent]\" access=\"all\"/>\n"
            + "      </HierarchyGrant>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>")
            .withRole("Role1");
        testContext.assertQueryThrows(
            "select {[Store].Children} on 0 from [Sales]",
            "Member '[Store].[USA].[Non Existent]' not found");
    }

    public void testRoleMemberAccess() {
        final Connection connection = getRestrictedConnection();
        // because CA has access
        assertMemberAccess(connection, Access.CUSTOM, "[Store].[USA]");
        assertMemberAccess(connection, Access.ALL, "[Store].[Mexico]");
        assertMemberAccess(connection, Access.NONE, "[Store].[Mexico].[DF]");
        assertMemberAccess(
            connection, Access.NONE, "[Store].[Mexico].[DF].[Mexico City]");
        assertMemberAccess(connection, Access.NONE, "[Store].[Canada]");
        assertMemberAccess(
            connection, Access.NONE, "[Store].[Canada].[BC].[Vancouver]");
        assertMemberAccess(
            connection, Access.ALL, "[Store].[USA].[CA].[Los Angeles]");
        assertMemberAccess(
            connection, Access.NONE, "[Store].[USA].[CA].[San Diego]");
        // USA deny supercedes OR grant
        assertMemberAccess(
            connection, Access.NONE, "[Store].[USA].[OR].[Portland]");
        assertMemberAccess(
            connection, Access.NONE, "[Store].[USA].[WA].[Seattle]");
        assertMemberAccess(connection, Access.NONE, "[Store].[USA].[WA]");
        // above top level
        assertMemberAccess(connection, Access.NONE, "[Store].[All Stores]");
    }

    private void assertMemberAccess(
        final Connection connection,
        Access expectedAccess,
        String memberName)
    {
        final Role role = connection.getRole(); // restricted
        Schema schema = connection.getSchema();
        final boolean fail = true;
        Cube salesCube = schema.lookupCube("Sales", fail);
        final SchemaReader schemaReader =
            salesCube.getSchemaReader(null); // unrestricted
        final Member member =
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(memberName), true);
        final Access actualAccess = role.getAccess(member);
        Assert.assertEquals(memberName, expectedAccess, actualAccess);
    }

    private void assertCubeAccess(
        final Connection connection,
        Access expectedAccess,
        String cubeName)
    {
        final Role role = connection.getRole();
        Schema schema = connection.getSchema();
        final boolean fail = true;
        Cube cube = schema.lookupCube(cubeName, fail);
        final Access actualAccess = role.getAccess(cube);
        Assert.assertEquals(cubeName, expectedAccess, actualAccess);
    }

    private void assertHierarchyAccess(
        final Connection connection,
        Access expectedAccess,
        String cubeName,
        String hierarchyName)
    {
        final Role role = connection.getRole();
        Schema schema = connection.getSchema();
        final boolean fail = true;
        Cube cube = schema.lookupCube(cubeName, fail);
        final SchemaReader schemaReader =
            cube.getSchemaReader(null); // unrestricted
        final Hierarchy hierarchy =
            (Hierarchy) schemaReader.lookupCompound(
                cube, Util.parseIdentifier(hierarchyName), fail,
                Category.Hierarchy);

        final Access actualAccess = role.getAccess(hierarchy);
        Assert.assertEquals(cubeName, expectedAccess, actualAccess);
    }

    private Role.HierarchyAccess getHierarchyAccess(
        final Connection connection,
        String cubeName,
        String hierarchyName)
    {
        final Role role = connection.getRole();
        Schema schema = connection.getSchema();
        final boolean fail = true;
        Cube cube = schema.lookupCube(cubeName, fail);
        final SchemaReader schemaReader =
            cube.getSchemaReader(null); // unrestricted
        final Hierarchy hierarchy =
            (Hierarchy) schemaReader.lookupCompound(
                cube, Util.parseIdentifier(hierarchyName), fail,
                Category.Hierarchy);

        return role.getAccessDetails(hierarchy);
    }

    public void testGrantHierarchy1a() {
        // assert: can access Mexico (explicitly granted)
        // assert: can not access Canada (explicitly denied)
        // assert: can access USA (rule 3 - parent of allowed member San
        // Francisco)
        getRestrictedTestContext().assertAxisReturns(
            "[Store].level.members",
            "[Store].[Mexico]\n" + "[Store].[USA]");
    }

    public void testGrantHierarchy1aAllMembers() {
        // assert: can access Mexico (explicitly granted)
        // assert: can not access Canada (explicitly denied)
        // assert: can access USA (rule 3 - parent of allowed member San
        // Francisco)
        getRestrictedTestContext().assertAxisReturns(
            "[Store].level.allmembers",
            "[Store].[Mexico]\n" + "[Store].[USA]");
    }

    public void testGrantHierarchy1b() {
        // can access Mexico (explicitly granted) which is the first accessible
        // one
        getRestrictedTestContext().assertAxisReturns(
            "[Store].defaultMember",
            "[Store].[Mexico]");
    }

    public void testGrantHierarchy1c() {
        // can access Mexico (explicitly granted) which is the first accessible
        // one
        getRestrictedTestContext().assertAxisReturns(
            "[Customers].defaultMember",
            "[Customers].[Canada].[BC]");
    }

    public void testGrantHierarchy2() {
        // assert: can access California (parent of allowed member)
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisReturns(
            "[Store].[USA].children",
            "[Store].[USA].[CA]");
        testContext.assertAxisReturns(
            "[Store].[USA].children",
            "[Store].[USA].[CA]");
        testContext.assertAxisReturns(
            "[Store].[USA].[CA].children",
            "[Store].[USA].[CA].[Los Angeles]\n"
            + "[Store].[USA].[CA].[San Francisco]");
    }

    public void testGrantHierarchy3() {
        // assert: can not access Washington (child of denied member)
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisThrows("[Store].[USA].[WA]", "not found");
    }

    private TestContext getRestrictedTestContext() {
        return new DelegatingTestContext(getTestContext()) {
            public Connection getConnection() {
                return getRestrictedConnection();
            }
        };
    }

    public void testGrantHierarchy4() {
        // assert: can not access Oregon (rule 1 - order matters)
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisThrows(
            "[Store].[USA].[OR].children", "not found");
    }

    public void testGrantHierarchy5() {
        // assert: can not access All (above top level)
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisThrows("[Store].[All Stores]", "not found");
        testContext.assertAxisReturns(
            "[Store].members",
                // note:
                // no: [All Stores] -- above top level
                // no: [Canada] -- not explicitly allowed
                // yes: [Mexico] -- explicitly allowed -- and all its children
                //      except [DF]
                // no: [Mexico].[DF]
                // yes: [USA] -- implicitly allowed
                // yes: [CA] -- implicitly allowed
                // no: [OR], [WA]
                // yes: [San Francisco] -- explicitly allowed
                // no: [San Diego]
            "[Store].[Mexico]\n"
            + "[Store].[Mexico].[Guerrero]\n"
            + "[Store].[Mexico].[Guerrero].[Acapulco]\n"
            + "[Store].[Mexico].[Guerrero].[Acapulco].[Store 1]\n"
            + "[Store].[Mexico].[Jalisco]\n"
            + "[Store].[Mexico].[Jalisco].[Guadalajara]\n"
            + "[Store].[Mexico].[Jalisco].[Guadalajara].[Store 5]\n"
            + "[Store].[Mexico].[Veracruz]\n"
            + "[Store].[Mexico].[Veracruz].[Orizaba]\n"
            + "[Store].[Mexico].[Veracruz].[Orizaba].[Store 10]\n"
            + "[Store].[Mexico].[Yucatan]\n"
            + "[Store].[Mexico].[Yucatan].[Merida]\n"
            + "[Store].[Mexico].[Yucatan].[Merida].[Store 8]\n"
            + "[Store].[Mexico].[Zacatecas]\n"
            + "[Store].[Mexico].[Zacatecas].[Camacho]\n"
            + "[Store].[Mexico].[Zacatecas].[Camacho].[Store 4]\n"
            + "[Store].[Mexico].[Zacatecas].[Hidalgo]\n"
            + "[Store].[Mexico].[Zacatecas].[Hidalgo].[Store 12]\n"
            + "[Store].[Mexico].[Zacatecas].[Hidalgo].[Store 18]\n"
            + "[Store].[USA]\n"
            + "[Store].[USA].[CA]\n"
            + "[Store].[USA].[CA].[Los Angeles]\n"
            + "[Store].[USA].[CA].[Los Angeles].[Store 7]\n"
            + "[Store].[USA].[CA].[San Francisco]\n"
            + "[Store].[USA].[CA].[San Francisco].[Store 14]");
    }

    public void testGrantHierarchy6() {
        // assert: parent if at top level is null
        getRestrictedTestContext().assertAxisReturns(
            "[Customers].[USA].[CA].parent",
            "");
    }

    public void testGrantHierarchy7() {
        // assert: members above top level do not exist
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisThrows(
                "[Customers].[Canada].children",
                "MDX object '[Customers].[Canada]' not found in cube 'Sales'");
    }

    public void testGrantHierarchy8() {
        // assert: can not access Catherine Abel in San Francisco (below bottom
        // level)
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisThrows(
            "[Customers].[USA].[CA].[San Francisco].[Catherine Abel]",
            "not found");
        testContext.assertAxisReturns(
            "[Customers].[USA].[CA].[San Francisco].children",
            "");
        Axis axis = testContext.executeAxis("[Customers].members");
        // 13 states, 109 cities
        Assert.assertEquals(122, axis.getPositions().size());
    }

    public void testGrantHierarchy8AllMembers() {
        // assert: can not access Catherine Abel in San Francisco (below bottom
        // level)
        final TestContext testContext = getRestrictedTestContext();
        testContext.assertAxisThrows(
            "[Customers].[USA].[CA].[San Francisco].[Catherine Abel]",
            "not found");
        testContext.assertAxisReturns(
            "[Customers].[USA].[CA].[San Francisco].children",
            "");
        Axis axis = testContext.executeAxis("[Customers].allmembers");
        // 13 states, 109 cities
        Assert.assertEquals(122, axis.getPositions().size());
    }

    /**
     * Tests that we only aggregate over SF, LA, even when called from
     * functions.
     */
    public void testGrantHierarchy9() {
        // Analysis services doesn't allow aggregation within calculated
        // measures, so use the following query to generate the results:
        //
        //   with member [Store].[SF LA] as
        //     'Aggregate({[USA].[CA].[San Francisco], [Store].[USA].[CA].[Los
        //     Angeles]})'
        //   select {[Measures].[Unit Sales]} on columns,
        //    {[Gender].children} on rows
        //   from Sales
        //   where ([Marital Status].[S], [Store].[SF LA])
        final TestContext tc = new RestrictedTestContext();
        tc.assertQueryReturns(
            "with member [Measures].[California Unit Sales] as "
            + " 'Aggregate({[Store].[USA].[CA].children}, [Measures].[Unit Sales])'\n"
            + "select {[Measures].[California Unit Sales]} on columns,\n"
            + " {[Gender].children} on rows\n"
            + "from Sales\n"
            + "where ([Marital Status].[S])",
            "Axis #0:\n"
            + "{[Marital Status].[S]}\n"
            + "Axis #1:\n"
            + "{[Measures].[California Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[F]}\n"
            + "{[Gender].[M]}\n"
            + "Row #0: 6,636\n"
            + "Row #1: 7,329\n");
    }

    public void testGrantHierarchyA() {
        final TestContext tc = new RestrictedTestContext();
        // assert: totals for USA include missing cells
        tc.assertQueryReturns(
            "select {[Unit Sales]} on columns,\n"
            + "{[Store].[USA], [Store].[USA].children} on rows\n"
            + "from [Sales]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[CA]}\n"
            + "Row #0: 266,773\n"
            + "Row #1: 74,748\n");
    }

    public void _testSharedObjectsInGrantMappingsBug() {
        final TestContext testContext = new TestContext() {
            public Connection getConnection() {
                boolean mustGet = true;
                Connection connection = super.getConnection();
                Schema schema = connection.getSchema();
                Cube salesCube = schema.lookupCube("Sales", mustGet);
                Cube warehouseCube = schema.lookupCube("Warehouse", mustGet);
                Hierarchy measuresInSales = salesCube.lookupHierarchy(
                    new Id.Segment("Measures", Id.Quoting.UNQUOTED), false);
                Hierarchy storeInWarehouse = warehouseCube.lookupHierarchy(
                    new Id.Segment("Store", Id.Quoting.UNQUOTED), false);

                RoleImpl role = new RoleImpl();
                role.grant(schema, Access.NONE);
                role.grant(salesCube, Access.NONE);
                // For using hierarchy Measures in #assertExprThrows
                Role.RollupPolicy rollupPolicy = Role.RollupPolicy.FULL;
                role.grant(
                    measuresInSales, Access.ALL, null, null, rollupPolicy);
                role.grant(warehouseCube, Access.NONE);
                role.grant(storeInWarehouse.getDimension(), Access.ALL);

                role.makeImmutable();
                connection.setRole(role);
                return connection;
            }
        };
        // Looking up default member on dimension Store in cube Sales should
        // fail.
        testContext.assertExprThrows(
            "[Store].DefaultMember",
            "'[Store]' not found in cube 'Sales'");
    }

    public void testNoAccessToCube() {
        final TestContext tc = new RestrictedTestContext();
        tc.assertQueryThrows("select from [HR]", "MDX cube 'HR' not found");
    }

    private Connection getRestrictedConnection() {
        return getRestrictedConnection(true);
    }

    /**
     * Returns a connection with limited access to the schema.
     *
     * @param restrictCustomers true to restrict access to the customers
     * dimension. This will change the defaultMember of the dimension,
     * all cell values will be null because there are no sales data
     * for Canada
     *
     * @return restricted connection
     */
    private Connection getRestrictedConnection(boolean restrictCustomers) {
        Connection connection = getTestContext().getFoodMartConnection(false);
        RoleImpl role = new RoleImpl();
        Schema schema = connection.getSchema();
        final boolean fail = true;
        Cube salesCube = schema.lookupCube("Sales", fail);
        final SchemaReader schemaReader = salesCube.getSchemaReader(null);
        Hierarchy storeHierarchy = salesCube.lookupHierarchy(
                new Id.Segment("Store", Id.Quoting.UNQUOTED), false);
        role.grant(schema, Access.ALL_DIMENSIONS);
        role.grant(salesCube, Access.ALL);
        Level nationLevel =
            Util.lookupHierarchyLevel(storeHierarchy, "Store Country");
        Role.RollupPolicy rollupPolicy = Role.RollupPolicy.FULL;
        role.grant(
            storeHierarchy, Access.CUSTOM, nationLevel, null, rollupPolicy);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier("[Store].[All Stores].[USA].[OR]"), fail),
            Access.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier("[Store].[All Stores].[USA]"), fail),
            Access.NONE);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[USA].[CA].[San Francisco]"), fail),
            Access.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[USA].[CA].[Los Angeles]"), fail),
            Access.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[Mexico]"), fail),
            Access.ALL);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[Mexico].[DF]"), fail),
            Access.NONE);
        role.grant(
            schemaReader.getMemberByUniqueName(
                Util.parseIdentifier(
                    "[Store].[All Stores].[Canada]"), fail),
            Access.NONE);
        if (restrictCustomers) {
            Hierarchy customersHierarchy =
                salesCube.lookupHierarchy(
                    new Id.Segment("Customers", Id.Quoting.UNQUOTED), false);
            Level stateProvinceLevel =
                Util.lookupHierarchyLevel(customersHierarchy, "State Province");
            Level customersCityLevel =
                Util.lookupHierarchyLevel(customersHierarchy, "City");
            role.grant(
                customersHierarchy,
                Access.CUSTOM,
                stateProvinceLevel,
                customersCityLevel,
                rollupPolicy);
            role.grant(
                schemaReader.getMemberByUniqueName(
                    Util.parseIdentifier("[Customers].[All Customers]"), fail),
                    Access.ALL);
        }

        // No access to HR cube.
        Cube hrCube = schema.lookupCube("HR", fail);
        role.grant(hrCube, Access.NONE);

        role.makeImmutable();
        connection.setRole(role);
        return connection;
    }

    /* todo: test that access to restricted measure fails (will not work --
    have not fixed Cube.getMeasures) */
    private class RestrictedTestContext extends TestContext {
        public synchronized Connection getFoodMartConnection() {
            return getRestrictedConnection(false);
        }
    }

    /**
     * Test context where the [Store] hierarchy has restricted access
     * and cell values are rolled up with 'partial' policy.
     */
    private final TestContext rollupTestContext =
        TestContext.create(
            null, null, null, null, null,
            "<Role name=\"Role1\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"partial\">\n"
            + "        <MemberGrant member=\"[Store].[USA]\" access=\"all\"/>\n"
            + "        <MemberGrant member=\"[Store].[USA].[CA]\" access=\"none\"/>\n"
            + "      </HierarchyGrant>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>")
            .withRole("Role1");

    /**
     * Basic test of partial rollup policy. [USA] = [OR] + [WA], not
     * the usual [CA] + [OR] + [WA].
     */
    public void testRollupPolicyBasic() {
        rollupTestContext.assertQueryReturns(
            "select {[Store].[USA], [Store].[USA].Children} on 0\n"
            + "from [Sales]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[OR]}\n"
            + "{[Store].[USA].[WA]}\n"
            + "Row #0: 192,025\n"
            + "Row #0: 67,659\n"
            + "Row #0: 124,366\n");
    }

    /**
     * The total for [Store].[All Stores] is similarly reduced. All
     * children of [All Stores] are visible, but one grandchild is not.
     * Normally the total is 266,773.
     */
    public void testRollupPolicyAll() {
        rollupTestContext.assertExprReturns(
            "([Store].[All Stores])",
            "192,025");
    }

    /**
     * Access [Store].[All Stores] implicitly as it is the default member
     * of the [Stores] hierarchy.
     */
    public void testRollupPolicyAllAsDefault() {
        rollupTestContext.assertExprReturns(
            "([Store])",
            "192,025");
    }

    /**
     * Access [Store].[All Stores] via the Parent relationship (to check
     * that this doesn't circumvent access control).
     */
    public void testRollupPolicyAllAsParent() {
        rollupTestContext.assertExprReturns(
            "([Store].[USA].Parent)",
            "192,025");
    }

    /**
     * Tests that members below bottom level are regarded as visible.
     */
    public void testRollupBottomLevel() {
        rollupPolicyBottom(
            Role.RollupPolicy.FULL, "74,748", "36,759", "266,773");
        rollupPolicyBottom(
            Role.RollupPolicy.PARTIAL, "72,739", "35,775", "264,764");
        rollupPolicyBottom(Role.RollupPolicy.HIDDEN, "", "", "");
    }

    private void rollupPolicyBottom(
        Role.RollupPolicy rollupPolicy,
        String v1,
        String v2,
        String v3)
    {
        TestContext testContext  =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\""
                    + rollupPolicy
                    + "\" bottomLevel=\"[Customers].[City]\">\n"
                    + "        <MemberGrant member=\"[Customers].[USA]\" access=\"all\"/>\n"
                    + "        <MemberGrant member=\"[Customers].[USA].[CA].[Los Angeles]\" access=\"none\"/>\n"
                    + "      </HierarchyGrant>\n"
                    + "    </CubeGrant>\n"
                    + "  </SchemaGrant>\n"
                    + "</Role>")
                .withRole("Role1");
        // All of the children of [San Francisco] are invisible, because [City]
        // is the bottom level, but that shouldn't affect the total.
        testContext.assertExprReturns(
            "([Customers].[USA].[CA].[San Francisco])", "88");
        testContext.assertExprThrows(
            "([Customers].[USA].[CA].[Los Angeles])",
            "MDX object '[Customers].[USA].[CA].[Los Angeles]' not found in cube 'Sales'");

        testContext.assertExprReturns("([Customers].[USA].[CA])", v1);
        testContext.assertExprReturns(
            "([Customers].[USA].[CA], [Gender].[F])", v2);
        testContext.assertExprReturns("([Customers].[USA])", v3);

        checkQuery(
            testContext,
            "select [Customers].Children on 0, "
            + "[Gender].Members on 1 from [Sales]");
    }

    /**
     * Calls various {@link SchemaReader} methods on the members returned in
     * a result set.
     *
     * @param testContext Test context
     * @param mdx MDX query
     */
    private void checkQuery(TestContext testContext, String mdx) {
        Result result = testContext.executeQuery(mdx);
        final SchemaReader schemaReader =
            testContext.getConnection().getSchemaReader();
        for (Axis axis : result.getAxes()) {
            for (Position position : axis.getPositions()) {
                for (Member member : position) {
                    final Member accessControlledParent =
                        schemaReader.getMemberParent(member);
                    if (member.getParentMember() == null) {
                        assertNull(accessControlledParent);
                    }
                    final List<Member> accessControlledChildren =
                        schemaReader.getMemberChildren(member);
                    assertNotNull(accessControlledChildren);
                }
            }
        }
    }

    /**
     * Tests that a bad value for the rollupPolicy attribute gives the
     * appropriate error.
     */
    public void testRollupPolicyNegative() {
        TestContext testContext  =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\"bad\" bottomLevel=\"[Customers].[City]\">\n"
                + "        <MemberGrant member=\"[Customers].[USA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[CA].[Los Angeles]\" access=\"none\"/>\n"
                + "      </HierarchyGrant>\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>")
                .withRole("Role1");
        testContext.assertQueryThrows(
            "select from [Sales]",
            "Illegal rollupPolicy value 'bad'");
    }

    /**
     * Tests where all children are visible but a grandchild is not.
     */
    public void testRollupPolicyGreatGrandchildInvisible() {
        rollupPolicyGreatGrandchildInvisible(
            Role.RollupPolicy.FULL, "266,773", "74,748");
        rollupPolicyGreatGrandchildInvisible(
            Role.RollupPolicy.PARTIAL, "266,767", "74,742");
        rollupPolicyGreatGrandchildInvisible(
            Role.RollupPolicy.HIDDEN, "", "");
    }

    private void rollupPolicyGreatGrandchildInvisible(
        Role.RollupPolicy policy,
        String v1,
        String v2)
    {
        TestContext testContext  =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\""
                + policy
                + "\">\n"
                + "        <MemberGrant member=\"[Customers].[USA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[CA].[San Francisco].[Gladys Evans]\" access=\"none\"/>\n"
                + "      </HierarchyGrant>\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>")
                .withRole("Role1");
        testContext.assertExprReturns("[Measures].[Unit Sales]", v1);
        testContext.assertExprReturns(
            "([Measures].[Unit Sales], [Customers].[USA])",
            v1);
        testContext.assertExprReturns(
            "([Measures].[Unit Sales], [Customers].[USA].[CA])",
            v2);
    }

    /**
     * Tests where two hierarchies are simultaneously access-controlled.
     */
    public void testRollupPolicySimultaneous() {
        // note that v2 is different for full vs partial, v3 is the same
        rollupPolicySimultaneous(
            Role.RollupPolicy.FULL, "266,773", "74,748", "25,635");
        rollupPolicySimultaneous(
            Role.RollupPolicy.PARTIAL, "72,631", "72,631", "25,635");
        rollupPolicySimultaneous(
            Role.RollupPolicy.HIDDEN, "", "", "");
    }

    private void rollupPolicySimultaneous(
        Role.RollupPolicy policy,
        String v1,
        String v2,
        String v3)
    {
        TestContext testContext  =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\""
                + policy
                + "\">\n"
                + "        <MemberGrant member=\"[Customers].[USA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[CA].[San Francisco].[Gladys Evans]\" access=\"none\"/>\n"
                + "      </HierarchyGrant>\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\""
                + policy
                + "\">\n"
                + "        <MemberGrant member=\"[Store].[USA].[CA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Store].[USA].[CA].[San Francisco].[Store 14]\" access=\"none\"/>\n"
                + "      </HierarchyGrant>\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>")
                .withRole("Role1");
        testContext.assertExprReturns("[Measures].[Unit Sales]", v1);
        testContext.assertExprReturns(
            "([Measures].[Unit Sales], [Customers].[USA])",
            v1);
        testContext.assertExprReturns(
            "([Measures].[Unit Sales], [Customers].[USA].[CA])",
            v2);
        testContext.assertExprReturns(
            "([Measures].[Unit Sales], "
            + "[Customers].[USA].[CA], [Store].[USA].[CA])",
            v2);
        testContext.assertExprReturns(
            "([Measures].[Unit Sales], "
            + "[Customers].[USA].[CA], "
            + "[Store].[USA].[CA].[San Diego])",
            v3);
    }

    // todo: performance test where 1 of 1000 children is not visible

    public void testUnionRole() {
        TestContext testContext  =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\"Partial\">\n"
                + "        <MemberGrant member=\"[Customers].[USA].[CA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[CA].[San Francisco].[Gladys Evans]\" access=\"none\"/>\n"
                + "      </HierarchyGrant>\n"
                + "      <HierarchyGrant hierarchy=\"[Promotion Media]\" access=\"all\"/>\n"
                + "      <HierarchyGrant hierarchy=\"[Marital Status]\" access=\"none\"/>\n"
                + "      <HierarchyGrant hierarchy=\"[Gender]\" access=\"none\"/>\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"Partial\" topLevel=\"[Store].[Store State]\"/>\n"
                + "    </CubeGrant>\n"
                + "    <CubeGrant cube=\"Warehouse\" access=\"all\"/>\n"
                + "  </SchemaGrant>\n"
                + "</Role>\n"
                + "<Role name=\"Role2\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"none\">\n"
                + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\" rollupPolicy=\"Hidden\">\n"
                + "        <MemberGrant member=\"[Customers].[USA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[CA]\" access=\"none\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[OR]\" access=\"none\"/>\n"
                + "        <MemberGrant member=\"[Customers].[USA].[OR].[Portland]\" access=\"all\"/>\n"
                + "      </HierarchyGrant>\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"all\" rollupPolicy=\"Hidden\"/>\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>\n");

        Connection connection;

        try {
            connection = testContext.withRole("Role3,Role2").getConnection();
            fail("expected exception, got " + connection);
        } catch (RuntimeException e) {
            final String message = e.getMessage();
            assertTrue(message, message.indexOf("Role 'Role3' not found") >= 0);
        }

        try {
            connection = testContext.withRole("Role1,Role3").getConnection();
            fail("expected exception, got " + connection);
        } catch (RuntimeException e) {
            final String message = e.getMessage();
            assertTrue(message, message.indexOf("Role 'Role3' not found") >= 0);
        }

        connection = testContext.withRole("Role1,Role2").getConnection();

        // Cube access:
        // Both can see [Sales]
        // Role1 only see [Warehouse]
        // Neither can see [Warehouse and Sales]
        assertCubeAccess(connection, Access.ALL, "Sales");
        assertCubeAccess(connection, Access.ALL, "Warehouse");
        assertCubeAccess(connection, Access.NONE, "Warehouse and Sales");

        // Hierarchy access:
        // Both can see [Customers] with Custom access
        // Both can see [Store], Role1 with Custom access, Role2 with All access
        // Role1 can see [Promotion Media], Role2 cannot
        // Neither can see [Marital Status]
        assertHierarchyAccess(
            connection, Access.CUSTOM, "Sales", "[Customers]");
        assertHierarchyAccess(
            connection, Access.ALL, "Sales", "[Store]");
        assertHierarchyAccess(
            connection, Access.ALL, "Sales", "[Promotion Media]");
        assertHierarchyAccess(
            connection, Access.NONE, "Sales", "[Marital Status]");

        // Rollup policy is the greater of Role1's partian and Role2's hidden
        final Role.HierarchyAccess hierarchyAccess =
            getHierarchyAccess(connection, "Sales", "[Store]");
        assertEquals(
            Role.RollupPolicy.PARTIAL,
            hierarchyAccess.getRollupPolicy());
        assertEquals(0, hierarchyAccess.getTopLevelDepth());
        assertEquals(4, hierarchyAccess.getBottomLevelDepth());

        // Member access:
        // both can see [USA]
        assertMemberAccess(connection, Access.ALL, "[Customers].[USA]");
        // Role1 can see [CA], Role2 cannot
        assertMemberAccess(connection, Access.ALL, "[Customers].[USA].[CA]");
        // Role1 cannoy see [USA].[OR].[Portland], Role2 can
        assertMemberAccess(
            connection, Access.ALL, "[Customers].[USA].[OR].[Portland]");
        // Role1 cannot see [USA].[OR], Role2 can see it by virtue of [Portland]
        assertMemberAccess(
            connection, Access.CUSTOM, "[Customers].[USA].[OR]");
        // Neither can see Beaverton
        assertMemberAccess(
            connection, Access.NONE, "[Customers].[USA].[OR].[Beaverton]");

        // Rollup policy
        String mdx = "select Hierarchize(\n"
            + "{[Customers].[USA].Children,\n"
            + " [Customers].[USA].[OR].Children}) on 0\n"
            + "from [Sales]";
        testContext.assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Customers].[USA].[CA]}\n"
            + "{[Customers].[USA].[OR]}\n"
            + "{[Customers].[USA].[OR].[Albany]}\n"
            + "{[Customers].[USA].[OR].[Beaverton]}\n"
            + "{[Customers].[USA].[OR].[Corvallis]}\n"
            + "{[Customers].[USA].[OR].[Lake Oswego]}\n"
            + "{[Customers].[USA].[OR].[Lebanon]}\n"
            + "{[Customers].[USA].[OR].[Milwaukie]}\n"
            + "{[Customers].[USA].[OR].[Oregon City]}\n"
            + "{[Customers].[USA].[OR].[Portland]}\n"
            + "{[Customers].[USA].[OR].[Salem]}\n"
            + "{[Customers].[USA].[OR].[W. Linn]}\n"
            + "{[Customers].[USA].[OR].[Woodburn]}\n"
            + "{[Customers].[USA].[WA]}\n"
            + "Row #0: 74,748\n"
            + "Row #0: 67,659\n"
            + "Row #0: 6,806\n"
            + "Row #0: 4,558\n"
            + "Row #0: 9,539\n"
            + "Row #0: 4,910\n"
            + "Row #0: 9,596\n"
            + "Row #0: 5,145\n"
            + "Row #0: 3,708\n"
            + "Row #0: 3,583\n"
            + "Row #0: 7,678\n"
            + "Row #0: 4,175\n"
            + "Row #0: 7,961\n"
            + "Row #0: 124,366\n");

        testContext.withRole("Role1").assertQueryThrows(
            mdx,
            "MDX object '[Customers].[USA].[OR]' not found in cube 'Sales'");

        testContext.withRole("Role2").assertQueryThrows(
            mdx,
            "MDX cube 'Sales' not found");

        // Compared to above:
        // a. cities in Oregon are missing besides Portland
        // b. total for Oregon = total for Portland
        testContext.withRole("Role1,Role2").assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Customers].[USA].[CA]}\n"
            + "{[Customers].[USA].[OR]}\n"
            + "{[Customers].[USA].[OR].[Portland]}\n"
            + "{[Customers].[USA].[WA]}\n"
            + "Row #0: 74,742\n"
            + "Row #0: 3,583\n"
            + "Row #0: 3,583\n"
            + "Row #0: 124,366\n");
        checkQuery(testContext.withRole("Role1,Role2"), mdx);
    }

    /**
     * Test to verify that non empty crossjoins enforce role access.
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-369">
     * MONDRIAN-369, "Non Empty Crossjoin fails to enforce role access".
     */
    public void testNonEmptyAccess() {
        TestContext testContext =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Product]\" access=\"custom\">\n"
                + "        <MemberGrant member=\"[Product].[Drink]\" access=\"all\"/>\n"
                + "      </HierarchyGrant>\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>")
                .withRole("Role1");

        // regular crossjoin returns the correct list of product children
        final String expected =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[All Gender], [Product].[Drink]}\n"
            + "Row #0: 24,597\n";

        final String mdx =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + " Crossjoin({[Gender].[All Gender]}, "
            + "[Product].Children) ON ROWS "
            + "from [Sales]";
        testContext.assertQueryReturns(mdx, expected);
        checkQuery(testContext, mdx);

        // with bug MONDRIAN-397, non empty crossjoin did not return the correct
        // list
        final String mdx2 =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + "NON EMPTY Crossjoin({[Gender].[All Gender]}, "
            + "[Product].[All Products].Children) ON ROWS "
            + "from [Sales]";
        testContext.assertQueryReturns(mdx2, expected);
        checkQuery(testContext, mdx2);
    }

    public void testNonEmptyAccessLevelMembers() {
        TestContext testContext = TestContext.create(
            null,
            null,
            null,
            null,
            null,
            "<Role name=\"Role1\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Product]\" access=\"custom\">\n"
            + "        <MemberGrant member=\"[Product].[Drink]\" access=\"all\"/>\n"
            + "      </HierarchyGrant>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>").withRole("Role1");

        // <Level>.members inside regular crossjoin returns the correct list of
        // product members
        final String expected =
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Gender].[All Gender], [Product].[Drink]}\n"
            + "Row #0: 24,597\n";

        final String mdx =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + " Crossjoin({[Gender].[All Gender]}, "
            + "[Product].[Product Family].Members) ON ROWS "
            + "from [Sales]";
        testContext.assertQueryReturns(mdx, expected);
        checkQuery(testContext, mdx);

        // with bug MONDRIAN-397, <Level>.members inside non empty crossjoin did
        // not return the correct list
        final String mdx2 =
            "select {[Measures].[Unit Sales]} ON COLUMNS, "
            + "NON EMPTY Crossjoin({[Gender].[All Gender]}, "
            + "[Product].[Product Family].Members) ON ROWS "
            + "from [Sales]";
        testContext.assertQueryReturns(mdx2, expected);
        checkQuery(testContext, mdx2);
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-406">
     * MONDRIAN-406, "Rollup policy doesn't work for members
     * that are implicitly visible"</a>.
     */
    public void testGoodman() {
        final String query = "select {[Measures].[Unit Sales]} ON COLUMNS,\n"
            + "Hierarchize(Union(Union(Union({[Store].[All Stores]},"
            + " [Store].[All Stores].Children),"
            + " [Store].[All Stores].[USA].Children),"
            + " [Store].[All Stores].[USA].[CA].Children)) ON ROWS\n"
            + "from [Sales]\n"
            + "where [Time].[1997]";

        // Note that total for [Store].[All Stores] and [Store].[USA] is sum
        // of visible children [Store].[CA] and [Store].[OR].[Portland].
        final TestContext testContext =
            goodmanContext(Role.RollupPolicy.PARTIAL);
        testContext.assertQueryReturns(
            query,
            "Axis #0:\n"
            + "{[Time].[1997]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[All Stores]}\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[CA]}\n"
            + "{[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[USA].[OR]}\n"
            + "Row #0: 100,827\n"
            + "Row #1: 100,827\n"
            + "Row #2: 74,748\n"
            + "Row #3: \n"
            + "Row #4: 21,333\n"
            + "Row #5: 25,663\n"
            + "Row #6: 25,635\n"
            + "Row #7: 2,117\n"
            + "Row #8: 26,079\n");

        goodmanContext(Role.RollupPolicy.FULL).assertQueryReturns(
            query,
            "Axis #0:\n"
            + "{[Time].[1997]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[All Stores]}\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[CA]}\n"
            + "{[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[USA].[OR]}\n"
            + "Row #0: 266,773\n"
            + "Row #1: 266,773\n"
            + "Row #2: 74,748\n"
            + "Row #3: \n"
            + "Row #4: 21,333\n"
            + "Row #5: 25,663\n"
            + "Row #6: 25,635\n"
            + "Row #7: 2,117\n"
            + "Row #8: 67,659\n");

        goodmanContext(Role.RollupPolicy.HIDDEN).assertQueryReturns(
            query,
            "Axis #0:\n"
            + "{[Time].[1997]}\n"
            + "Axis #1:\n"
            + "{[Measures].[Unit Sales]}\n"
            + "Axis #2:\n"
            + "{[Store].[All Stores]}\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[CA]}\n"
            + "{[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[USA].[CA].[Los Angeles]}\n"
            + "{[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[USA].[OR]}\n"
            + "Row #0: \n"
            + "Row #1: \n"
            + "Row #2: 74,748\n"
            + "Row #3: \n"
            + "Row #4: 21,333\n"
            + "Row #5: 25,663\n"
            + "Row #6: 25,635\n"
            + "Row #7: 2,117\n"
            + "Row #8: \n");
        checkQuery(testContext, query);
    }

    private static TestContext goodmanContext(final Role.RollupPolicy policy) {
        return
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"California manager\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" rollupPolicy=\""
                + policy.name().toLowerCase()
                + "\" access=\"custom\">\n"
                + "        <MemberGrant member=\"[Store].[USA].[CA]\" access=\"all\"/>\n"
                + "        <MemberGrant member=\"[Store].[USA].[OR].[Portland]\" access=\"all\"/>\n"
                + "      </HierarchyGrant>"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>")
                .withRole("California manager");
    }

    /**
     * Test case for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-402">
     * MONDRIAN-402, "Bug in RolapCubeHierarchy.hashCode() ?"</a>.
     * Access-control elements for hierarchies with
     * same name in different cubes could not be distinguished.
     */
    public void testBugMondrian402() {
        final TestContext testContext =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"California manager\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"none\" />\n"
                + "    </CubeGrant>\n"
                + "    <CubeGrant cube=\"Sales Ragged\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" />\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>")
                .withRole("California manager");
        assertHierarchyAccess(
            testContext.getConnection(), Access.NONE, "Sales", "Store");
        assertHierarchyAccess(
            testContext.getConnection(),
            Access.CUSTOM,
            "Sales Ragged",
            "Store");
    }

    public void testPartialRollupParentChildHierarchy() {
        final TestContext testContext = TestContext.create(
            null, null, null, null, null,
            "<Role name=\"Buggy Role\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"HR\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Employees]\" access=\"custom\"\n"
            + "                      rollupPolicy=\"partial\">\n"
            + "        <MemberGrant\n"
            + "            member=\"[Employees].[All Employees].[Sheri Nowmer].[Darren Stanz]\"\n"
            + "            access=\"all\"/>\n"
            + "      </HierarchyGrant>\n"
            + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\"\n"
            + "                      rollupPolicy=\"partial\">\n"
            + "        <MemberGrant member=\"[Store].[All Stores].[USA].[CA]\" access=\"all\"/>\n"
            + "      </HierarchyGrant>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>")
            .withRole("Buggy Role");

        final String mdx = "select\n"
            + "  {[Measures].[Number of Employees]} on columns,\n"
            + "  {[Store]} on rows\n"
            + "from HR";
        testContext.assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Number of Employees]}\n"
            + "Axis #2:\n"
            + "{[Store].[All Stores]}\n"
            + "Row #0: 1\n");
        checkQuery(testContext, mdx);

        final String mdx2 = "select\n"
            + "  {[Measures].[Number of Employees]} on columns,\n"
            + "  {[Employees]} on rows\n"
            + "from HR";
        testContext.assertQueryReturns(
            mdx2,
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Number of Employees]}\n"
            + "Axis #2:\n"
            + "{[Employees].[All Employees]}\n"
            + "Row #0: 1\n");
        checkQuery(testContext, mdx2);
    }

    /**
     * Test case for
     * <a href="http://jira.pentaho.com/browse/BISERVER-1574">BISERVER-1574,
     * "Cube role rollupPolicy='partial' failure"</a>. The problem was a
     * NullPointerException in
     * {@link SchemaReader#getMemberParent(mondrian.olap.Member)} when called
     * on a members returned in a result set. JPivot calls that method but
     * Mondrian normally does not.
     */
    public void testBugBiserver1574() {
        final TestContext testContext =
            TestContext.create(
                null, null, null, null, null, BiServer1574Role1)
                .withRole("role1");
        final String mdx =
            "select {([Measures].[Store Invoice], [Store Size in SQFT].[All Store Size in SQFTs])} ON COLUMNS,\n"
            + "  {[Warehouse].[All Warehouses]} ON ROWS\n"
            + "from [Warehouse]";
        checkQuery(testContext, mdx);
        testContext.assertQueryReturns(
            mdx,
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Store Invoice], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "Axis #2:\n"
            + "{[Warehouse].[All Warehouses]}\n"
            + "Row #0: 4,042.96\n");
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-435">
     * MONDRIAN-435, "Internal error in HierarchizeArrayComparator"</a>. Occurs
     * when apply Hierarchize function to tuples on a hierarchy with
     * partial-rollup.
     */
    public void testBugMondrian435() {
        final TestContext testContext =
            TestContext.create(
                null, null, null, null, null, BiServer1574Role1)
                .withRole("role1");

        // minimal testcase
        testContext.assertQueryReturns(
            "select hierarchize("
            + "    crossjoin({[Store Size in SQFT], [Store Size in SQFT].Children}, {[Product]})"
            + ") on 0,"
            + "[Store Type].Members on 1 from [Warehouse]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[All Products]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[All Products]}\n"
            + "Axis #2:\n"
            + "{[Store Type].[All Store Types]}\n"
            + "{[Store Type].[Supermarket]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 4,042.96\n");

        // explicit tuples, not crossjoin
        testContext.assertQueryReturns(
            "select hierarchize("
            + "    { ([Store Size in SQFT], [Product]),\n"
            + "      ([Store Size in SQFT].[20319], [Product].[Food]),\n"
            + "      ([Store Size in SQFT], [Product].[Drink].[Dairy]),\n"
            + "      ([Store Size in SQFT].[20319], [Product]) }\n"
            + ") on 0,"
            + "[Store Type].Members on 1 from [Warehouse]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[All Products]}\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Drink].[Dairy]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[All Products]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[Food]}\n"
            + "Axis #2:\n"
            + "{[Store Type].[All Store Types]}\n"
            + "{[Store Type].[Supermarket]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 82.454\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 2,696.758\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 82.454\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 2,696.758\n");

        // extended testcase; note that [Store Size in SQFT].Parent is null,
        // so disappears
        testContext.assertQueryReturns(
            "select non empty hierarchize("
            + "union("
            + "  union("
            + "    crossjoin({[Store Size in SQFT]}, {[Product]}),"
            + "    crossjoin({[Store Size in SQFT], [Store Size in SQFT].Children}, {[Product]}),"
            + "    all),"
            + "  union("
            + "    crossjoin({[Store Size in SQFT].Parent}, {[Product].[Drink]}),"
            + "    crossjoin({[Store Size in SQFT].Children}, {[Product].[Food]}),"
            + "    all),"
            + "  all)) on 0,"
            + "[Store Type].Members on 1 from [Warehouse]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[All Products]}\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[All Products]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[All Products]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[Food]}\n"
            + "Axis #2:\n"
            + "{[Store Type].[All Store Types]}\n"
            + "{[Store Type].[Supermarket]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 2,696.758\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 4,042.96\n"
            + "Row #1: 2,696.758\n");

        testContext.assertQueryReturns(
            "select Hierarchize(\n"
            + "  CrossJoin\n("
            + "    CrossJoin(\n"
            + "      {[Product].[All Products], "
            + "       [Product].[Food],\n"
            + "       [Product].[Food].[Eggs],\n"
            + "       [Product].[Drink].[Dairy]},\n"
            + "      [Store Type].MEMBERS),\n"
            + "    [Store Size in SQFT].MEMBERS),\n"
            + "  PRE) on 0\n"
            + "from [Warehouse]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Product].[All Products], [Store Type].[All Store Types], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[All Products], [Store Type].[All Store Types], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[All Products], [Store Type].[Supermarket], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[All Products], [Store Type].[Supermarket], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[Drink].[Dairy], [Store Type].[All Store Types], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Drink].[Dairy], [Store Type].[All Store Types], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[Drink].[Dairy], [Store Type].[Supermarket], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Drink].[Dairy], [Store Type].[Supermarket], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[Food], [Store Type].[All Store Types], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Food], [Store Type].[All Store Types], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[Food], [Store Type].[Supermarket], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Food], [Store Type].[Supermarket], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[Food].[Eggs], [Store Type].[All Store Types], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Food].[Eggs], [Store Type].[All Store Types], [Store Size in SQFT].[20319]}\n"
            + "{[Product].[Food].[Eggs], [Store Type].[Supermarket], [Store Size in SQFT].[All Store Size in SQFTs]}\n"
            + "{[Product].[Food].[Eggs], [Store Type].[Supermarket], [Store Size in SQFT].[20319]}\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 4,042.96\n"
            + "Row #0: 82.454\n"
            + "Row #0: 82.454\n"
            + "Row #0: 82.454\n"
            + "Row #0: 82.454\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: 2,696.758\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: \n");
    }

    /**
     * Testcase for bug <a href="http://jira.pentaho.com/browse/MONDRIAN-436">
     * MONDRIAN-436, "SubstitutingMemberReader.getMemberBuilder gives
     * UnsupportedOperationException"</a>.
     */
    public void testBugMondrian436() {
        propSaver.set(propSaver.properties.EnableNativeCrossJoin, true);
        propSaver.set(propSaver.properties.EnableNativeFilter, true);
        propSaver.set(propSaver.properties.EnableNativeNonEmpty, true);
        propSaver.set(propSaver.properties.EnableNativeTopCount, true);
        propSaver.set(propSaver.properties.ExpandNonNative, true);

        // Run with native enabled, then with whatever properties are set for
        // this test run.
        checkBugMondrian436();
        propSaver.reset();
        checkBugMondrian436();
    }

    private void checkBugMondrian436() {
        final TestContext testContext =
            TestContext.create(
                null, null, null, null, null, BiServer1574Role1)
                .withRole("role1");

        testContext.assertQueryReturns(
            "select non empty {[Measures].[Units Ordered],\n"
            + "            [Measures].[Units Shipped]} on 0,\n"
            + "non empty hierarchize(\n"
            + "    union(\n"
            + "        crossjoin(\n"
            + "            {[Store Size in SQFT]},\n"
            + "            {[Product].[Drink],\n"
            + "             [Product].[Food],\n"
            + "             [Product].[Drink].[Dairy]}),\n"
            + "        crossjoin(\n"
            + "            {[Store Size in SQFT].[20319]},\n"
            + "            {[Product].Children}))) on 1\n"
            + "from [Warehouse]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Units Ordered]}\n"
            + "{[Measures].[Units Shipped]}\n"
            + "Axis #2:\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Drink]}\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Drink].[Dairy]}\n"
            + "{[Store Size in SQFT].[All Store Size in SQFTs], [Product].[Food]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[Drink]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[Food]}\n"
            + "{[Store Size in SQFT].[20319], [Product].[Non-Consumable]}\n"
            + "Row #0: 865.0\n"
            + "Row #0: 767.0\n"
            + "Row #1: 195.0\n"
            + "Row #1: 182.0\n"
            + "Row #2: 6065.0\n"
            + "Row #2: 5723.0\n"
            + "Row #3: 865.0\n"
            + "Row #3: 767.0\n"
            + "Row #4: 6065.0\n"
            + "Row #4: 5723.0\n"
            + "Row #5: 2179.0\n"
            + "Row #5: 2025.0\n");
    }

    /**
     * Tests that hierarchy-level access control works on a virtual cube.
     * See bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-456">
     * MONDRIAN-456, "Roles and virtual cubes"</a>.
     */
    public void testVirtualCube() {
        TestContext testContext = TestContext.create(
            null, null, null, null, null,
            "<Role name=\"VCRole\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"Warehouse and Sales\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\"\n"
            + "          topLevel=\"[Store].[Store Country]\">\n"
            + "        <MemberGrant member=\"[Store].[USA].[CA]\" access=\"all\"/>\n"
            + "        <MemberGrant member=\"[Store].[USA].[CA].[Los Angeles]\" access=\"none\"/>\n"
            + "      </HierarchyGrant>\n"
            + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\"\n"
            + "          topLevel=\"[Customers].[State Province]\" bottomLevel=\"[Customers].[City]\">\n"
            + "        <MemberGrant member=\"[Customers].[USA].[CA]\" access=\"all\"/>\n"
            + "        <MemberGrant member=\"[Customers].[USA].[CA].[Los Angeles]\" access=\"none\"/>\n"
            + "      </HierarchyGrant>\n"
            + "      <HierarchyGrant hierarchy=\"[Gender]\" access=\"none\"/>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>").withRole("VCRole");
        testContext.assertQueryReturns(
            "select [Store].Members on 0 from [Warehouse and Sales]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Store].[USA]}\n"
            + "{[Store].[USA].[CA]}\n"
            + "{[Store].[USA].[CA].[Alameda]}\n"
            + "{[Store].[USA].[CA].[Alameda].[HQ]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills]}\n"
            + "{[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
            + "{[Store].[USA].[CA].[San Diego]}\n"
            + "{[Store].[USA].[CA].[San Diego].[Store 24]}\n"
            + "{[Store].[USA].[CA].[San Francisco]}\n"
            + "{[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
            + "Row #0: 159,167.84\n"
            + "Row #0: 159,167.84\n"
            + "Row #0: \n"
            + "Row #0: \n"
            + "Row #0: 45,750.24\n"
            + "Row #0: 45,750.24\n"
            + "Row #0: 54,431.14\n"
            + "Row #0: 54,431.14\n"
            + "Row #0: 4,441.18\n"
            + "Row #0: 4,441.18\n");
    }

    /**
     * this tests the fix for
     * http://jira.pentaho.com/browse/BISERVER-2491
     * rollupPolicy=partial and queries to upper members don't work
     */
    public void testBugBiserver2491() {
        final String BiServer2491Role2 =
            "<Role name=\"role2\">"
            + " <SchemaGrant access=\"none\">"
            + "  <CubeGrant cube=\"Sales\" access=\"all\">"
            + "   <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"partial\">"
            + "    <MemberGrant member=\"[Store].[USA].[CA]\" access=\"all\"/>"
            + "    <MemberGrant member=\"[Store].[USA].[CA].[Los Angeles]\" access=\"none\"/>"
            + "   </HierarchyGrant>"
            + "  </CubeGrant>"
            + " </SchemaGrant>"
            + "</Role>";

        final TestContext testContext =
            TestContext.create(
                null, null, null, null, null, BiServer2491Role2)
                .withRole("role2");

        final String firstBrokenMdx =
            "select [Measures].[Unit Sales] ON COLUMNS, {[Store].[Store Country].Members} ON ROWS from [Sales]";

        checkQuery(testContext, firstBrokenMdx);
        testContext.assertQueryReturns(
                firstBrokenMdx,
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[USA]}\n"
                + "Row #0: 49,085\n");

        final String secondBrokenMdx =
            "select [Measures].[Unit Sales] ON COLUMNS, "
            + "Descendants([Store],[Store].[Store Name]) ON ROWS from [Sales]";
        checkQuery(testContext, secondBrokenMdx);
        testContext.assertQueryReturns(
                secondBrokenMdx,
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Store].[USA].[CA].[Alameda].[HQ]}\n"
                + "{[Store].[USA].[CA].[Beverly Hills].[Store 6]}\n"
                + "{[Store].[USA].[CA].[San Diego].[Store 24]}\n"
                + "{[Store].[USA].[CA].[San Francisco].[Store 14]}\n"
                + "Row #0: \n"
                + "Row #1: 21,333\n"
                + "Row #2: 25,635\n"
                + "Row #3: 2,117\n");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-622">MONDRIAN-622,
     * "Poor performance with large union role"</a>.
     */
    public void testBugMondrian622() {
        StringBuilder buf = new StringBuilder();
        StringBuilder buf2 = new StringBuilder();
        final String cubeName = "Sales with multiple customers";
        final Result result = TestContext.instance().executeQuery(
            "select [Customers].[City].Members on 0 from [Sales]");
        for (Position position : result.getAxes()[0].getPositions()) {
            Member member = position.get(0);
            String name = member.getParentMember().getName()
                + "."
                + member.getName(); // e.g. "BC.Burnaby"
            // e.g. "[Customers].[State Province].[BC].[Burnaby]"
            String uniqueName =
                Util.replace(member.getUniqueName(), ".[All Customers]", "");
            // e.g. "[Customers2].[State Province].[BC].[Burnaby]"
            String uniqueName2 =
                Util.replace(uniqueName, "Customers", "Customers2");
            // e.g. "[Customers3].[State Province].[BC].[Burnaby]"
            String uniqueName3 =
                Util.replace(uniqueName, "Customers", "Customers3");
            buf.append(
                "  <Role name=\"" + name + "\"> \n"
                + "    <SchemaGrant access=\"none\"> \n"
                + "      <CubeGrant access=\"all\" cube=\"" + cubeName
                + "\"> \n"
                + "        <HierarchyGrant access=\"custom\" hierarchy=\"[Customers]\" rollupPolicy=\"partial\"> \n"
                + "          <MemberGrant access=\"all\" member=\""
                + uniqueName + "\"/> \n"
                + "        </HierarchyGrant> \n"
                + "        <HierarchyGrant access=\"custom\" hierarchy=\"[Customers2]\" rollupPolicy=\"partial\"> \n"
                + "          <MemberGrant access=\"all\" member=\""
                + uniqueName2 + "\"/> \n"
                + "        </HierarchyGrant> \n"
                + "        <HierarchyGrant access=\"custom\" hierarchy=\"[Customers3]\" rollupPolicy=\"partial\"> \n"
                + "          <MemberGrant access=\"all\" member=\""
                + uniqueName3 + "\"/> \n"
                + "        </HierarchyGrant> \n"
                + "      </CubeGrant> \n"
                + "    </SchemaGrant> \n"
                + "  </Role> \n");

            buf2.append("    <RoleUsage roleName=\"" + name + "\"/>\n");
        }
        TestContext testContext = TestContext.create(
            " <Dimension name=\"Customers\"> \n"
            + "    <Hierarchy hasAll=\"true\" primaryKey=\"customer_id\"> \n"
            + "      <Table name=\"customer\"/> \n"
            + "      <Level name=\"Country\" column=\"country\" uniqueMembers=\"true\"/> \n"
            + "      <Level name=\"State Province\" column=\"state_province\" uniqueMembers=\"true\"/> \n"
            + "      <Level name=\"City\" column=\"city\" uniqueMembers=\"false\"/> \n"
            + "      <Level name=\"Name\" column=\"customer_id\" type=\"Numeric\" uniqueMembers=\"true\"/> \n"
            + "    </Hierarchy> \n"
            + "  </Dimension> ",
            "  <Cube name=\"" + cubeName + "\"> \n"
            + "    <Table name=\"sales_fact_1997\"/> \n"
            + "    <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/> \n"
            + "    <DimensionUsage name=\"Product\" source=\"Product\" foreignKey=\"product_id\"/> \n"
            + "    <DimensionUsage name=\"Customers\" source=\"Customers\" foreignKey=\"customer_id\"/> \n"
            + "    <DimensionUsage name=\"Customers2\" source=\"Customers\" foreignKey=\"customer_id\"/> \n"
            + "    <DimensionUsage name=\"Customers3\" source=\"Customers\" foreignKey=\"customer_id\"/> \n"
            + "    <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\" formatString=\"Standard\"/> \n"
            + "  </Cube> \n",
            null, null, null,
            buf.toString()
            + "  <Role name=\"Test\"> \n"
            + "    <Union>\n"
            + buf2.toString()
            + "    </Union>\n"
            + "  </Role>\n");
        final long t0 = System.currentTimeMillis();
        final TestContext testContext1 = testContext.withRole("Test");
        testContext1.executeQuery("select from [" + cubeName + "]");
        final long t1 = System.currentTimeMillis();
//      System.out.println("Elapsed=" + (t1 - t0) + " millis");
//      System.out.println(
//          "RoleImpl.accessCount=" + RoleImpl.accessCallCount);
//      testContext1.executeQuery(
//          "select from [Sales with multiple customers]");
//      final long t2 = System.currentTimeMillis();
//      System.out.println("Elapsed=" + (t2 - t1) + " millis");
//      System.out.println("RoleImpl.accessCount=" + RoleImpl.accessCallCount);
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-694">MONDRIAN-694,
     * "Incorrect handling of child/parent relationship with hierarchy
     * grants"</a>.
     */
    public void testBugMondrian694() {
        final TestContext testContext =
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"REG1\"> \n"
                + "  <SchemaGrant access=\"none\"> \n"
                + "    <CubeGrant cube=\"HR\" access=\"all\"> \n"
                + "      <HierarchyGrant hierarchy=\"Employees\" access=\"custom\" rollupPolicy=\"partial\"> \n"
                + "        <MemberGrant member=\"[Employees].[All Employees]\" access=\"none\"/>\n"
                + "        <MemberGrant member=\"[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Cody Goldey].[Shanay Steelman].[Steven Betsekas]\" access=\"all\"/> \n"
                + "        <MemberGrant member=\"[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Cody Goldey].[Shanay Steelman].[Arvid Ziegler]\" access=\"all\"/> \n"
                + "        <MemberGrant member=\"[Employees].[Sheri Nowmer].[Derrick Whelply].[Laurie Borges].[Cody Goldey].[Shanay Steelman].[Ann Weyerhaeuser]\" access=\"all\"/> \n"
                + "      </HierarchyGrant> \n"
                + "    </CubeGrant> \n"
                + "  </SchemaGrant> \n"
                + "</Role>")
                .withRole("REG1");

        // With bug MONDRIAN-694 returns 874.80, should return 79.20.
        // Test case is minimal: doesn't happen without the Crossjoin, or
        // without the NON EMPTY, or with [Employees] as opposed to
        // [Employees].[All Employees], or with [Department].[All Departments].
        testContext.assertQueryReturns(
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS,\n"
            + "NON EMPTY Crossjoin({[Department].[14]}, {[Employees].[All Employees]}) ON ROWS\n"
            + "from [HR]",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Department].[14], [Employees].[All Employees]}\n"
            + "Row #0: $97.20\n");

        // This query gave the right answer, even with MONDRIAN-694.
        testContext.assertQueryReturns(
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, \n"
            + "NON EMPTY Hierarchize(Crossjoin({[Department].[14]}, {[Employees].[All Employees], [Employees].Children})) ON ROWS \n"
            + "from [HR] ",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Department].[14], [Employees].[All Employees]}\n"
            + "{[Department].[14], [Employees].[Sheri Nowmer]}\n"
            + "Row #0: $97.20\n"
            + "Row #1: $97.20\n");

        // Original test case, not quite minimal. With MONDRIAN-694, returns
        // $874.80 for [All Employees].
        testContext.assertQueryReturns(
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, \n"
            + "NON EMPTY Hierarchize(Union(Crossjoin({[Department].[All Departments].[14]}, {[Employees].[All Employees]}), Crossjoin({[Department].[All Departments].[14]}, [Employees].[All Employees].Children))) ON ROWS \n"
            + "from [HR]  ",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Department].[14], [Employees].[All Employees]}\n"
            + "{[Department].[14], [Employees].[Sheri Nowmer]}\n"
            + "Row #0: $97.20\n"
            + "Row #1: $97.20\n");

        testContext.assertQueryReturns(
            "select NON EMPTY {[Measures].[Org Salary]} ON COLUMNS, \n"
            + "NON EMPTY Crossjoin(Hierarchize(Union({[Employees].[All Employees]}, [Employees].[All Employees].Children)), {[Department].[14]}) ON ROWS \n"
            + "from [HR] ",
            "Axis #0:\n"
            + "{}\n"
            + "Axis #1:\n"
            + "{[Measures].[Org Salary]}\n"
            + "Axis #2:\n"
            + "{[Employees].[All Employees], [Department].[14]}\n"
            + "{[Employees].[Sheri Nowmer], [Department].[14]}\n"
            + "Row #0: $97.20\n"
            + "Row #1: $97.20\n");
    }


    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-722">MONDRIAN-722, "If
     * ignoreInvalidMembers=true, should ignore grants with invalid
     * members"</a>.
     */
    public void testBugMondrian722() {
        propSaver.set(MondrianProperties.instance().IgnoreInvalidMembers, true);
        TestContext.create(
            null, null, null, null, null,
            "<Role name=\"CTO\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\">\n"
            + "        <MemberGrant member=\"[Customers].[USA].[XX]\" access=\"none\"/>\n"
            + "        <MemberGrant member=\"[Customers].[USA].[XX].[Yyy Yyyyyyy]\" access=\"all\"/>\n"
            + "        <MemberGrant member=\"[Customers].[USA].[CA]\" access=\"none\"/>\n"
            + "        <MemberGrant member=\"[Customers].[USA].[CA].[Los Angeles]\" access=\"all\"/>\n"
            + "        <MemberGrant member=\"[Customers].[USA].[CA].[Zzz Zzzz]\" access=\"none\"/>\n"
            + "        <MemberGrant member=\"[Customers].[USA].[CA].[San Francisco]\" access=\"all\"/>\n"
            + "      </HierarchyGrant>\n"
            + "      <HierarchyGrant hierarchy=\"[Gender]\" access=\"none\"/>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>")
            .withRole("CTO")
            .assertQueryReturns(
                "select [Measures] on 0,\n"
                + " Hierarchize(\n"
                + "   {[Customers].[USA].Children,\n"
                + "    [Customers].[USA].[CA].Children}) on 1\n"
                + "from [Sales]",
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Unit Sales]}\n"
                + "Axis #2:\n"
                + "{[Customers].[USA].[CA]}\n"
                + "{[Customers].[USA].[CA].[Los Angeles]}\n"
                + "{[Customers].[USA].[CA].[San Francisco]}\n"
                + "Row #0: 74,748\n"
                + "Row #1: 2,009\n"
                + "Row #2: 88\n");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-742">MONDRIAN-742, "Role
     * security not applied correctly for the 'Top Level' restriction"</a>.
     */
    public void testBugMondrian742() {
        TestContext.create(
            null, null, null, null, null,
            "<Role name=\"Role1\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"HR\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"Partial\" topLevel=\"[Store].[Store State]\">\n"
            + "        <MemberGrant member=\"[Store].[All Stores]\" access=\"all\"/>\n"
            + "      </HierarchyGrant>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>\n")
            .withRole("Role1")
            .assertQueryReturns(
                "With \n"
                + "Member [Store].[*TOTAL_MEMBER_SEL~SUM] as 'Sum([Store].Members)', SOLVE_ORDER=-100 \n"
                + "Select \n"
                + "{[Measures].[Count]} on columns, \n"
                + "{[Store].[*TOTAL_MEMBER_SEL~SUM]} on rows \n"
                + "From [HR]",
                "Axis #0:\n"
                + "{}\n"
                + "Axis #1:\n"
                + "{[Measures].[Count]}\n"
                + "Axis #2:\n"
                + "{[Store].[*TOTAL_MEMBER_SEL~SUM]}\n"
                + "Row #0: 22,176\n");
    }

    /**
     * Tests that if a hierarchy has a top level visible, and there are no
     * members explicitly granted, then the hierarchy is empty.
     */
    public void testHierarchyWithNoVisibleMembers() {
        // Similar scenario to testBugMondrian742, but HierarchyGrant has no
        // MemberGrants, so no members are visible. It is a common mistake to
        // make, adding no MemberGrants if all you want to do is restrict the
        // topLevel of the hierarchy. Note that the solution is to add a
        // MemberGrant of the 'all' member.
        TestContext.create(
            null, null, null, null, null,
            "<Role name=\"Role1\">\n"
            + "  <SchemaGrant access=\"none\">\n"
            + "    <CubeGrant cube=\"HR\" access=\"all\">\n"
            + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"Partial\" topLevel=\"[Store].[Store State]\"/>\n"
            + "    </CubeGrant>\n"
            + "  </SchemaGrant>\n"
            + "</Role>\n")
            .withRole("Role1")
            .assertQueryThrows(
                "With \n"
                + "Member [Store].[*TOTAL_MEMBER_SEL~SUM] as 'Sum([Store].Members)', SOLVE_ORDER=-100 \n"
                + "Select \n"
                + "{[Measures].[Count]} on columns, \n"
                + "{[Store].[*TOTAL_MEMBER_SEL~SUM]} on rows \n"
                + "From [HR]",
                "Hierarchy '[Store]' has no accessible members.");
    }

    /**
     * Test case for bug
     * <a href="http://jira.pentaho.com/browse/MONDRIAN-746">MONDRIAN-746,
     * "Report returns stack trace when turning on subtotals on a hierarchy with
     * top level hidden"</a>.
     */
    public void testCalcMemberLevel() {
        checkCalcMemberLevel(getTestContext());
        checkCalcMemberLevel(
            TestContext.create(
                null, null, null, null, null,
                "<Role name=\"Role1\">\n"
                + "  <SchemaGrant access=\"none\">\n"
                + "    <CubeGrant cube=\"Sales\" access=\"all\">\n"
                + "      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\" rollupPolicy=\"Partial\" topLevel=\"[Store].[Store State]\">\n"
                + "        <MemberGrant member=\"[Store].[All Stores]\" access=\"all\"/>\n"
                + "      </HierarchyGrant>\n"
                + "    </CubeGrant>\n"
                + "  </SchemaGrant>\n"
                + "</Role>\n")
                .withRole("Role1"));
    }

    private void checkCalcMemberLevel(TestContext testContext) {
        Result result = testContext.executeQuery(
            "with member [Store].[USA].[CA].[Foo] as\n"
            + " 1\n"
            + "select {[Measures].[Unit Sales]} on columns,\n"
            + "{[Store].[USA].[CA],\n"
            + " [Store].[USA].[CA].[Los Angeles],\n"
            + " [Store].[USA].[CA].[Foo]} on rows\n"
            + "from [Sales]");
        final List<Position> rowPos = result.getAxes()[1].getPositions();
        final Member member0 = rowPos.get(0).get(0);
        assertEquals("CA", member0.getName());
        assertEquals("Store State", member0.getLevel().getName());
        final Member member1 = rowPos.get(1).get(0);
        assertEquals("Los Angeles", member1.getName());
        assertEquals("Store City", member1.getLevel().getName());
        final Member member2 = rowPos.get(2).get(0);
        assertEquals("Foo", member2.getName());
        assertEquals("Store City", member2.getLevel().getName());
    }
}

// End AccessControlTest.java
