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

package mondrian.rolap;

import mondrian.calc.*;
import mondrian.calc.impl.ValueCalc;
import mondrian.calc.impl.GenericCalc;
import mondrian.olap.*;
import mondrian.olap.DimensionType;
import mondrian.olap.fun.*;
import mondrian.olap.type.*;
import mondrian.resource.MondrianResource;
import mondrian.rolap.agg.AggregationManager;
import mondrian.util.ConcatenableList;
import mondrian.util.Format;
import mondrian.util.ObjectPool;

import org.apache.log4j.Logger;

import java.util.*;

/**
 * A <code>RolapResult</code> is the result of running a query.
 *
 * @author jhyde
 * @since 10 August, 2001
 * @version $Id$
 */
public class RolapResult extends ResultBase {

    static final Logger LOGGER = Logger.getLogger(ResultBase.class);

    private RolapEvaluator evaluator;
    private final CellKey point;

    private CellInfoContainer cellInfos;
    private FastBatchingCellReader batchingReader;
    private final CellReader aggregatingReader =
        AggregationManager.instance().getCacheCellReader();
    private Modulos modulos = null;
    private final int maxEvalDepth =
            MondrianProperties.instance().MaxEvalDepth.get();

    private final Map<Integer, Boolean> positionsHighCardinality =
        new HashMap<Integer, Boolean>();
    private final Map<Integer, Iterator<Position>> positionsIterators =
        new HashMap<Integer, Iterator<Position>>();
    private final Map<Integer, Integer> positionsIndexes =
        new HashMap<Integer, Integer>();
    private final Map<Integer, List<Position>> positionsCurrent =
        new HashMap<Integer, List<Position>>();

    /**
     * Creates a RolapResult.
     *
     * @param query Query
     * @param execute Whether to execute the query
     */
    RolapResult(final Query query, boolean execute) {
        super(query, new Axis[query.axes.length]);

        this.point = CellKey.Generator.newCellKey(query.axes.length);
        final int expDeps =
            MondrianProperties.instance().TestExpDependencies.get();
        if (expDeps > 0) {
            this.evaluator = new RolapDependencyTestingEvaluator(this, expDeps);
        } else {
            final RolapEvaluatorRoot root =
                new RolapResultEvaluatorRoot(this);
            this.evaluator = new RolapEvaluator(root);
        }
        RolapCube cube = (RolapCube) query.getCube();
        this.batchingReader = new FastBatchingCellReader(cube);

        this.cellInfos =
            (query.axes.length > 4)
                ? new CellInfoMap(point)
                : new CellInfoPool(query.axes.length);

        if (!execute) {
            return;
        }

        boolean normalExecution = true;
        try {
            // This call to clear the cube's cache only has an
            // effect if caching has been disabled, otherwise
            // nothing happens.
            // Clear the local cache before a query has run
            cube.clearCachedAggregations();
            // Check if there are modifications to the global aggregate cache
            cube.checkAggregateModifications();


            /////////////////////////////////////////////////////////////////
            //
            // Evaluation Algorithm
            //
            // There are three basic steps to the evaluation algorithm:
            // 1) Determine all Members for each axis but do not save
            // information (do not build the RolapAxis),
            // 2) Save all Members for each axis (build RolapAxis).
            // 3) Evaluate and store each Cell determined by the Members
            // of the axes.
            // Step 1 converges on the stable set of Members pre axis.
            // Steps 1 and 2 make sure that the data has been loaded.
            //
            // More detail follows.
            //
            // Explicit and Implicit Members:
            // A Member is said to be 'explicit' if it appears on one of
            // the Axes (one of the RolapAxis Position List of Members).
            // A Member is 'implicit' if it is in the query but does not
            // end up on any Axes (its usage, for example, is in a function).
            // When for a Dimension none of its Members are explicit in the
            // query, then the default Member is used which is like putting
            // the Member in the Slicer.
            //
            // Special Dimensions:
            // There are 2 special dimensions.
            // The first is the Time dimension. If in a schema there is
            // no ALL Member, then Whatever happens to be the default
            // Member is used if Time Members are not explicitly set
            // in the query.
            // The second is the Measures dimension. This dimension
            // NEVER has an ALL Member. A cube's default Measure is set
            // by convention - its simply the first Measure defined in the
            // cube.
            //
            // First a RolapEvaluator is created. During its creation,
            // it gets a Member from each Hierarchy. Each Member is the
            // default Member of the Hierarchy. For most Hierarchies this
            // Member is the ALL Member, but there are cases where 1)
            // a Hierarchy does not have an ALL Member or 2) the Hierarchy
            // has an ALL Member but that Member is not the default Member.
            // In these cases, the default Member is still used, but its
            // use can cause evaluation issues (seemingly strange evaluation
            // results).
            //
            // Next, load all root Members for Hierarchies that have no ALL
            // Member and load ALL Members that are not the default Member.
            //
            // Determine the Members of the Slicer axis (Step 1 above).  Any
            // Members found are added to the AxisMember object. If one of these
            // Members happens to be a Measure, then the Slicer is explicitly
            // specifying the query's Measure and this should be put into the
            // evaluator's context (replacing the default Measure which just
            // happens to be the first Measure defined in the cube).  Other
            // Members found in the AxisMember object are also placed into the
            // evaluator's context since these also are explicitly specified.
            // Also, any other Members in the AxisMember object which have the
            // same Hierarchy as Members in the list of root Members for
            // Hierarchies that have no ALL Member, replace those Members - they
            // Slicer has explicitly determined which ones to use. The
            // AxisMember object is now cleared.
            // The Slicer does not depend upon the other Axes, but the other
            // Axes depend upon both the Slicer and each other.
            //
            // The AxisMember object also checks if the number of Members
            // exceeds the ResultLimit property throwing a
            // TotalMembersLimitExceeded Exception if it does.
            //
            // For all non-Slicer axes, the Members are determined (Step 1
            // above). If a Measure is found in the AxisMember, then an
            // Axis is explicitly specifying a Measure.
            // If any Members in the AxisMember object have the same Hierarchy
            // as a Member in the set of root Members for Hierarchies that have
            // no ALL Member, then replace those root Members with the Member
            // from the AxisMember object. In this case, again, a Member
            // was explicitly specified in an Axis. If this replacement
            // occurs, then one must redo this step with the new Members.
            //
            // Now Step 3 above is done. First to the Slicer Axis and then
            // to the other Axes. Here the Axes are actually generated.
            // If a Member of an Axis is an Calculated Member (and the
            // Calculated Member is not a Member of the Measure Hierarchy),
            // then find the Dimension associated with the Calculated
            // Member and remove Members with the same Dimension in the set of
            // root Members for Hierarchies that have no ALL Member.
            // This is done because via the Calculated Member the Member
            // was implicitly specified in the query. If this removal occurs,
            // then the Axes must be re-evaluated repeating Step 3.
            //
            /////////////////////////////////////////////////////////////////


            // The AxisMember object is used to hold Members that are found
            // during Step 1 when the Axes are determined.
            final AxisMember axisMembers = new AxisMember();


            // list of ALL Members that are not default Members
            final List<Member> nonDefaultAllMembers = new ArrayList<Member>();

            // List of Members of Hierarchies that do not have an ALL Member
            List<List<Member>> nonAllMembers = new ArrayList<List<Member>>();

            // List of Measures
            final List<Member> measureMembers = new ArrayList<Member>();

            // load all root Members for Hierarchies that have no ALL
            // Member and load ALL Members that are not the default Member.
            // Also, all Measures are are gathered.
            loadSpecialMembers(
                nonDefaultAllMembers, nonAllMembers, measureMembers);

            // clear evaluation cache
            query.clearEvalCache();

            // Save, may be needed by some Expression Calc's
            query.putEvalCache("ALL_MEMBER_LIST", nonDefaultAllMembers);


            final List<List<Member>> emptyNonAllMembers =
                Collections.emptyList();

            /////////////////////////////////////////////////////////////////
            // Determine Slicer
            //
            axisMembers.setSlicer(true);
            loadMembers(
                emptyNonAllMembers,
                evaluator,
                query.slicerAxis,
                query.slicerCalc,
                axisMembers);
            axisMembers.setSlicer(false);

            if (!axisMembers.isEmpty()) {
                for (Member m : axisMembers) {
                    if (m == null) {
                        break;
                    }
                    evaluator.setSlicerContext(m);
                    if (m.isMeasure()) {
                        // A Measure was explicitly declared in the
                        // Slicer, don't need to worry about Measures
                        // for this query.
                        measureMembers.clear();
                    }
                }
                replaceNonAllMembers(nonAllMembers, axisMembers);
                axisMembers.clearMembers();
            }

            /////////////////////////////////////////////////////////////////
            // Determine Axes
            //
            boolean changed = false;

            // reset to total member count
            axisMembers.clearTotalCellCount();

            for (int i = 0; i < axes.length; i++) {
                final QueryAxis axis = query.axes[i];
                final Calc calc = query.axisCalcs[i];
                loadMembers(
                    emptyNonAllMembers, evaluator, axis, calc, axisMembers);
            }

            if (!axisMembers.isEmpty()) {
                for (Member m : axisMembers) {
                    if (m.isMeasure()) {
                        // A Measure was explicitly declared on an
                        // axis, don't need to worry about Measures
                        // for this query.
                        measureMembers.clear();
                    }
                }
                changed = replaceNonAllMembers(nonAllMembers, axisMembers);
                axisMembers.clearMembers();
            }

            if (changed) {
                // only count number of members, do not collect any
                axisMembers.countOnly(true);
                // reset to total member count
                axisMembers.clearTotalCellCount();

                for (int i = 0; i < axes.length; i++) {
                    final QueryAxis axis = query.axes[i];
                    final Calc calc = query.axisCalcs[i];
                    loadMembers(
                        nonAllMembers,
                        evaluator.push(),
                        axis, calc, axisMembers);
                }
            }

            // throws exception if number of members exceeds limit
            axisMembers.checkLimit();

            /////////////////////////////////////////////////////////////////
            // Execute Slicer
            //
            this.slicerAxis =
                evalExecute(
                    nonAllMembers,
                    nonAllMembers.size() - 1,
                    evaluator.push(),
                    query.slicerAxis,
                    query.slicerCalc);

            // Use the context created by the slicer for the other
            // axes.  For example, "select filter([Customers], [Store
            // Sales] > 100) on columns from Sales where
            // ([Time].[1998])" should show customers whose 1998 (not
            // total) purchases exceeded 100.

            // Getting the Position list's size and the Position
            // at index == 0 will, in fact, cause an Iterable-base
            // Axis Position List to become a List-base Axis
            // Position List (and increase memory usage), but for
            // the slicer axis, the number of Positions is (generally) very
            // small, so who cares.
            final List<Position> positionList = slicerAxis.getPositions();
            RolapEvaluator evaluator = this.evaluator;
            if (positionList.size() > 1) {
                int arity = positionList.get(0).size();
                List<Member[]> tupleList =
                    new ArrayList<Member[]>(positionList.size());
                for (Position position : positionList) {
                    final Member[] members = new Member[arity];
                    for (int i = 0; i < position.size(); i++) {
                        members[i] = position.get(i);
                    }
                    tupleList.add(members);
                }
                tupleList =
                    AggregateFunDef.AggregateCalc.optimizeTupleList(
                        evaluator,
                        tupleList);

                final Calc valueCalc =
                    new ValueCalc(
                        new DummyExp(new ScalarType()));
                final List<Member[]> tupleList1 = tupleList;
                final Calc calc =
                    new GenericCalc(
                        new DummyExp(query.slicerCalc.getType()))
                    {
                        public Object evaluate(Evaluator evaluator) {
                            return AggregateFunDef.AggregateCalc.aggregate(
                                valueCalc, evaluator, tupleList1);
                        }
                    };
                final List<RolapHierarchy> hierarchyList =
                    new AbstractList<RolapHierarchy>() {
                        final Position pos0 = positionList.get(0);

                        public RolapHierarchy get(int index) {
                            return
                                ((RolapMember) pos0.get(index)).getHierarchy();
                        }

                        public int size() {
                            return 0;
                        }
                    };
                evaluator = evaluator.push(
                    new RolapTupleCalculation(hierarchyList, calc));
            }

            /////////////////////////////////////////////////////////////////
            // Execute Axes
            //
            boolean redo = true;
            while (redo) {
                RolapEvaluator e = evaluator.push();
                redo = false;

                for (int i = 0; i < axes.length; i++) {
                    QueryAxis axis = query.axes[i];
                    final Calc calc = query.axisCalcs[i];
                    Axis axisResult = evalExecute(
                        nonAllMembers, nonAllMembers.size() - 1, e, axis, calc);

                    if (!nonAllMembers.isEmpty()) {
                        List<Position> pl = axisResult.getPositions();
                        if (!pl.isEmpty()) {
                            // Only need to process the first Position
                            Position p = pl.get(0);
                            for (Member m : p) {
                                if (m.isCalculated()) {
                                    CalculatedMeasureVisitor visitor =
                                        new CalculatedMeasureVisitor();
                                    m.getExpression().accept(visitor);
                                    Dimension dimension = visitor.dimension;
                                    redo = removeDimension(
                                        dimension, nonAllMembers);
                                }
                            }
                        }
                    }
                    this.axes[i] = axisResult;
                }
            }

            // Get value for each Cell
            executeBody(evaluator, this.query, new int[axes.length]);

            // If you are very close to running out of memory due to
            // the number of CellInfo's in cellInfos, then calling this
            // may cause the out of memory one is trying to aviod.
            // On the other hand, calling this can reduce the size of
            // the ObjectPool's internal storage by half (but, of course,
            // it will not reduce the size of the stored objects themselves).
            // Only call this if there are lots of CellInfo.
            if (this.cellInfos.size() > 10000) {
                this.cellInfos.trimToSize();
            }
        } catch (ResultLimitExceededException ex) {
            // If one gets a ResultLimitExceededException, then
            // don't count on anything being worth caching.
            normalExecution = false;

            // De-reference data structures that might be holding
            // partial results but surely are taking up memory.
            evaluator = null;
            cellInfos = null;
            batchingReader = null;
            for (int i = 0; i < axes.length; i++) {
                axes[i] = null;
            }
            slicerAxis = null;

            query.clearEvalCache();

            throw ex;
        } finally {
            if (normalExecution) {
                // Push all modifications to the aggregate cache to the global
                // cache so each thread can start using it
                cube.pushAggregateModificationsToGlobalCache();

                // Expression cache duration is for each query. It is time to
                // clear out the whole expression cache at the end of a query.
                evaluator.clearExpResultCache(true);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("RolapResult<init>: " + Util.printMemory());
            }
        }
    }

    protected boolean removeDimension(
        Dimension dimension,
        List<List<Member>> nonAllMembers)
    {
        boolean changed = false;
        for (ListIterator<List<Member>> it = nonAllMembers.listIterator();
                it.hasNext();)
        {
            List<Member> ms = it.next();
            Dimension d = ms.get(0).getHierarchy().getDimension();
            if (d.equals(dimension)) {
                it.remove();
            }
        }
        return changed;
    }

    private static class CalculatedMeasureVisitor
        extends mondrian.mdx.MdxVisitorImpl
    {
        Dimension dimension;

        CalculatedMeasureVisitor() {
        }
        public Object visit(mondrian.olap.Formula formula) {
            return null;
        }
        public Object visit(mondrian.mdx.ResolvedFunCall call) {
            return null;
        }
        public Object visit(mondrian.olap.Id id) {
            return null;
        }
        public Object visit(mondrian.mdx.ParameterExpr parameterExpr) {
            return null;
        }
        public Object visit(mondrian.mdx.DimensionExpr dimensionExpr) {
            dimension = dimensionExpr.getDimension();
            return null;
        }
        public Object visit(mondrian.mdx.HierarchyExpr hierarchyExpr) {
            Hierarchy hierarchy = hierarchyExpr.getHierarchy();
            dimension = hierarchy.getDimension();
            return null;
        }
        public Object visit(mondrian.mdx.LevelExpr levelExpr) {
            return null;
        }
        public Object visit(mondrian.mdx.MemberExpr memberExpr)  {
            Member member = memberExpr.getMember();
            dimension = member.getHierarchy().getDimension();
            return null;
        }
        public Object visit(mondrian.mdx.NamedSetExpr namedSetExpr) {
            return null;
        }
        public Object visit(mondrian.olap.Literal literal) {
            return null;
        }
    }

    protected boolean replaceNonAllMembers(
        List<List<Member>> nonAllMembers,
        AxisMember axisMembers)
    {
        boolean changed = false;
        List<Member> mList = new ArrayList<Member>();
        for (ListIterator<List<Member>> it = nonAllMembers.listIterator();
                it.hasNext();)
        {
            List<Member> ms = it.next();
            Hierarchy h = ms.get(0).getHierarchy();
            mList.clear();
            for (Member m : axisMembers) {
                if (m.getHierarchy().equals(h)) {
                    mList.add(m);
                }
            }
            if (! mList.isEmpty()) {
                changed = true;
                it.set(mList);
            }
        }
        return changed;
    }

    protected void loadMembers(
        List<List<Member>> nonAllMembers,
        RolapEvaluator evaluator,
        QueryAxis axis,
        Calc calc,
        AxisMember axisMembers)
    {
        int attempt = 0;
        evaluator.setCellReader(batchingReader);
        while (true) {
            axisMembers.clearAxisCount();
            evalLoad(
                nonAllMembers,
                nonAllMembers.size() - 1,
                evaluator,
                axis,
                calc,
                axisMembers);

            if (!batchingReader.loadAggregations(query)) {
                break;
            } else {
                // Clear invalid expression result so that the next evaluation
                // will pick up the newly loaded aggregates.
                evaluator.clearExpResultCache(false);
            }

            if (attempt++ > maxEvalDepth) {
                throw Util.newInternal(
                    "Failed to load all aggregations after "
                    + maxEvalDepth
                    + " passes; there's probably a cycle");
            }
        }
    }

    void evalLoad(
        List<List<Member>> nonAllMembers,
        int cnt,
        Evaluator evaluator,
        QueryAxis axis,
        Calc calc,
        AxisMember axisMembers)
    {
        if (cnt < 0) {
            executeAxis(evaluator.push(), axis, calc, false, axisMembers);
        } else {
            for (Member m : nonAllMembers.get(cnt)) {
                evaluator.setContext(m);
                evalLoad(
                    nonAllMembers, cnt - 1, evaluator, axis, calc, axisMembers);
            }
        }
    }

    Axis evalExecute(
        List<List<Member>> nonAllMembers,
        int cnt,
        RolapEvaluator evaluator,
        QueryAxis axis,
        Calc calc)
    {
        if (cnt < 0) {
            evaluator.setCellReader(aggregatingReader);
            return executeAxis(evaluator.push(), axis, calc, true, null);
            // No need to clear expression cache here as no new aggregates are
            // loaded(aggregatingReader reads from cache).
        } else {
            Axis axisResult = null;
            for (Member m : nonAllMembers.get(cnt)) {
                evaluator.setContext(m);
                Axis a =
                    evalExecute(nonAllMembers, cnt - 1, evaluator, axis, calc);
                boolean ordered = false;
                if (axis != null) {
                    ordered = axis.isOrdered();
                }
                axisResult = mergeAxes(axisResult, a, evaluator, ordered);
            }
            return axisResult;
        }
    }

    /**
     * Finds all root Members 1) whose Hierarchy does not have an ALL
     * Member, 2) whose default Member is not the ALL Member and 3)
     * all Measures.
     *
     * @param nonDefaultAllMembers  List of all root Members for Hierarchies
     * whose default Member is not the ALL Member.
     * @param nonAllMembers List of root Members for Hierarchies that have no
     * ALL Member.
     * @param measureMembers  List all Measures
     */
    protected void loadSpecialMembers(
        List<Member> nonDefaultAllMembers,
        List<List<Member>> nonAllMembers,
        List<Member> measureMembers)
    {
        SchemaReader schemaReader = evaluator.getSchemaReader();
        Member[] evalMembers = evaluator.getMembers();
        for (Member em : evalMembers) {
            if (em.isCalculated()) {
                continue;
            }
            Hierarchy h = em.getHierarchy();
            Dimension d = h.getDimension();
            if (d.getDimensionType() == DimensionType.TimeDimension) {
                continue;
            }
            if (!em.isAll()) {
                List<Member> rootMembers =
                    schemaReader.getHierarchyRootMembers(h);
                if (em.isMeasure()) {
                    for (Member mm : rootMembers) {
                        measureMembers.add(mm);
                    }
                } else {
                    if (h.hasAll()) {
                        for (Member m : rootMembers) {
                            if (m.isAll()) {
                                nonDefaultAllMembers.add(m);
                                break;
                            }
                        }
                    } else {
                        nonAllMembers.add(rootMembers);
                    }
                }
            }
        }
    }

    protected Logger getLogger() {
        return LOGGER;
    }

    public final RolapCube getCube() {
        return evaluator.getCube();
    }

    // implement Result
    public Axis[] getAxes() {
        return axes;
    }

    /**
     * Get the Cell for the given Cell position.
     *
     * @param pos Cell position.
     * @return the Cell associated with the Cell position.
     */
    public Cell getCell(int[] pos) {
        if (pos.length != point.size()) {
            throw Util.newError(
                    "coordinates should have dimension " + point.size());
        }

        for (int i = 0; i < pos.length; i++) {
            if (positionsHighCardinality.get(i)) {
                executeBody(evaluator, this.query, pos);
                break;
            }
        }

        CellInfo ci = cellInfos.lookup(pos);
        if (ci.value == null) {
            for (int i = 0; i < pos.length; i++) {
                int po = pos[i];
                if (po < 0 || po >= axes[i].getPositions().size()) {
                    throw Util.newError("coordinates out of range");
                }
            }
            ci.value = Util.nullValue;
        }

        return new RolapCell(this, pos.clone(), ci);
    }

    private Axis executeAxis(
        Evaluator evaluator,
        QueryAxis axis,
        Calc axisCalc,
        boolean construct,
        AxisMember axisMembers)
    {
        Axis axisResult = null;
        if (axis == null) {
            // Create an axis containing one position with no members (not
            // the same as an empty axis).
            if (construct) {
                axisResult = new RolapAxis.SingleEmptyPosition();
            }
        } else {
            final int arity =
                axis.getSet().getType() instanceof SetType
                    ? ((SetType) axis.getSet().getType()).getArity()
                    : axis.getSet().getType() instanceof TupleType
                    ? ((TupleType) axis.getSet().getType()).elementTypes.length
                    : 1;
            evaluator.setNonEmpty(axis.isNonEmpty());
            evaluator.setEvalAxes(true);
            Object value = axisCalc.evaluate(evaluator);
            if (axisCalc.getClass().getName().indexOf("OrderFunDef") != -1) {
                axis.setOrdered(true);
            }
            evaluator.setNonEmpty(false);
            if (value != null) {
                // List or Iterable of Member or Member[]
                if (value instanceof List) {
                    List<Object> list = (List) value;
                    if (construct) {
                        if (list.isEmpty()) {
                            // should be???
                            axisResult = new RolapAxis.NoPosition();
                        } else if (arity == 1) {
                            axisResult =
                                new RolapAxis.MemberList((List<Member>)value);
                        } else {
                            axisResult =
                                new RolapAxis.MemberArrayList(
                                    (List<Member[]>)value);
                        }
                    } else if (axisMembers != null) {
                        if (arity == 1) {
                            axisMembers.mergeMemberList((List<Member>) value);
                        } else {
                            axisMembers.mergeTupleList((List<Member[]>) value);
                        }
                    }
                } else {
                    // Iterable
                    Iterable<Object> iter = (Iterable) value;
                    Iterator it = iter.iterator();
                    if (construct) {
                        if (! it.hasNext()) {
                            axisResult = new RolapAxis.NoPosition();
                        } else if (arity != 1) {
                            axisResult = new RolapAxis.MemberArrayIterable(
                                (Iterable<Member[]>)value);
                        } else {
                            axisResult = new RolapAxis.MemberIterable(
                                (Iterable<Member>)value);
                        }
                    } else if (axisMembers != null) {
                        if (arity == 1) {
                            axisMembers.mergeMemberIter(it);
                        } else {
                            axisMembers.mergeTupleIter(it);
                        }
                    }
                }
            }
            evaluator.setEvalAxes(false);
        }
        return axisResult;
    }

    private void executeBody(
        RolapEvaluator evaluator,
        Query query,
        final int[] pos)
    {
        // Compute the cells several times. The first time, use a dummy
        // evaluator which collects requests.
        int count = 0;
        while (true) {
            evaluator.setCellReader(batchingReader);
            executeStripe(query.axes.length - 1, evaluator.push(), pos);

            // Retrieve the aggregations collected.
            //
            if (!batchingReader.loadAggregations(query)) {
                // We got all of the cells we needed, so the result must be
                // correct.
                return;
            } else {
                // Clear invalid expression result so that the next evaluation
                // will pick up the newly loaded aggregates.
                evaluator.clearExpResultCache(false);
            }

            if (count++ > maxEvalDepth) {
                if (evaluator instanceof RolapDependencyTestingEvaluator) {
                    // The dependency testing evaluator can trigger new
                    // requests every cycle. So let is run as normal for
                    // the first N times, then run it disabled.
                    ((RolapDependencyTestingEvaluator.DteRoot)
                        evaluator.root).disabled = true;
                    if (count > maxEvalDepth * 2) {
                        throw Util.newInternal(
                            "Query required more than " + count
                            + " iterations");
                    }
                } else {
                    throw Util.newInternal(
                        "Query required more than " + count + " iterations");
                }
            }

            cellInfos.clear();
        }
    }

    boolean isDirty() {
        return batchingReader.isDirty();
    }

    /**
     * Evaluates an expression. Intended for evaluating named sets.
     *
     * @param calc Compiled expression
     * @param evaluator Evaluation context
     * @return Result
     */
    Object evaluateExp(Calc calc, RolapEvaluator evaluator) {
        int attempt = 0;
        boolean dirty = batchingReader.isDirty();
        while (true) {
            RolapEvaluator ev = evaluator.push();

            ev.setCellReader(batchingReader);
            Object preliminaryValue = calc.evaluate(ev);
            if (preliminaryValue instanceof Iterable
                && !(preliminaryValue instanceof List))
            {
                Iterable iterable = (Iterable) preliminaryValue;
                for (Object anIterable : iterable) {
                    Util.discard(anIterable);
                }
            }

            if (!batchingReader.loadAggregations(evaluator.getQuery())) {
                break;
            } else {
                // Clear invalid expression result so that the next evaluation
                // will pick up the newly loaded aggregates.
                ev.clearExpResultCache(false);
            }

            if (attempt++ > maxEvalDepth) {
                throw Util.newInternal(
                    "Failed to load all aggregations after "
                    + maxEvalDepth + "passes; there's probably a cycle");
            }
        }

        // If there were pending reads when we entered, some of the other
        // expressions may have been evaluated incorrectly. Set the reader's
        // 'dirty' flag so that the caller knows that it must re-evaluate them.
        if (dirty) {
            batchingReader.setDirty(true);
        }

        RolapEvaluator ev = evaluator.push();
        ev.setCellReader(aggregatingReader);
        return calc.evaluate(ev);
    }

    private void executeStripe(
        int axisOrdinal,
        RolapEvaluator revaluator,
        final int[] pos)
    {
        if (axisOrdinal < 0) {
            Axis axis = slicerAxis;
            List<Position> positions = axis.getPositions();
            for (Position position : positions) {
                getQuery().checkCancelOrTimeout();
                revaluator.setContext(position);
                Object o;
                try {
                    o = revaluator.evaluateCurrent();
                } catch (MondrianEvaluationException e) {
                    LOGGER.warn("Mondrian: exception in executeStripe.", e);
                    o = e;
                }

                CellInfo ci = null;

                // Get the Cell's format string and value formatting
                // Object.
                try {
                    // This code is a combination of the code found in
                    // the old RolapResult
                    // <code>getCellNoDefaultFormatString</code> method and
                    // the old RolapCell <code>getFormattedValue</code> method.

                    // Create a CellInfo object for the given position
                    // integer array.
                    ci = cellInfos.create(point.getOrdinals());

                    String cachedFormatString = null;
                    ValueFormatter valueFormatter;

                    // Determine if there is a CellFormatter registered for
                    // the current Cube's Measure's Dimension. If so,
                    // then find or create a CellFormatterValueFormatter
                    // for it. If not, then find or create a Locale based
                    // FormatValueFormatter.
                    final RolapCube cube = getCube();
                    Hierarchy measuresHierarchy =
                        cube.getMeasuresHierarchy();
                    RolapMeasure m =
                        (RolapMeasure) revaluator.getContext(measuresHierarchy);
                    CellFormatter cf = m.getFormatter();
                    if (cf != null) {
                        valueFormatter = cellFormatters.get(cf);
                        if (valueFormatter == null) {
                            valueFormatter =
                                new CellFormatterValueFormatter(cf);
                            cellFormatters.put(cf, valueFormatter);
                        }
                    } else {
                        cachedFormatString = revaluator.getFormatString();
                        Locale locale = query.getConnection().getLocale();
                        valueFormatter = formatValueFormatters.get(locale);
                        if (valueFormatter == null) {
                            valueFormatter = new FormatValueFormatter(locale);
                            formatValueFormatters.put(locale, valueFormatter);
                        }
                    }

                    ci.formatString = cachedFormatString;
                    ci.valueFormatter = valueFormatter;
                } catch (ResultLimitExceededException e) {
                    // Do NOT ignore a ResultLimitExceededException!!!
                    throw e;
                } catch (MondrianEvaluationException e) {
                    // ignore but warn
                    LOGGER.warn("Mondrian: exception in executeStripe.", e);
                } catch (Error e) {
                    // Errors indicate fatal JVM problems; do not discard
                    throw e;
                } catch (Throwable e) {
                    LOGGER.warn("Mondrian: exception in executeStripe.", e);
                    Util.discard(e);
                }

                if (o == RolapUtil.valueNotReadyException) {
                    continue;
                }

                ci.value = o;
            }
        } else {
            Axis axis = axes[axisOrdinal];
            List<Position> positions = axis.getPositions();
            if (positionsHighCardinality.get(axisOrdinal) == null
                && !positions.isEmpty()
                && !positions.get(0).isEmpty())
            {
                positionsHighCardinality.put(
                    axisOrdinal,
                    positions.get(0).get(0).getDimension()
                        .isHighCardinality());
            }
            if (positionsHighCardinality.get(axisOrdinal) != null
                && positionsHighCardinality.get(axisOrdinal))
            {
                final int limit =
                    MondrianProperties.instance().HighCardChunkSize.get();
                if (positionsIterators.get(axisOrdinal) == null) {
                    final Iterator<Position> it = positions.iterator();
                    positionsIterators.put(axisOrdinal, it);
                    positionsIndexes.put(axisOrdinal, 0);
                    final List<Position> subPositions =
                        new ArrayList<Position>();
                    for (int i = 0; i < limit && it.hasNext(); i++) {
                        subPositions.add(it.next());
                    }
                    positionsCurrent.put(axisOrdinal, subPositions);
                }
                final Iterator<Position> it =
                    positionsIterators.get(axisOrdinal);
                final int positionIndex = positionsIndexes.get(axisOrdinal);
                List<Position> subPositions = positionsCurrent.get(axisOrdinal);

                if (subPositions == null) {
                    return;
                }

                int pi;
                if (pos[axisOrdinal] > positionIndex + subPositions.size() - 1
                        && subPositions.size() == limit)
                {
                    pi = positionIndex + subPositions.size();
                    positionsIndexes.put(
                        axisOrdinal, positionIndex + subPositions.size());
                    subPositions.subList(0, subPositions.size()).clear();
                    for (int i = 0; i < limit && it.hasNext(); i++) {
                        subPositions.add(it.next());
                    }
                    positionsCurrent.put(axisOrdinal, subPositions);
                } else {
                    pi = positionIndex;
                }
                for (final Position position : subPositions) {
                    point.setAxis(axisOrdinal, pi);
                    revaluator.setContext(position);
                    getQuery().checkCancelOrTimeout();
                    executeStripe(axisOrdinal - 1, revaluator, pos);
                    pi++;
                }
            } else {
                int positionIndex = 0;
                for (final Position position : positions) {
                    point.setAxis(axisOrdinal, positionIndex);
                    revaluator.setContext(position);
                    getQuery().checkCancelOrTimeout();
                    executeStripe(axisOrdinal - 1, revaluator, pos);
                    positionIndex++;
                }
            }
        }
    }

    /**
     * Converts a set of cell coordinates to a cell ordinal.
     *
     * <p>This method can be expensive, because the ordinal is computed from the
     * length of the axes, and therefore the axes need to be instantiated.
     */
    int getCellOrdinal(int[] pos) {
        if (modulos == null) {
            makeModulos();
        }
        return modulos.getCellOrdinal(pos);
    }

    /*
     * Instantiates the calculator to convert cell coordinates to a cell ordinal
     * and vice versa.
     *
     * <p>To create the calculator, any axis that is based upon an Iterable is
     * converted into a List - thus increasing memory usage.
     */
    protected void makeModulos() {
        modulos = Modulos.Generator.create(axes);
    }

    /**
     * Called only by RolapCell.
     *
     * @param pos Coordinates of cell
     * @return Evaluator whose context is the given cell
     */
    RolapEvaluator getCellEvaluator(int[] pos) {
        final RolapEvaluator cellEvaluator = evaluator.push();
        for (int i = 0; i < pos.length; i++) {
            Position position = axes[i].getPositions().get(pos[i]);
            cellEvaluator.setContext(position);
        }
        return cellEvaluator;
    }

    /**
     * Called only by RolapCell. Use this when creating an Evaluator
     * (using method {@link #getCellEvaluator}) is not required.
     *
     * @param pos Coordinates of cell
     * @return Members which form the context of the given cell
     */
    RolapMember[] getCellMembers(int[] pos) {
        RolapMember[] members = (RolapMember[]) evaluator.getMembers().clone();
        for (int i = 0; i < pos.length; i++) {
            Position position = axes[i].getPositions().get(pos[i]);
            for (Member member : position) {
                RolapMember m = (RolapMember) member;
                int ordinal = m.getHierarchy().getOrdinalInCube();
                members[ordinal] = m;
            }
        }
        return members;
    }

    Evaluator getRootEvaluator() {
        return evaluator;
    }

    Evaluator getEvaluator(int[] pos) {
        // Set up evaluator's context, so that context-dependent format
        // strings work properly.
        Evaluator cellEvaluator = evaluator.push();
        for (int i = -1; i < axes.length; i++) {
            Axis axis;
            int index;
            if (i < 0) {
                axis = slicerAxis;
                index = 0;
            } else {
                axis = axes[i];
                index = pos[i];
            }
            Position position = axis.getPositions().get(index);
            cellEvaluator.setContext(position);
        }
        return cellEvaluator;
    }


    /**
     * Counts and collects Members found of the axes.
     * This class does two things. First it collects all Members
     * found during the Member-Determination phase.
     * Secondly, it counts how many Members are on each axis and
     * forms the product, the totalCellCount which is checked against
     * the ResultLimit property value.
     */
    private static class AxisMember implements Iterable<Member> {
        private final List<Member> members;
        private final int limit;
        private boolean isSlicer;
        private int totalCellCount;
        private int axisCount;
        private boolean countOnly;

        AxisMember() {
            this.countOnly = false;
            this.members = new ConcatenableList<Member>();
            this.totalCellCount = 1;
            this.axisCount = 0;
            // Now that the axes are evaluated, make sure that the number of
            // cells does not exceed the result limit.
            this.limit = MondrianProperties.instance().ResultLimit.get();
        }

        public Iterator<Member> iterator() {
            return members.iterator();
        }

        void setSlicer(final boolean isSlicer) {
            this.isSlicer = isSlicer;
        }

        boolean isEmpty() {
            return this.members.isEmpty();
        }

        void countOnly(boolean countOnly) {
            this.countOnly = countOnly;
        }

        void checkLimit() {
            if (this.limit > 0) {
                this.totalCellCount *= this.axisCount;
                if (this.totalCellCount > this.limit) {
                    throw MondrianResource.instance().TotalMembersLimitExceeded
                        .ex(
                            this.totalCellCount,
                            this.limit);
                }
                this.axisCount = 0;
            }
        }

        void clearAxisCount() {
            this.axisCount = 0;
        }

        void clearTotalCellCount() {
            this.totalCellCount = 1;
        }

        void clearMembers() {
            this.members.clear();
            this.axisCount = 0;
            this.totalCellCount = 1;
        }

        List<Member> members() {
            return this.members;
        }

        void mergeMemberList(List<Member> list) {
            for (Member o : list) {
                if (o == null) {
                    continue;
                }
                if (o.getDimension().isHighCardinality()) {
                    break;
                }
                mergeMember(o);
            }
        }

        void mergeTupleList(List<Member[]> list) {
            for (Member[] o : list) {
                mergeTuple(o);
            }
        }

        private void mergeMemberIter(Iterator<Member> it) {
            while (it.hasNext()) {
                mergeMember(it.next());
            }
        }

        private void mergeTupleIter(Iterator<Member[]> it) {
            while (it.hasNext()) {
                mergeTuple(it.next());
            }
        }

        private Member getTopParent(final Member m) {
            Member parent = m.getParentMember();
            return (parent == null) ? m : getTopParent(parent);
        }

        private void mergeTuple(final Member[] members) {
            for (Member member : members) {
                mergeMember(member);
            }
        }

        private void mergeMember(final Member member) {
            this.axisCount++;
            if (! countOnly) {
                if (isSlicer) {
                    if (! members.contains(member)) {
                        members.add(member);
                    }
                } else {
                    if (member.isNull()) {
                        return;
                    } else if (member.isMeasure()) {
                        return;
                    } else if (member.isCalculated()) {
                        return;
                    } else if (member.isAll()) {
                        return;
                    }
                    Member topParent = getTopParent(member);
                    if (! this.members.contains(topParent)) {
                        this.members.add(topParent);
                    }
                }
            }
        }
    }

    /**
     * Extension to {@link RolapEvaluatorRoot} which is capable
     * of evaluating named sets.<p/>
     *
     * A given set is only evaluated once each time a query is executed; the
     * result is added to the {@link #namedSetEvaluators} cache on first execution
     * and re-used.<p/>
     *
     * <p>Named sets are always evaluated in the context of the slicer.<p/>
     */
    protected static class RolapResultEvaluatorRoot
        extends RolapEvaluatorRoot
    {
        /**
         * Maps the names of sets to their values. Populated on demand.
         */
        private final Map<String, RolapNamedSetEvaluator> namedSetEvaluators =
            new HashMap<String, RolapNamedSetEvaluator>();

        /**
         * Evaluator containing context resulting from evaluating the slicer.
         */
        RolapEvaluator slicerEvaluator;
        final RolapResult result;
        private static final Object Sentinel = new Object();

        public RolapResultEvaluatorRoot(RolapResult result) {
            super(result.query);
            this.result = result;
        }

        protected void init(Evaluator evaluator) {
            slicerEvaluator = (RolapEvaluator) evaluator;
        }

        protected Evaluator.NamedSetEvaluator evaluateNamedSet(
            final String name,
            final Exp exp)
        {
            RolapNamedSetEvaluator value = namedSetEvaluators.get(name);
            if (value == null) {
                value = new RolapNamedSetEvaluator(this, name, exp);
                namedSetEvaluators.put(name, value);
            }
            return value;
        }

        public Object getParameterValue(ParameterSlot slot) {
            Object value = slot.getParameterValue();
            if (value != null) {
                return value;
            }

            // Look in other places for the value. Which places we look depends
            // on the scope of the parameter.
            Parameter.Scope scope = slot.getParameter().getScope();
            switch (scope) {
            case System:
                // TODO: implement system params

                // fall through
            case Schema:
                // TODO: implement schema params

                // fall through
            case Connection:
                // if it's set in the session, return that value

                // fall through
            case Statement:
                break;

            default:
                throw Util.badValue(scope);
            }

            // Not set in any accessible scope. Evaluate the default value,
            // then cache it.
            value = slot.getCachedDefaultValue();
            if (value != null) {
                if (value == Sentinel) {
                    throw MondrianResource.instance()
                        .CycleDuringParameterEvaluation.ex(
                            slot.getParameter().getName());
                }
                return value;
            }
            // Set value to a sentinel, so we can detect cyclic evaluation.
            slot.setCachedDefaultValue(Sentinel);
            value = result.evaluateExp(
                slot.getDefaultValueCalc(), slicerEvaluator.push());
            slot.setCachedDefaultValue(value);
            return value;
        }
    }

    /**
     * Formatter to convert values into formatted strings.
     *
     * <p>Every Cell has a value, a format string (or CellFormatter) and a
     * formatted value string.
     * There are a wide range of possible values (pick a Double, any
     * Double - its a value). Because there are lots of possible values,
     * there are also lots of possible formatted value strings. On the
     * other hand, there are only a very small number of format strings
     * and CellFormatter's. These formatters are to be cached
     * in a synchronized HashMaps in order to limit how many copies
     * need to be kept around.
     *
     * <p>
     * There are two implementations of the ValueFormatter interface:<ul>
     * <li>{@link CellFormatterValueFormatter}, which formats using a
     * user-registered {@link CellFormatter}; and
     * <li> {@link FormatValueFormatter}, which takes the {@link Locale} object.
     * </ul>
     */
    interface ValueFormatter {
        /**
         * Formats a value according to a format string.
         *
         * @param value Value
         * @param formatString Format string
         * @return Formatted value
         */
        String format(Object value, String formatString);

        /**
         * Formatter that always returns the empty string.
         */
        public static final ValueFormatter EMPTY = new ValueFormatter() {
            public String format(Object value, String formatString) {
                return "";
            }
        };
    }

    /**
     * A CellFormatterValueFormatter uses a user-defined {@link CellFormatter}
     * to format values.
     */
    static class CellFormatterValueFormatter implements ValueFormatter {
        final CellFormatter cf;

        /**
         * Creates a CellFormatterValueFormatter
         *
         * @param cf Cell formatter
         */
        CellFormatterValueFormatter(CellFormatter cf) {
            this.cf = cf;
        }
        public String format(Object value, String formatString) {
            return cf.formatCell(value);
        }
    }

    /**
     * A FormatValueFormatter takes a {@link Locale}
     * as a parameter and uses it to get the {@link mondrian.util.Format}
     * to be used in formatting an Object value with a
     * given format string.
     */
    static class FormatValueFormatter implements ValueFormatter {
        final Locale locale;

        /**
         * Creates a FormatValueFormatter.
         *
         * @param locale Locale
         */
        FormatValueFormatter(Locale locale) {
            this.locale = locale;
        }
        public String format(Object value, String formatString) {
            if (value == Util.nullValue) {
                Format format = getFormat(formatString);
                return format.format(null);
            } else if (value instanceof Throwable) {
                return "#ERR: " + value.toString();
            } else if (value instanceof String) {
                return (String) value;
            } else {
                Format format = getFormat(formatString);
                return format.format(value);
            }
        }
        private Format getFormat(String formatString) {
            return Format.get(formatString, locale);
        }
    }

    /*
     * Generate a long ordinal based upon the values of the integers
     * stored in the cell position array. With this mechanism, the
     * Cell information can be stored using a long key (rather than
     * the array integer of positions) thus saving memory. The trick
     * is to use a 'large number' per axis in order to convert from
     * position array to long key where the 'large number' is greater
     * than the number of members in the axis.
     * The largest 'long' is java.lang.Long.MAX_VALUE which is
     * 9,223,372,036,854,776,000. The product of the maximum number
     * of members per axis must be less than this maximum 'long'
     * value (otherwise one gets hashing collisions).
     * <p>
     * For a single axis, the maximum number of members is equal to
     * the max 'long' number, 9,223,372,036,854,776,000.
     * <p>
     * For two axes, the maximum number of members is the square root
     * of the max 'long' number, 9,223,372,036,854,776,000, which is
     * slightly bigger than 2,147,483,647 (which is the maximum integer).
     * <p>
     * For three axes, the maximum number of members per axis is the
     * cube root of the max 'long' which is about 2,000,000
     * <p>
     * For four axes the forth root is about 50,000.
     * <p>
     * For five or more axes, the maximum number of members per axis
     * based upon the root of the maximum 'long' number,
     * start getting too small to guarantee that it will be
     * smaller than the number of members on a given axis and so
     * we must resort to the Map-base Cell container.
     */



    /**
     * Synchronized Map from Locale to ValueFormatter. It is expected that
     * there will be only a small number of Locale's.
     * Should these be a WeakHashMap?
     */
    protected static final Map<Locale, ValueFormatter>
            formatValueFormatters =
            Collections.synchronizedMap(new HashMap<Locale, ValueFormatter>());

    /**
     * Synchronized Map from CellFormatter to ValueFormatter.
     * CellFormatter's are defined in schema files. It is expected
     * the there will only be a small number of CellFormatter's.
     * Should these be a WeakHashMap?
     */
    protected static final Map<CellFormatter, ValueFormatter>
        cellFormatters =
            Collections.synchronizedMap(
                new HashMap<CellFormatter, ValueFormatter>());

    /**
     * A CellInfo contains all of the information that a Cell requires.
     * It is placed in the cellInfos map during evaluation and
     * serves as a constructor parameter for {@link RolapCell}.
     *
     * <p>During the evaluation stage they are mutable but after evaluation has
     * finished they are not changed.
     */
    static class CellInfo {
        Object value;
        String formatString;
        ValueFormatter valueFormatter;
        long key;

        /**
         * Creates a CellInfo representing the position of a cell.
         *
         * @param key Ordinal representing the position of a cell
         */
        CellInfo(long key) {
            this(key, null, null, ValueFormatter.EMPTY);
        }

        /**
         * Creates a CellInfo with position, value, format string and formatter
         * of a cell.
         *
         * @param key Ordinal representing the position of a cell
         * @param value Value of cell, or null if not yet known
         * @param formatString Format string of cell, or null
         * @param valueFormatter Formatter for cell, or null
         */
        CellInfo(
            long key,
            Object value,
            String formatString,
            ValueFormatter valueFormatter)
        {
            this.key = key;
            this.value = value;
            this.formatString = formatString;
            this.valueFormatter = valueFormatter;
        }

        public int hashCode() {
            // Combine the upper 32 bits of the key with the lower 32 bits.
            // We used to use 'key ^ (key >>> 32)' but that was bad, because
            // CellKey.Two encodes (i, j) as
            // (i * Integer.MAX_VALUE + j), which is practically the same as
            // (i << 32, j). If i and j were
            // both k bits long, all of the hashcodes were k bits long too!
            return (int) (key ^ (key >>> 11) ^ (key >>> 24));
        }

        public boolean equals(Object o) {
            if (o instanceof CellInfo) {
                CellInfo that = (CellInfo) o;
                return that.key == this.key;
            } else {
                return false;
            }
        }

        /**
         * Returns the formatted value of the Cell
         * @return formatted value of the Cell
         */
        String getFormatValue() {
            return valueFormatter.format(value, formatString);
        }
    }

    /**
     * API for the creation and
     * lookup of {@link CellInfo} objects. There are two implementations,
     * one that uses a Map for storage and the other uses an ObjectPool.
     */
    interface CellInfoContainer {
        /**
         * Returns the number of CellInfo objects in this container.
         * @return  the number of CellInfo objects.
         */
        int size();
        /**
         * Reduces the size of the internal data structures needed to
         * support the current entries. This should be called after
         * all CellInfo objects have been added to container.
         */
        void trimToSize();
        /**
         * Removes all CellInfo objects from container. Does not
         * change the size of the internal data structures.
         */
        void clear();
        /**
         * Creates a new CellInfo object, adds it to the container
         * a location <code>pos</code> and returns it.
         *
         * @param pos where to store CellInfo object.
         * @return the newly create CellInfo object.
         */
        CellInfo create(int[] pos);
        /**
         * Gets the CellInfo object at the location <code>pos</code>.
         *
         * @param pos where to find the CellInfo object.
         * @return the CellInfo found or null.
         */
        CellInfo lookup(int[] pos);
    }

    /**
     * Implementation of {@link CellInfoContainer} which uses a {@link Map} to
     * store CellInfo Objects.
     *
     * <p>Note that the CellKey point instance variable is the same
     * Object (NOT a copy) that is used and modified during
     * the recursive calls to executeStripe - the
     * <code>create</code> method relies on this fact.
     */
    static class CellInfoMap implements CellInfoContainer {
        private final Map<CellKey, CellInfo> cellInfoMap;
        private final CellKey point;

        /**
         * Creates a CellInfoMap
         *
         * @param point Cell position
         */
        CellInfoMap(CellKey point) {
            this.point = point;
            this.cellInfoMap = new HashMap<CellKey, CellInfo>();
        }
        public int size() {
            return this.cellInfoMap.size();
        }
        public void trimToSize() {
            // empty
        }
        public void clear() {
            this.cellInfoMap.clear();
        }
        public CellInfo create(int[] pos) {
            CellKey key = this.point.copy();
            CellInfo ci = this.cellInfoMap.get(key);
            if (ci == null) {
                ci = new CellInfo(0);
                this.cellInfoMap.put(key, ci);
            }
            return ci;
        }
        public CellInfo lookup(int[] pos) {
            CellKey key = CellKey.Generator.newCellKey(pos);
            return this.cellInfoMap.get(key);
        }
    }

    /**
     * Implementation of {@link CellInfoContainer} which uses an
     * {@link ObjectPool} to store {@link CellInfo} Objects.
     *
     * <p>There is an inner interface (<code>CellKeyMaker</code>) and
     * implementations for 0 through 4 axes that convert the Cell
     * position integer array into a long.
     *
     * <p>
     * It should be noted that there is an alternate approach.
     * As the <code>executeStripe</code>
     * method is recursively called, at each call it is known which
     * axis is being iterated across and it is known whether or
     * not the Position object for that axis is a List or just
     * an Iterable. It it is a List, then one knows the real
     * size of the axis. If it is an Iterable, then one has to
     * use one of the MAX_AXIS_SIZE values. Given that this information
     * is available when one recursives down to the next
     * <code>executeStripe</code> call, the Cell ordinal, the position
     * integer array could converted to an <code>long</code>, could
     * be generated on the call stack!! Just a thought for the future.
     */
    static class CellInfoPool implements CellInfoContainer {
        /**
         * The maximum number of Members, 2,147,483,647, that can be any given
         * Axis when the number of Axes is 2.
         */
        protected static final long MAX_AXIS_SIZE_2 = 2147483647;
        /**
         * The maximum number of Members, 2,000,000, that can be any given
         * Axis when the number of Axes is 3.
         */
        protected static final long MAX_AXIS_SIZE_3 = 2000000;
        /**
         * The maximum number of Members, 50,000, that can be any given
         * Axis when the number of Axes is 4.
         */
        protected static final long MAX_AXIS_SIZE_4 = 50000;

        /**
         * Implementations of CellKeyMaker convert the Cell
         * position integer array to a <code>long</code>.
         */
        interface CellKeyMaker {
            long generate(int[] pos);
        }
        /**
         * For axis of size 0.
         */
        static class Zero implements CellKeyMaker {
            public long generate(int[] pos) {
                return 0;
            }
        }
        /**
         * For axis of size 1.
         */
        static class One implements CellKeyMaker {
            public long generate(int[] pos) {
                return pos[0];
            }
        }
        /**
         * For axis of size 2.
         */
        static class Two implements CellKeyMaker {
            public long generate(int[] pos) {
                long l = pos[0];
                l += (MAX_AXIS_SIZE_2 * (long) pos[1]);
                return l;
            }
        }
        /**
         * For axis of size 3.
         */
        static class Three implements CellKeyMaker {
            public long generate(int[] pos) {
                long l = pos[0];
                l += (MAX_AXIS_SIZE_3 * (long) pos[1]);
                l += (MAX_AXIS_SIZE_3 * MAX_AXIS_SIZE_3 * (long) pos[2]);
                return l;
            }
        }
        /**
         * For axis of size 4.
         */
        static class Four implements CellKeyMaker {
            public long generate(int[] pos) {
                long l = pos[0];
                l += (MAX_AXIS_SIZE_4 * (long) pos[1]);
                l += (MAX_AXIS_SIZE_4 * MAX_AXIS_SIZE_4 * (long) pos[2]);
                l += (MAX_AXIS_SIZE_4 * MAX_AXIS_SIZE_4 * MAX_AXIS_SIZE_4
                      * (long) pos[3]);
                return l;
            }
        }

        private final ObjectPool<CellInfo> cellInfoPool;
        private final CellKeyMaker cellKeyMaker;

        CellInfoPool(int axisLength) {
            this.cellInfoPool = new ObjectPool<CellInfo>();
            this.cellKeyMaker = createCellKeyMaker(axisLength);
        }

        CellInfoPool(int axisLength, int initialSize) {
            this.cellInfoPool = new ObjectPool<CellInfo>(initialSize);
            this.cellKeyMaker = createCellKeyMaker(axisLength);
        }

        private static CellKeyMaker createCellKeyMaker(int axisLength) {
            switch (axisLength) {
            case 0:
                return new Zero();
            case 1:
                return new One();
            case 2:
                return new Two();
            case 3:
                return new Three();
            case 4:
                return new Four();
            default:
                throw new RuntimeException(
                    "Creating CellInfoPool with axisLength=" + axisLength);
            }
        }

        public int size() {
            return this.cellInfoPool.size();
        }
        public void trimToSize() {
            this.cellInfoPool.trimToSize();
        }
        public void clear() {
            this.cellInfoPool.clear();
        }
        public CellInfo create(int[] pos) {
            long key = this.cellKeyMaker.generate(pos);
            return this.cellInfoPool.add(new CellInfo(key));
        }
        public CellInfo lookup(int[] pos) {
            long key = this.cellKeyMaker.generate(pos);
            return this.cellInfoPool.add(new CellInfo(key));
        }
    }

    static Axis mergeAxes(
        Axis axis1,
        Axis axis2,
        RolapEvaluator evaluator,
        boolean ordered)
    {
        if (axis1 == null) {
            return axis2;
        }
        List<Position> posList1 = axis1.getPositions();
        List<Position> posList2 = axis2.getPositions();
        int arrayLen = -1;
        if (posList1 instanceof RolapAxis.PositionListBase) {
            if (posList1.isEmpty()) {
                return axis2;
            }
            arrayLen = posList1.get(0).size();
        }
        if (axis1 instanceof RolapAxis.SingleEmptyPosition) {
            return axis2;
        }
        if (axis1 instanceof RolapAxis.NoPosition) {
            return axis2;
        }
        if (posList2 instanceof RolapAxis.PositionListBase) {
            if (posList2.isEmpty()) {
                return axis1;
            }
            arrayLen = posList2.get(0).size();
        }
        if (axis2 instanceof RolapAxis.SingleEmptyPosition) {
            return axis1;
        }
        if (axis2 instanceof RolapAxis.NoPosition) {
            return axis1;
        }
        if (arrayLen == -1) {
            // Avoid materialization of axis
            arrayLen = 0;
            for (Position p1 : posList1) {
                arrayLen += p1.size();
                break;
            }
            // reset to start of List
            posList1 = axis1.getPositions();
        }
        if (arrayLen == 1) {
            // single Member per position

            // LinkedHashSet gives O(n log n) additions (versus O(n ^ 2) for
            // ArrayList, and preserves order (versus regular HashSet).
            LinkedHashSet<Member> orderedSet = new LinkedHashSet<Member>();
            for (Position p1 : posList1) {
                for (Member m1 : p1) {
                    orderedSet.add(m1);
                }
            }
            for (Position p2 : posList2) {
                for (Member m2 : p2) {
                    orderedSet.add(m2);
                }
            }
            return new RolapAxis.MemberList(
                Arrays.asList(
                    orderedSet.toArray(new Member[orderedSet.size()])));
        } else {
            // array of Members per position

            Set<List<Member>> set = new HashSet<List<Member>>();
            List<Member[]> list = new ArrayList<Member[]>();
            for (Position p1 : posList1) {
                if (set.add(p1)) {
                    Member[] members = new Member[arrayLen];
                    for (int i = 0; i < p1.size(); i++) {
                        members[i] = p1.get(i);
                    }
                    list.add(members);
                }
            }
            int halfWay = list.size();
            for (Position p2 : posList2) {
                if (set.add(p2)) {
                    Member[] members = new Member[arrayLen];
                    for (int i = 0; i < p2.size(); i++) {
                        Member m2 = p2.get(i);
                        members[i] = m2;
                    }
                    list.add(members);
                }
            }

            // if there are unique members on both axes and no order function,
            //  sort the list to ensure default order
            if (halfWay > 0 && halfWay < list.size() && !ordered) {
                Member[] membs = list.get(0);
                int membsSize = membs.length;
                ValueCalc valCalc =
                    new ValueCalc(
                        new DummyExp(new ScalarType()));
                FunUtil.sortTuples(
                    evaluator,
                    list,
                    list,
                    valCalc,
                    false,
                    false,
                    membsSize);
            }

            return new RolapAxis.MemberArrayList(list);
        }
    }

}

// End RolapResult.java
