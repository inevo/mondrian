/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2002-2007 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
*/

package mondrian.test;

import mondrian.olap.MondrianProperties;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;
import junit.framework.*;

import mondrian.test.clearview.*;
import mondrian.olap.Util;

/**
 * A copy of {@link ConcurrentMdxTest} with modifications to take
 * as input ref.xml files. This does not fully use {@link DiffRepository}
 * and does not generate log files.
 * This Class is not added to the Main test suite.
 * Purpose of this test is to simulate Concurrent access to Aggregation and data
 * load. Simulation will be more effective if we run this single test again and
 * again with a fresh connection.
 *
 * @author Khanh Vu
 * @version $Id$
 */
public class CVConcurrentMdxTest extends FoodMartTestCase {
    private MondrianProperties props;

    public CVConcurrentMdxTest() {
        super();
        props = MondrianProperties.instance();
    }

    public CVConcurrentMdxTest(String name) {
        super(name);
        props = MondrianProperties.instance();
    }

    public void testConcurrentQueriesInRandomOrder() {
        props.DisableCaching.set(false);
        props.UseAggregates.set(false);
        props.ReadAggregates.set(false);

        // test partially filled aggregation cache
        // add test classes
        List<Class> testList = new ArrayList<Class>();
        List<TestSuite> suiteList = new ArrayList<TestSuite>();

        testList.add(PartialCacheTest.class);
        suiteList.add(PartialCacheTest.suite());
        testList.add(MultiLevelTest.class);
        suiteList.add(MultiLevelTest.suite());
        testList.add(QueryAllTest.class);
        suiteList.add(QueryAllTest.suite());
        testList.add(MultiDimTest.class);
        suiteList.add(MultiDimTest.suite());
        
        // sanity check
        assertTrue(sanityCheck(suiteList));
        
        // generate list of queries and results
        QueryAndResult[] queryList = generateQueryArray(testList);
        
        assertTrue(ConcurrentValidatingQueryRunner.runTest(
            3, 100, true, true, true, queryList).size() == 0);
    }

    public void testConcurrentQueriesInRandomOrderOnVirtualCube() {
        props.DisableCaching.set(false);
        props.UseAggregates.set(false);
        props.ReadAggregates.set(false);

        // test partially filled aggregation cache
        // add test classes
        List<Class> testList = new ArrayList<Class>();
        List<TestSuite> suiteList = new ArrayList<TestSuite>();

        testList.add(PartialCacheVCTest.class);
        suiteList.add(PartialCacheVCTest.suite());
        testList.add(MultiLevelTest.class);
        suiteList.add(MultiLevelTest.suite());
        testList.add(QueryAllVCTest.class);
        suiteList.add(QueryAllVCTest.suite());
        testList.add(MultiDimVCTest.class);
        suiteList.add(MultiDimVCTest.suite());
        
        // sanity check
        assertTrue(sanityCheck(suiteList));
        
        // generate list of queries and results
        QueryAndResult[] queryList = generateQueryArray(testList);
        
        assertTrue(ConcurrentValidatingQueryRunner.runTest(
            3, 100, true, true, true, queryList).size() == 0);
    }
    
    public void testConcurrentCVQueriesInRandomOrder() {
        props.DisableCaching.set(false);
        props.UseAggregates.set(false);
        props.ReadAggregates.set(false);

        // test partially filled aggregation cache
        // add test classes
        List<Class> testList = new ArrayList<Class>();

        testList.add(CVBasicTest.class);
        testList.add(GrandTotalTest.class);
        testList.add(MetricFilterTest.class);
        testList.add(MiscTest.class);
        testList.add(PredicateFilterTest.class);
        testList.add(SubTotalTest.class);
        testList.add(SummaryMetricPercentTest.class);
        testList.add(SummaryTest.class);
        testList.add(TopBottomTest.class);

        // generate list of queries and results
        QueryAndResult[] queryList = generateQueryArray(testList);
        
        assertTrue(ConcurrentValidatingQueryRunner.runTest(
            3, 100, true, true, true, queryList).size() == 0);
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Runs one pass of all tests single-threaded using
     * {@link mondrian.test.clearview.ClearViewBase} mechanism
     * @param suiteList list of tests to be checked
     * @return true if all tests pass
     */
    private boolean sanityCheck(List<TestSuite> suiteList) {
        TestSuite suite = new TestSuite();
        
        for (int i = 0; i < suiteList.size(); i++) {
            suite.addTest(suiteList.get(i));
        }
        
        TestResult tres = new TestResult();
        suite.run(tres);
        
        return tres.wasSuccessful();
    }
    
    /**
     * Generates an array of QueryAndResult objects from the list of 
     * test classes
     * @param testList list of test classes
     * @return array of QueryAndResult
     */
    private QueryAndResult[] generateQueryArray(List<Class> testList) {
        List<QueryAndResult> queryList = new ArrayList<QueryAndResult>();
        for (int i = 0; i < testList.size(); i++) {
            Class testClass = testList.get(i);       
            Class[] types = new Class[] { String.class };
            try {
                Constructor cons = testClass.getConstructor(types);
                Object[] args = new Object[] { "" };
                Test newCon = (Test) cons.newInstance(args);
                DiffRepository diffRepos = 
                    ((ClearViewBase) newCon).getDiffRepos();
                
                List<String> testCaseNames = diffRepos.getTestCaseNames();
                for (int j = 0; j < testCaseNames.size(); j++) {
                    String testCaseName = testCaseNames.get(j);
                    String query = diffRepos.get(testCaseName, "mdx");
                    String result = diffRepos.get(testCaseName, "result");
                    
                    // current limitation: only run queries if
                    // calculated members are not specified
                    if (diffRepos.get(testCaseName, "calculatedMembers")
                        == null) {
                        // trim the starting newline char only
                        if (result.startsWith(Util.nl)) {
                            result = result.replaceFirst(Util.nl, "");
                        }
                        QueryAndResult queryResult = new QueryAndResult(
                            query,result);
                        queryList.add(queryResult);
                    }
                }
            } catch (Exception e) {
                throw new Error(e.getMessage());
            }
        }
        QueryAndResult[] queryArray = new QueryAndResult[queryList.size()];
        return queryList.toArray(queryArray);
    }
}

// End CVConcurrentMdxTest.java
