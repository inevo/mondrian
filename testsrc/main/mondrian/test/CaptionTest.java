package mondrian.test;

import java.net.URL;

import junit.framework.Assert;
import junit.framework.TestCase;
import mondrian.olap.Axis;
import mondrian.olap.Connection;
import mondrian.olap.Member;
import mondrian.olap.Position;

import mondrian.rolap.DynamicSchemaProcessor;

/**
 * test special "caption" settings
 */
public class CaptionTest extends TestCase {
 
    /**
     * set caption "Anzahl Verkauf" for measure "Unit Sales"
     */
	public void testMeasureCaption() {
	    TestContext tc = TestContext.instance();
	    Connection monConnection = tc.getFoodMartConnection("mondrian.test.CaptionTest$MyFoodmart");
        String mdxQuery = "SELECT {[Measures].[Unit Sales]} ON COLUMNS, {[Time].[1997].[Q1]} ON ROWS FROM [Sales]";
		mondrian.olap.Query monQuery = monConnection.parseQuery(mdxQuery);
		mondrian.olap.Result monResult = monConnection.execute(monQuery);
		Axis[] axes = monResult.getAxes();
		Position[] positions = axes[0].positions;
		Member m0 = positions[0].members[0];
		String caption = m0.getCaption();
		Assert.assertEquals("Anzahl Verkauf", caption);
	}
	
	/**
	 * set caption "Werbemedium" for nonshared dimension "Promotion Media"
	 */
	public void testDimCaption() {
	    TestContext tc = TestContext.instance();
	    Connection monConnection = tc.getFoodMartConnection("mondrian.test.CaptionTest$MyFoodmart");
        String mdxQuery = "SELECT {[Measures].[Unit Sales]} ON COLUMNS, {[Promotion Media].[All Media]} ON ROWS FROM [Sales]";
		mondrian.olap.Query monQuery = monConnection.parseQuery(mdxQuery);
		mondrian.olap.Result monResult = monConnection.execute(monQuery);
		Axis[] axes = monResult.getAxes();
		Position[] positions = axes[1].positions;
		Member mall = positions[0].members[0];
		
		String caption = mall.getHierarchy().getCaption();
		Assert.assertEquals("Werbemedium", caption);
	}
	
	/**
	 * set caption "Quadrat-Fuesse:-)" for shared dimension "Store Size in SQFT"
	 */
	public void testDimCaptionShared() {
	    TestContext tc = TestContext.instance();
		String mdxQuery = "SELECT {[Measures].[Unit Sales]} ON COLUMNS, {[Store Size in SQFT].[All Store Size in SQFTs]} ON ROWS FROM [Sales]";
        Connection monConnection = tc.getFoodMartConnection("mondrian.test.CaptionTest$MyFoodmart");
 		mondrian.olap.Query monQuery = monConnection.parseQuery(mdxQuery);
		mondrian.olap.Result monResult = monConnection.execute(monQuery);
		Axis[] axes = monResult.getAxes();
		Position[] positions = axes[1].positions;
		Member mall = positions[0].members[0];
		
		String caption = mall.getHierarchy().getCaption();
		Assert.assertEquals("Quadrat-Fuesse:-)", caption);
	}	


	/**
	 * created from foodmart.xml via perl script,
	 * some captions added.
	 */
	public static class MyFoodmart implements DynamicSchemaProcessor {
	    final static String foodmart =	
	"<?xml version=\"1.0\"?>" +
	"<Schema name=\"FoodMart\">" +
	"<!--" +
	"  == $Id$" +
	"  == This software is subject to the terms of the Common Public License" +
	"  == Agreement, available at the following URL:" +
	"  == http://www.opensource.org/licenses/cpl.html." +
	"  == (C) Copyright 2000-2005 Kana Software, Inc. and others." +
	"  == All Rights Reserved." +
	"  == You must accept the terms of that agreement to use this software." +
	"  -->" +
	"<!-- Shared dimensions -->" +
	"  <Dimension name=\"Store\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">" +
	"      <Table name=\"store\"/>" +
	"      <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"true\">" +
	"        <Property name=\"Store Type\" column=\"store_type\"/>" +
	"        <Property name=\"Store Manager\" column=\"store_manager\"/>" +
	"        <Property name=\"Store Sqft\" column=\"store_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Grocery Sqft\" column=\"grocery_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Frozen Sqft\" column=\"frozen_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Meat Sqft\" column=\"meat_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Has coffee bar\" column=\"coffee_bar\" type=\"Boolean\"/>" +
	"        <Property name=\"Street address\" column=\"store_street_address\" type=\"String\"/>" +
	"      </Level>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Store Size in SQFT\" caption=\"Quadrat-Fuesse:-)\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">" +
	"      <Table name=\"store\"/>" +
	"      <Level name=\"Store Sqft\" column=\"store_sqft\" type=\"Numeric\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Store Type\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">" +
	"      <Table name=\"store\"/>" +
	"      <Level name=\"Store Type\" column=\"store_type\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Time\" type=\"TimeDimension\">" +
	"    <Hierarchy hasAll=\"false\" primaryKey=\"time_id\">" +
	"      <Table name=\"time_by_day\"/>" +
	"      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"" +
	"          levelType=\"TimeYears\"/>" +
	"      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"" +
	"          levelType=\"TimeQuarters\"/>" +
	"      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\" type=\"Numeric\"" +
	"          levelType=\"TimeMonths\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Product\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"product_id\" primaryKeyTable=\"product\">" +
	"      <Join leftKey=\"product_class_id\" rightKey=\"product_class_id\">" +
	"        <Table name=\"product\"/>" +
	"        <Table name=\"product_class\"/>" +
	"      </Join>" +
	"<!--" +
	"      <Query>" +
	"        <SQL dialect=\"generic\">" +
	"SELECT *" +
	"FROM \"product\", \"product_class\"" +
	"WHERE \"product\".\"product_class_id\" = \"product_class\".\"product_class_id\"" +
	"        </SQL>" +
	"      </Query>" +
	"      <Level name=\"Product Family\" column=\"product_family\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"Product Department\" column=\"product_department\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Product Category\" column=\"product_category\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Product Subcategory\" column=\"product_subcategory\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Brand Name\" column=\"brand_name\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Product Name\" column=\"product_name\" uniqueMembers=\"true\"/>" +
	"-->" +
	"      <Level name=\"Product Family\" table=\"product_class\" column=\"product_family\"" +
	"          uniqueMembers=\"true\"/>" +
	"      <Level name=\"Product Department\" table=\"product_class\" column=\"product_department\"" +
	"          uniqueMembers=\"false\"/>" +
	"      <Level name=\"Product Category\" table=\"product_class\" column=\"product_category\"" +
	"          uniqueMembers=\"false\"/>" +
	"      <Level name=\"Product Subcategory\" table=\"product_class\" column=\"product_subcategory\"" +
	"          uniqueMembers=\"false\"/>" +
	"      <Level name=\"Brand Name\" table=\"product\" column=\"brand_name\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Product Name\" table=\"product\" column=\"product_name\"" +
	"          uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Warehouse\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"warehouse_id\">" +
	"      <Table name=\"warehouse\"/>" +
	"      <Level name=\"Country\" column=\"warehouse_country\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"State Province\" column=\"warehouse_state_province\"" +
	"          uniqueMembers=\"true\"/>" +
	"      <Level name=\"City\" column=\"warehouse_city\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Warehouse Name\" column=\"warehouse_name\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"<!-- Sales -->" +
	"<Cube name=\"Sales\">" +
	"  <Table name=\"sales_fact_1997\">" +
        "  <AggExclude pattern=\".*\" /> " +
	"  </Table>" +
	"  <DimensionUsage name=\"Store\" source=\"Store\" foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Store Size in SQFT\" source=\"Store Size in SQFT\"" +
	"      foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>" +
	"  <DimensionUsage name=\"Product\" source=\"Product\" foreignKey=\"product_id\"/>" +
	"  <Dimension name=\"Promotion Media\" caption=\"Werbemedium\" foreignKey=\"promotion_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Media\" primaryKey=\"promotion_id\">" +
	"      <Table name=\"promotion\"/>" +
	"      <Level name=\"Media Type\" column=\"media_type\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Promotions\" foreignKey=\"promotion_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Promotions\" primaryKey=\"promotion_id\">" +
	"      <Table name=\"promotion\"/>" +
	"      <Level name=\"Promotion Name\" column=\"promotion_name\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Customers\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Customers\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Country\" column=\"country\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"State Province\" column=\"state_province\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"City\" column=\"city\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Name\" uniqueMembers=\"true\">" +
	"        <KeyExpression>" +
	"          <SQL dialect=\"oracle\">" +
	"\"fname\" || ' ' || \"lname\"" +
	"          </SQL>" +
	"          <SQL dialect=\"access\">" +
	"fname + ' ' + lname" +
	"          </SQL>" +
	"          <SQL dialect=\"postgres\">" +
	"\"fname\" || ' ' || \"lname\"" +
	"          </SQL>" +
	"          <SQL dialect=\"mysql\">" +
	"CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)" +
	"          </SQL>" +
	"          <SQL dialect=\"mssql\">" +
	"fname + ' ' + lname" +
	"          </SQL>" +
	"          <SQL dialect=\"generic\">" +
	"lname" +
	"          </SQL>" +
	"        </KeyExpression>" +
	"        <Property name=\"Gender\" column=\"gender\"/>" +
	"        <Property name=\"Marital Status\" column=\"marital_status\"/>" +
	"        <Property name=\"Education\" column=\"education\"/>" +
	"        <Property name=\"Yearly Income\" column=\"yearly_income\"/>" +
	"      </Level>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Education Level\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Education Level\" column=\"education\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Gender\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Gender\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Gender\" column=\"gender\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Marital Status\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Marital Status\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Marital Status\" column=\"marital_status\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Yearly Income\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Yearly Income\" column=\"yearly_income\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Measure name=\"Unit Sales\" caption=\"Anzahl Verkauf\" column=\"unit_sales\" aggregator=\"sum\"" +
	"      formatString=\"Standard\"/>" +
	"  <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"" +
	"      formatString=\"#,###.00\"/>" +
	"  <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"" +
	"      formatString=\"#,###.00\"/>" +
	"  <Measure name=\"Sales Count\" column=\"product_id\" aggregator=\"count\"" +
	"      formatString=\"#,###\"/>" +
	"  <Measure name=\"Customer Count\" column=\"customer_id\"" +
	"      aggregator=\"distinct count\" formatString=\"#,###\"/>" +
	"  <CalculatedMember" +
	"      name=\"Profit\"" +
	"      dimension=\"Measures\"" +
	"      formula=\"[Measures].[Store Sales] - [Measures].[Store Cost]\">" +
	"    <CalculatedMemberProperty name=\"FORMAT_STRING\" value=\"$#,##0.00\"/>" +
	"  </CalculatedMember>" +
	"  <CalculatedMember" +
	"      name=\"Profit last Period\"" +
	"      dimension=\"Measures\"" +
	"      formula=\"COALESCEEMPTY((Measures.[Profit], [Time].PREVMEMBER),    Measures.[Profit])\"" +
	"      visible=\"false\"/>" +
	"  <CalculatedMember" +
	"      name=\"Profit Growth\"" +
	"      dimension=\"Measures\"" +
	"      formula=\"([Measures].[Profit] - [Measures].[Profit last Period]) / [Measures].[Profit last Period]\"" +
	"      visible=\"true\"" +
	"      caption=\"Gewinn-Wachstum\">" +
	"    <CalculatedMemberProperty name=\"FORMAT_STRING\" value=\"0.0%\"/>" +
	"  </CalculatedMember>" +
	"</Cube>" +
	"<Cube name=\"Warehouse\">" +
	"  <Table name=\"inventory_fact_1997\"/>" +
	"  <DimensionUsage name=\"Store\" source=\"Store\" foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Store Size in SQFT\" source=\"Store Size in SQFT\"" +
	"      foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>" +
	"  <DimensionUsage name=\"Product\" source=\"Product\" foreignKey=\"product_id\"/>" +
	"  <DimensionUsage name=\"Warehouse\" source=\"Warehouse\" foreignKey=\"warehouse_id\"/>" +
	"  <Measure name=\"Store Invoice\" column=\"store_invoice\" aggregator=\"sum\"/>" +
	"  <Measure name=\"Supply Time\" column=\"supply_time\" aggregator=\"sum\"/>" +
	"  <Measure name=\"Warehouse Cost\" column=\"warehouse_cost\" aggregator=\"sum\"/>" +
	"  <Measure name=\"Warehouse Sales\" column=\"warehouse_sales\" aggregator=\"sum\"/>" +
	"  <Measure name=\"Units Shipped\" column=\"units_shipped\" aggregator=\"sum\" formatString=\"#.0\"/>" +
	"  <Measure name=\"Units Ordered\" column=\"units_ordered\" aggregator=\"sum\" formatString=\"#.0\"/>" +
	"  <Measure name=\"Warehouse Profit\" column=\"&quot;warehouse_sales&quot;-&quot;inventory_fact_1997&quot;.&quot;warehouse_cost&quot;\" aggregator=\"sum\"/>" +
	"</Cube>" +
	"<!-- Test a cube based upon a single table. -->" +
	"<Cube name=\"Store\">" +
	"  <Table name=\"store\"/>" +
	"  <!-- We could have used the shared dimension \"Store Type\", but we" +
	"     want to test private dimensions without primary key. -->" +
	"  <Dimension name=\"Store Type\">" +
	"    <Hierarchy hasAll=\"true\">" +
	"      <Level name=\"Store Type\" column=\"store_type\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <!-- We don't have to specify primary key or foreign key since the shared" +
	"     dimension \"Store\" has the same underlying table as the cube. -->" +
	"  <DimensionUsage name=\"Store\" source=\"Store\"/>" +
	"  <Dimension name=\"Has coffee bar\">" +
	"    <Hierarchy hasAll=\"true\">" +
	"      <Level name=\"Has coffee bar\" column=\"coffee_bar\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Measure name=\"Store Sqft\" column=\"store_sqft\" aggregator=\"sum\"" +
	"      formatString=\"#,###\"/>" +
	"  <Measure name=\"Grocery Sqft\" column=\"grocery_sqft\" aggregator=\"sum\"" +
	"      formatString=\"#,###\"/>" +
	"</Cube>" +
	"<Cube name=\"HR\">" +
	"  <Table name=\"salary\"/>" +
	"  <!-- Use private \"Time\" dimension because key is different than public" +
	"     \"Time\" dimension. -->" +
	"  <Dimension name=\"Time\" type=\"TimeDimension\" foreignKey=\"pay_date\">" +
	"    <Hierarchy hasAll=\"false\" primaryKey=\"the_date\">" +
	"      <Table name=\"time_by_day\"/>" +
	"      <Level name=\"Year\" column=\"the_year\" type=\"Numeric\" uniqueMembers=\"true\"" +
	"          levelType=\"TimeYears\"/>" +
	"      <Level name=\"Quarter\" column=\"quarter\" uniqueMembers=\"false\"" +
	"          levelType=\"TimeQuarters\"/>" +
	"      <Level name=\"Month\" column=\"month_of_year\" uniqueMembers=\"false\"" +
	"          type=\"Numeric\" levelType=\"TimeMonths\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Store\" foreignKey=\"employee_id\" >" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"employee_id\"" +
	"        primaryKeyTable=\"employee\">" +
	"      <Join leftKey=\"store_id\" rightKey=\"store_id\">" +
	"        <Table name=\"employee\"/>" +
	"        <Table name=\"store\"/>" +
	"      </Join>" +
	"      <Level name=\"Store Country\" table=\"store\" column=\"store_country\"" +
	"          uniqueMembers=\"true\"/>" +
	"      <Level name=\"Store State\" table=\"store\" column=\"store_state\"" +
	"          uniqueMembers=\"true\"/>" +
	"      <Level name=\"Store City\" table=\"store\" column=\"store_city\"" +
	"          uniqueMembers=\"false\"/>" +
	"      <Level name=\"Store Name\" table=\"store\" column=\"store_name\"" +
	"          uniqueMembers=\"true\">" +
	"        <Property name=\"Store Type\" column=\"store_type\"/>" +
	"        <Property name=\"Store Manager\" column=\"store_manager\"/>" +
	"        <Property name=\"Store Sqft\" column=\"store_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Grocery Sqft\" column=\"grocery_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Frozen Sqft\" column=\"frozen_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Meat Sqft\" column=\"meat_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Has coffee bar\" column=\"coffee_bar\" type=\"Boolean\"/>" +
	"        <Property name=\"Street address\" column=\"store_street_address\"" +
	"            type=\"String\"/>" +
	"      </Level>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Pay Type\" foreignKey=\"employee_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"employee_id\"" +
	"        primaryKeyTable=\"employee\">" +
	"      <Join leftKey=\"position_id\" rightKey=\"position_id\">" +
	"        <Table name=\"employee\"/>" +
	"        <Table name=\"position\"/>" +
	"      </Join>" +
	"      <Level name=\"Pay Type\" table=\"position\" column=\"pay_type\"" +
	"          uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Store Type\" foreignKey=\"employee_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKeyTable=\"employee\" primaryKey=\"employee_id\">" +
	"      <Join leftKey=\"store_id\" rightKey=\"store_id\">" +
	"        <Table name=\"employee\"/>" +
	"        <Table name=\"store\"/>" +
	"      </Join>" +
	"      <Level name=\"Store Type\" table=\"store\" column=\"store_type\"" +
	"          uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Position\" foreignKey=\"employee_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Position\"" +
	"        primaryKey=\"employee_id\">" +
	"      <Table name=\"employee\"/>" +
	"      <Level name=\"Management Role\" uniqueMembers=\"true\"" +
	"          column=\"management_role\"/>" +
	"      <Level name=\"Position Title\" uniqueMembers=\"false\"" +
	"          column=\"position_title\" ordinalColumn=\"position_id\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Department\" foreignKey=\"department_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"department_id\">" +
	"      <Table name=\"department\"/>" +
	"      <Level name=\"Department Description\" uniqueMembers=\"true\"" +
	"          column=\"department_id\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Employees\" foreignKey=\"employee_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Employees\"" +
	"        primaryKey=\"employee_id\">" +
	"      <Table name=\"employee\"/>" +
	"      <Level name=\"Employee Id\" type=\"Numeric\" uniqueMembers=\"true\"" +
	"          column=\"employee_id\" parentColumn=\"supervisor_id\"" +
	"          nameColumn=\"full_name\" nullParentValue=\"0\">" +
	"        <Closure parentColumn=\"supervisor_id\" childColumn=\"employee_id\">" +
	"          <Table name=\"employee_closure\"/>" +
	"        </Closure>" +
	"        <Property name=\"Marital Status\" column=\"marital_status\"/>" +
	"        <Property name=\"Position Title\" column=\"position_title\"/>" +
	"        <Property name=\"Gender\" column=\"gender\"/>" +
	"        <Property name=\"Salary\" column=\"salary\"/>" +
	"        <Property name=\"Education Level\" column=\"education_level\"/>" +
	"        <Property name=\"Management Role\" column=\"management_role\"/>" +
	"      </Level>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <!-- Explicit Closure of [Employees] (just for unit testing):" +
	"    == [Employees] is a parent/child hierarchy (along the relationship" +
	"    == supervisor_id/employee_id). The table employee_closure expresses the" +
	"    == closure of the parent/child relation, ie it represents" +
	"    == ancestor/descendant, having a row for each ancestor/descendant pair." +
	"    ==" +
	"    == The closed hierarchy has two levels: the detail level (here named" +
	"    == [Employee]) is equivalent to the base hierarchy; the [Closure] level" +
	"    == relates each descendant to all its ancestors." +
	"  <Dimension name=\"EmployeesClosure\" foreignKey=\"employee_id\">" +
	"      <Hierarchy hasAll=\"true\" allMemberName=\"All Employees\"" +
	"          primaryKey=\"employee_id\" primaryKeyTable=\"employee_closure\">" +
	"        <Join leftKey=\"supervisor_id\" rightKey=\"employee_id\">" +
	"          <Table name=\"employee_closure\"/>" +
	"          <Table name=\"employee\"/>" +
	"        </Join>" +
	"        <Level name=\"Closure\"  type=\"Numeric\" uniqueMembers=\"false\"" +
	"            table=\"employee_closure\" column=\"supervisor_id\"/>" +
	"        <Level name=\"Employee\" type=\"Numeric\" uniqueMembers=\"true\"" +
	"            table=\"employee_closure\" column=\"employee_id\"/>" +
	"      </Hierarchy>" +
	"  </Dimension>" +
	"    -->" +
	"  <Measure name=\"Org Salary\" column=\"salary_paid\" aggregator=\"sum\"" +
	"      formatString=\"Currency\"/>" +
	"  <Measure name=\"Count\" column=\"employee_id\" aggregator=\"count\"" +
	"      formatString=\"#,#\"/>" +
	"  <Measure name=\"Number of Employees\" column=\"employee_id\"" +
	"      aggregator=\"distinct count\" formatString=\"#,#\"/>" +
	"  <CalculatedMember name=\"Employee Salary\" dimension=\"Measures\"" +
	"      formatString=\"Currency\"" +
	"      formula=\"([Employees].currentmember.datamember, [Measures].[Org Salary])\"/>" +
	"  <CalculatedMember name=\"Avg Salary\" dimension=\"Measures\"" +
	"      formatString=\"Currency\"" +
	"      formula=\"[Measures].[Org Salary]/[Measures].[Number of Employees]\"/>" +
	"</Cube>" +
	"<!-- Cube with one ragged hierarchy (otherwise the same as the \"Sales\"" +
	"   cube). -->" +
	"<Cube name=\"Sales Ragged\">" +
	"  <Table name=\"sales_fact_1997\"/>" +
	"  <Dimension name=\"Store\" foreignKey=\"store_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"store_id\">" +
	"      <Table name=\"store_ragged\"/>" +
	"      <Level name=\"Store Country\" column=\"store_country\" uniqueMembers=\"true\"" +
	"          hideMemberIf=\"Never\"/>" +
	"      <Level name=\"Store State\" column=\"store_state\" uniqueMembers=\"true\"" +
	"          hideMemberIf=\"IfParentsName\"/>" +
	"      <Level name=\"Store City\" column=\"store_city\" uniqueMembers=\"false\"" +
	"          hideMemberIf=\"IfBlankName\"/>" +
	"      <Level name=\"Store Name\" column=\"store_name\" uniqueMembers=\"true\"" +
	"          hideMemberIf=\"Never\">" +
	"        <Property name=\"Store Type\" column=\"store_type\"/>" +
	"        <Property name=\"Store Manager\" column=\"store_manager\"/>" +
	"        <Property name=\"Store Sqft\" column=\"store_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Grocery Sqft\" column=\"grocery_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Frozen Sqft\" column=\"frozen_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Meat Sqft\" column=\"meat_sqft\" type=\"Numeric\"/>" +
	"        <Property name=\"Has coffee bar\" column=\"coffee_bar\" type=\"Boolean\"/>" +
	"        <Property name=\"Street address\" column=\"store_street_address\" type=\"String\"/>" +
	"      </Level>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <DimensionUsage name=\"Store Size in SQFT\" source=\"Store Size in SQFT\"" +
	"      foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Store Type\" source=\"Store Type\" foreignKey=\"store_id\"/>" +
	"  <DimensionUsage name=\"Time\" source=\"Time\" foreignKey=\"time_id\"/>" +
	"  <DimensionUsage name=\"Product\" source=\"Product\" foreignKey=\"product_id\"/>" +
	"  <Dimension name=\"Promotion Media\" foreignKey=\"promotion_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Media\" primaryKey=\"promotion_id\">" +
	"      <Table name=\"promotion\"/>" +
	"      <Level name=\"Media Type\" column=\"media_type\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Promotions\" foreignKey=\"promotion_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Promotions\" primaryKey=\"promotion_id\">" +
	"      <Table name=\"promotion\"/>" +
	"      <Level name=\"Promotion Name\" column=\"promotion_name\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Customers\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Customers\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Country\" column=\"country\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"State Province\" column=\"state_province\" uniqueMembers=\"true\"/>" +
	"      <Level name=\"City\" column=\"city\" uniqueMembers=\"false\"/>" +
	"      <Level name=\"Name\" uniqueMembers=\"true\">" +
	"        <KeyExpression>" +
	"          <SQL dialect=\"oracle\">" +
	"\"fname\" || ' ' || \"lname\"" +
	"          </SQL>" +
	"          <SQL dialect=\"access\">" +
	"fname + ' ' + lname" +
	"          </SQL>" +
	"          <SQL dialect=\"postgres\">" +
	"\"fname\" || ' ' || \"lname\"" +
	"          </SQL>" +
	"          <SQL dialect=\"mysql\">" +
	"CONCAT(`customer`.`fname`, ' ', `customer`.`lname`)" +
	"          </SQL>" +
	"          <SQL dialect=\"mssql\">" +
	"fname + ' ' + lname" +
	"          </SQL>" +
	"          <SQL dialect=\"generic\">" +
	"\"lname\"" +
	"          </SQL>" +
	"        </KeyExpression>" +
	"        <Property name=\"Gender\" column=\"gender\"/>" +
	"        <Property name=\"Marital Status\" column=\"marital_status\"/>" +
	"        <Property name=\"Education\" column=\"education\"/>" +
	"        <Property name=\"Yearly Income\" column=\"yearly_income\"/>" +
	"      </Level>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Education Level\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Education Level\" column=\"education\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Gender\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Gender\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Gender\" column=\"gender\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Marital Status\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" allMemberName=\"All Marital Status\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Marital Status\" column=\"marital_status\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Dimension name=\"Yearly Income\" foreignKey=\"customer_id\">" +
	"    <Hierarchy hasAll=\"true\" primaryKey=\"customer_id\">" +
	"      <Table name=\"customer\"/>" +
	"      <Level name=\"Yearly Income\" column=\"yearly_income\" uniqueMembers=\"true\"/>" +
	"    </Hierarchy>" +
	"  </Dimension>" +
	"  <Measure name=\"Unit Sales\" column=\"unit_sales\" aggregator=\"sum\"" +
	"      formatString=\"Standard\"/>" +
	"  <Measure name=\"Store Cost\" column=\"store_cost\" aggregator=\"sum\"" +
	"      formatString=\"#,###.00\"/>" +
	"  <Measure name=\"Store Sales\" column=\"store_sales\" aggregator=\"sum\"" +
	"      formatString=\"#,###.00\"/>" +
	"  <Measure name=\"Sales Count\" column=\"product_id\" aggregator=\"count\"" +
	"      formatString=\"#,###\"/>" +
	"  <Measure name=\"Customer Count\" column=\"customer_id\" aggregator=\"distinct count\"" +
	"      formatString=\"#,###\"/>" +
	"</Cube>" +
	"<VirtualCube name=\"Warehouse and Sales\">" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Customers\"/>" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Education Level\"/>" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Gender\"/>" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Marital Status\"/>" +
	"  <VirtualCubeDimension name=\"Product\"/>" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Promotion Media\"/>" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Promotions\"/>" +
	"  <VirtualCubeDimension name=\"Store\"/>" +
	"  <VirtualCubeDimension name=\"Time\"/>" +
	"  <VirtualCubeDimension cubeName=\"Sales\" name=\"Yearly Income\"/>" +
	"  <VirtualCubeDimension cubeName=\"Warehouse\" name=\"Warehouse\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Sales\" name=\"[Measures].[Sales Count]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Sales\" name=\"[Measures].[Store Cost]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Sales\" name=\"[Measures].[Store Sales]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Sales\" name=\"[Measures].[Unit Sales]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Store Invoice]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Supply Time]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Units Ordered]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Units Shipped]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Warehouse Cost]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Warehouse Profit]\"/>" +
	"  <VirtualCubeMeasure cubeName=\"Warehouse\" name=\"[Measures].[Warehouse Sales]\"/>" +
	"  <!--" +
	"  <VirtualCubeMeasure cubeName=\"Sales\" name=\"[Measures].[Store Sales Net]\"/>" +
	"  -->" +
	"</VirtualCube>" +
	"<!-- A California manager can only see customers and stores in California." +
	"     They cannot drill down on Gender. -->" +
	"<Role name=\"California manager\">" +
	"  <SchemaGrant access=\"none\">" +
	"    <CubeGrant cube=\"Sales\" access=\"all\">" +
	"      <HierarchyGrant hierarchy=\"[Store]\" access=\"custom\"" +
	"          topLevel=\"[Store].[Store Country]\">" +
	"        <MemberGrant member=\"[Store].[USA].[CA]\" access=\"all\"/>" +
	"        <MemberGrant member=\"[Store].[USA].[CA].[Los Angeles]\" access=\"none\"/>" +
	"      </HierarchyGrant>" +
	"      <HierarchyGrant hierarchy=\"[Customers]\" access=\"custom\"" +
	"          topLevel=\"[Customers].[State Province]\" bottomLevel=\"[Customers].[City]\">" +
	"        <MemberGrant member=\"[Customers].[USA].[CA]\" access=\"all\"/>" +
	"        <MemberGrant member=\"[Customers].[USA].[CA].[Los Angeles]\" access=\"none\"/>" +
	"      </HierarchyGrant>" +
	"      <HierarchyGrant hierarchy=\"[Gender]\" access=\"none\"/>" +
	"    </CubeGrant>" +
	"  </SchemaGrant>" +
	"</Role>" +
	"" +
	"<Role name=\"No HR Cube\">" +
	"  <SchemaGrant access=\"all\">" +
	"    <CubeGrant cube=\"HR\" access=\"none\"/>" +
	"  </SchemaGrant>" +
	"</Role>" +
	"</Schema>" ;

	    public String processSchema(URL schemaUrl) throws Exception {
	        return foodmart;
	    }
	} // MyFoodmart
} // CaptionTest