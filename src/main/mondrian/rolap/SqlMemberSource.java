/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2001-2002 Kana Software, Inc.
// Copyright (C) 2001-2010 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 21 December, 2001
*/

package mondrian.rolap;

import mondrian.olap.*;
import mondrian.resource.MondrianResource;
import mondrian.rolap.sql.*;
import mondrian.rolap.aggmatcher.AggStar;
import mondrian.rolap.agg.AggregationManager;
import mondrian.rolap.agg.CellRequest;
import mondrian.spi.Dialect;
import mondrian.util.ObjectFactory;
import mondrian.util.CreationException;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import org.eigenbase.util.property.StringProperty;

/**
 * A <code>SqlMemberSource</code> reads members from a SQL database.
 *
 * <p>It's a good idea to put a {@link CacheMemberReader} on top of this.
 *
 * @author jhyde
 * @since 21 December, 2001
 * @version $Id$
 */
class SqlMemberSource
    implements MemberReader, SqlTupleReader.MemberBuilder
{
    private final SqlConstraintFactory sqlConstraintFactory =
        SqlConstraintFactory.instance();
    private final RolapHierarchy hierarchy;
    private final DataSource dataSource;
    private MemberCache cache;
    private int lastOrdinal = 0;
    private boolean assignOrderKeys;
    private Map<Object, Object> valuePool;

    SqlMemberSource(RolapHierarchy hierarchy) {
        this.hierarchy = hierarchy;
        this.dataSource =
            hierarchy.getRolapSchema().getInternalConnection().getDataSource();
        assignOrderKeys =
            MondrianProperties.instance().CompareSiblingsByOrderKey.get();
        valuePool = ValuePoolFactoryFactory.getValuePoolFactory().create(this);
    }

    // implement MemberSource
    public RolapHierarchy getHierarchy() {
        return hierarchy;
    }

    // implement MemberSource
    public boolean setCache(MemberCache cache) {
        this.cache = cache;
        return true; // yes, we support cache writeback
    }

    // implement MemberSource
    public int getMemberCount() {
        RolapLevel[] levels = (RolapLevel[]) hierarchy.getLevels();
        int count = 0;
        for (RolapLevel level : levels) {
            count += getLevelMemberCount(level);
        }
        return count;
    }

    public RolapMember substitute(RolapMember member) {
        return member;
    }

    public RolapMember desubstitute(RolapMember member) {
        return member;
    }

    public RolapMember lookupMember(
        List<Id.Segment> uniqueNameParts,
        boolean failIfNotFound)
    {
        throw new UnsupportedOperationException();
    }

    public int getLevelMemberCount(RolapLevel level) {
        if (level.isAll()) {
            return 1;
        }
        return getMemberCount(level, dataSource);
    }

    private int getMemberCount(RolapLevel level, DataSource dataSource) {
        boolean[] mustCount = new boolean[1];
        String sql = makeLevelMemberCountSql(level, dataSource, mustCount);
        final SqlStatement stmt =
            RolapUtil.executeQuery(
                dataSource, sql, "SqlMemberSource.getLevelMemberCount",
                "while counting members of level '" + level);
        try {
            ResultSet resultSet = stmt.getResultSet();
            int count;
            if (! mustCount[0]) {
                Util.assertTrue(resultSet.next());
                ++stmt.rowCount;
                count = resultSet.getInt(1);
            } else {
                // count distinct "manually"
                ResultSetMetaData rmd = resultSet.getMetaData();
                int nColumns = rmd.getColumnCount();
                String[] colStrings = new String[nColumns];
                count = 0;
                while (resultSet.next()) {
                    ++stmt.rowCount;
                    boolean isEqual = true;
                    for (int i = 0; i < nColumns; i++) {
                        String colStr = resultSet.getString(i + 1);
                        if (!colStr.equals(colStrings[i])) {
                            isEqual = false;
                        }
                        colStrings[i] = colStr;
                    }
                    if (!isEqual) {
                        count++;
                    }
                }
            }
            return count;
        } catch (SQLException e) {
            throw stmt.handle(e);
        } finally {
            stmt.close();
        }
    }

    /**
     * Generates the SQL statement to count the members in
     * <code>level</code>. For example, <blockquote>
     *
     * <pre>SELECT count(*) FROM (
     *   SELECT DISTINCT "country", "state_province"
     *   FROM "customer") AS "init"</pre>
     *
     * </blockquote> counts the non-leaf "state_province" level. MySQL
     * doesn't allow SELECT-in-FROM, so we use the syntax<blockquote>
     *
     * <pre>SELECT count(DISTINCT "country", "state_province")
     * FROM "customer"</pre>
     *
     * </blockquote>. The leaf level requires a different query:<blockquote>
     *
     * <pre>SELECT count(*) FROM "customer"</pre>
     *
     * </blockquote> counts the leaf "name" level of the "customer" hierarchy.
     */
    private String makeLevelMemberCountSql(
        RolapLevel level,
        DataSource dataSource,
        boolean[] mustCount)
    {
        mustCount[0] = false;
        SqlQuery sqlQuery =
            SqlQuery.newQuery(
                dataSource,
                "while generating query to count members in level " + level);
        int levelDepth = level.getDepth();
        RolapLevel[] levels = (RolapLevel[]) hierarchy.getLevels();
        if (levelDepth == levels.length) {
            // "select count(*) from schema.customer"
            sqlQuery.addSelect("count(*)");
            hierarchy.addToFrom(sqlQuery, level.getKeyExp());
            return sqlQuery.toString();
        }
        if (!sqlQuery.getDialect().allowsFromQuery()) {
            String columnList = "";
            int columnCount = 0;
            for (int i = levelDepth; i >= 0; i--) {
                RolapLevel level2 = levels[i];
                if (level2.isAll()) {
                     continue;
                }
                if (columnCount > 0) {
                    if (sqlQuery.getDialect().allowsCompoundCountDistinct()) {
                        columnList += ", ";
                    } else if (true) {
                        // for databases where both SELECT-in-FROM and
                        // COUNT DISTINCT do not work, we do not
                        // generate any count and do the count
                        // distinct "manually".
                        mustCount[0] = true;
                    } else if (sqlQuery.getDialect().getDatabaseProduct()
                        == Dialect.DatabaseProduct.SYBASE)
                    {
                        // "select count(distinct convert(varchar, c1) +
                        // convert(varchar, c2)) from table"
                        if (columnCount == 1) {
                            // Conversion to varchar is expensive, so we only
                            // do it when we know we are dealing with a
                            // compound key.
                            columnList = "convert(varchar, " + columnList + ")";
                        }
                        columnList += " + ";
                    } else {
                        // Apparently this database allows neither
                        // SELECT-in-FROM nor compound COUNT DISTINCT. I don't
                        // know any database where this happens. If you receive
                        // this error, try a workaround similar to the Sybase
                        // workaround above.
                        throw Util.newInternal(
                            "Cannot generate query to count members of level '"
                            + level.getUniqueName()
                            + "': database supports neither SELECT-in-FROM nor "
                            + "compound COUNT DISTINCT");
                    }
                }
                hierarchy.addToFrom(sqlQuery, level2.getKeyExp());

                String keyExp = level2.getKeyExp().getExpression(sqlQuery);
                if (columnCount > 0
                    && !sqlQuery.getDialect().allowsCompoundCountDistinct()
                    && sqlQuery.getDialect().getDatabaseProduct()
                    == Dialect.DatabaseProduct.SYBASE)
                {
                    keyExp = "convert(varchar, " + columnList + ")";
                }
                columnList += keyExp;

                if (level2.isUnique()) {
                    break; // no further qualification needed
                }
                ++columnCount;
            }
            if (mustCount[0]) {
                sqlQuery.addSelect(columnList);
                sqlQuery.addOrderBy(columnList, true, false, true);
            } else {
                sqlQuery.addSelect("count(DISTINCT " + columnList + ")");
            }
            return sqlQuery.toString();

        } else {
            sqlQuery.setDistinct(true);
            for (int i = levelDepth; i >= 0; i--) {
                RolapLevel level2 = levels[i];
                if (level2.isAll()) {
                    continue;
                }
                hierarchy.addToFrom(sqlQuery, level2.getKeyExp());
                sqlQuery.addSelect(level2.getKeyExp().getExpression(sqlQuery));
                if (level2.isUnique()) {
                    break; // no further qualification needed
                }
            }
            SqlQuery outerQuery =
                SqlQuery.newQuery(
                    dataSource,
                    "while generating query to count members in level "
                    + level);
            outerQuery.addSelect("count(*)");
            // Note: the "init" is for Postgres, which requires
            // FROM-queries to have an alias
            boolean failIfExists = true;
            outerQuery.addFrom(sqlQuery, "init", failIfExists);
            return outerQuery.toString();
        }
    }


    public List<RolapMember> getMembers() {
        return getMembers(dataSource);
    }

    private List<RolapMember> getMembers(DataSource dataSource) {
        String sql = makeKeysSql(dataSource);
        RolapLevel[] levels = (RolapLevel[]) hierarchy.getLevels();
        SqlStatement stmt =
            RolapUtil.executeQuery(
                dataSource, sql, "SqlMemberSource.getMembers",
                "while building member cache");
        try {
            final List<SqlStatement.Accessor> accessors = stmt.getAccessors();
            List<RolapMember> list = new ArrayList<RolapMember>();
            Map<MemberKey, RolapMember> map =
                new HashMap<MemberKey, RolapMember>();
            RolapMember root = null;
            if (hierarchy.hasAll()) {
                root = hierarchy.getAllMember();
                list.add(root);
            }

            int limit = MondrianProperties.instance().ResultLimit.get();
            ResultSet resultSet = stmt.getResultSet();
            while (resultSet.next()) {
                ++stmt.rowCount;
                if (limit > 0 && limit < stmt.rowCount) {
                    // result limit exceeded, throw an exception
                    throw stmt.handle(
                        MondrianResource.instance().MemberFetchLimitExceeded.ex(
                            limit));
                }

                int column = 0;
                RolapMember member = root;
                for (RolapLevel level : levels) {
                    if (level.isAll()) {
                        continue;
                    }
                    Object value = accessors.get(column).get();
                    if (value == null) {
                        value = RolapUtil.sqlNullValue;
                    }
                    RolapMember parent = member;
                    MemberKey key = new MemberKey(parent, value);
                    member = map.get(key);
                    if (member == null) {
                        RolapMemberBase memberBase =
                            new RolapMemberBase(parent, level, value);
                        memberBase.setOrdinal(lastOrdinal++);
                        member = memberBase;
/*
RME is this right
                        if (level.getOrdinalExp() != level.getKeyExp()) {
                            member.setOrdinal(lastOrdinal++);
                        }
*/
                        if (value == RolapUtil.sqlNullValue) {
                            addAsOldestSibling(list, member);
                        } else {
                            list.add(member);
                        }
                        map.put(key, member);
                    }
                    column++;

                    // REVIEW jvs 20-Feb-2007:  What about caption?

                    if (!level.getOrdinalExp().equals(level.getKeyExp())) {
                        if (assignOrderKeys) {
                            Object orderKey = accessors.get(column).get();
                            setOrderKey((RolapMemberBase) member, orderKey);
                        }
                        column++;
                    }

                    Property[] properties = level.getProperties();
                    for (Property property : properties) {
                        /* REVIEW emcdermid 9-Jul-2009:
                         * Should we also look up the value in the
                         * pool here, rather than setting it directly?
                         * Presumably the value is already in the pool
                         * as a result of makeMember().
                         */
                        member.setProperty(
                            property.getName(),
                            accessors.get(column).get());
                        column++;
                    }
                }
            }

            return list;
        } catch (SQLException e) {
            throw stmt.handle(e);
        } finally {
            stmt.close();
        }
    }

    private void setOrderKey(RolapMemberBase member, Object orderKey) {
        if ((orderKey != null) && !(orderKey instanceof Comparable)) {
            orderKey = orderKey.toString();
        }
        member.setOrderKey((Comparable) orderKey);
    }

    /**
     * Adds <code>member</code> just before the first element in
     * <code>list</code> which has the same parent.
     */
    private void addAsOldestSibling(
        List<RolapMember> list,
        RolapMember member)
    {
        int i = list.size();
        while (--i >= 0) {
            RolapMember sibling = list.get(i);
            if (sibling.getParentMember() != member.getParentMember()) {
                break;
            }
        }
        list.add(i + 1, member);
    }

    private String makeKeysSql(DataSource dataSource) {
        SqlQuery sqlQuery =
            SqlQuery.newQuery(
                dataSource,
                "while generating query to retrieve members of " + hierarchy);
        RolapLevel[] levels = (RolapLevel[]) hierarchy.getLevels();
        for (RolapLevel level : levels) {
            if (level.isAll()) {
                continue;
            }
            MondrianDef.Expression exp = level.getKeyExp();
            hierarchy.addToFrom(sqlQuery, exp);
            String expString = exp.getExpression(sqlQuery);
            sqlQuery.addSelectGroupBy(expString);
            exp = level.getOrdinalExp();
            hierarchy.addToFrom(sqlQuery, exp);
            expString = exp.getExpression(sqlQuery);
            sqlQuery.addOrderBy(expString, true, false, true);
            if (!exp.equals(level.getKeyExp())) {
                sqlQuery.addSelect(expString);
            }

            RolapProperty[] properties = level.getProperties();
            for (RolapProperty property : properties) {
                exp = property.getExp();
                hierarchy.addToFrom(sqlQuery, exp);
                expString = exp.getExpression(sqlQuery);
                String alias = sqlQuery.addSelect(expString);
                // Some dialects allow us to eliminate properties from the
                // group by that are functionally dependent on the level value
                if (!sqlQuery.getDialect().allowsSelectNotInGroupBy()
                    || !property.dependsOnLevelValue())
                {
                    sqlQuery.addGroupBy(expString, alias);
                }
            }
        }
        return sqlQuery.toString();
    }

    // implement MemberReader
    public List<RolapMember> getMembersInLevel(
        RolapLevel level,
        int startOrdinal,
        int endOrdinal)
    {
        TupleConstraint constraint =
            sqlConstraintFactory.getLevelMembersConstraint(null);
        return getMembersInLevel(level, startOrdinal, endOrdinal, constraint);
    }

    public List<RolapMember> getMembersInLevel(
        RolapLevel level,
        int startOrdinal,
        int endOrdinal,
        TupleConstraint constraint)
    {
        if (level.isAll()) {
            final List<RolapMember> list = new ArrayList<RolapMember>();
            list.add(hierarchy.getAllMember());
                //return Collections.singletonList(hierarchy.getAllMember());
            return list;
        }
        return getMembersInLevel(level, constraint);
    }

    private List<RolapMember> getMembersInLevel(
        RolapLevel level,
        TupleConstraint constraint)
    {
        final TupleReader tupleReader =
            level.getDimension().isHighCardinality()
                ? new HighCardSqlTupleReader(constraint)
                : new SqlTupleReader(constraint);
        tupleReader.addLevelMembers(level, this, null);
        final List<RolapMember[]> tupleList =
            tupleReader.readTuples(dataSource, null, null);

        return new AbstractList<RolapMember>() {
            public RolapMember get(final int index) {
                return tupleList.get(index)[0];
            }

            public int size() {
                return tupleList.size();
            }

            public mondrian.rolap.RolapMember[] toArray() {
                final List<Member> l = new ArrayList<Member>();
                for (final RolapMember[] tuple : tupleList) {
                    l.add(tuple[0]);
                }
                return l.toArray(new RolapMember[l.size()]);
            }

            public <T> T[] toArray(T[] pattern) {
                return (T[]) toArray();
            }

            public Iterator<RolapMember> iterator() {
                final Iterator<RolapMember[]> it = tupleList.iterator();
                return new Iterator<RolapMember>() {
                    public boolean hasNext() {
                        return it.hasNext();
                    }
                    public RolapMember next() {
                        return it.next()[0];
                    }
                    public void remove() {
                        it.remove();
                    }
                };
            }
        };
    }

    public MemberCache getMemberCache() {
        return cache;
    }

    public Object getMemberCacheLock() {
        return cache;
    }

    // implement MemberSource
    public List<RolapMember> getRootMembers() {
        return getMembersInLevel(
            (RolapLevel) hierarchy.getLevels()[0],
            0,
            Integer.MAX_VALUE);
    }

    /**
     * Generates the SQL statement to access the children of
     * <code>member</code>. For example, <blockquote>
     *
     * <pre>SELECT "city"
     * FROM "customer"
     * WHERE "country" = 'USA'
     * AND "state_province" = 'BC'
     * GROUP BY "city"</pre>
     * </blockquote> retrieves the children of the member
     * <code>[Canada].[BC]</code>.
     * <p>Note that this method is never called in the context of
     * virtual cubes, it is only called on regular cubes.
     *
     * <p>See also {@link SqlTupleReader#makeLevelMembersSql}.
     */
    String makeChildMemberSql(
        RolapMember member,
        DataSource dataSource,
        MemberChildrenConstraint constraint)
    {
        SqlQuery sqlQuery =
            SqlQuery.newQuery(
                dataSource,
                "while generating query to retrieve children of member "
                    + member);

        // If this is a non-empty constraint, it is more efficient to join to
        // an aggregate table than to the fact table. See whether a suitable
        // aggregate table exists.
        AggStar aggStar = chooseAggStar(constraint, member);

        // Create the condition, which is either the parent member or
        // the full context (non empty).
        constraint.addMemberConstraint(sqlQuery, null, aggStar, member);

        RolapLevel level = (RolapLevel) member.getLevel().getChildLevel();

        boolean levelCollapsed =
            (aggStar != null)
            && isLevelCollapsed(aggStar, (RolapCubeLevel)level);

        boolean multipleCols =
            SqlMemberSource.levelContainsMultipleColumns(level);

        if (levelCollapsed && !multipleCols) {
            // if this is a single column collapsed level, there is
            // no need to join it with dimension tables
            RolapStar.Column starColumn =
                ((RolapCubeLevel) level).getStarKeyColumn();
            int bitPos = starColumn.getBitPosition();
            AggStar.Table.Column aggColumn = aggStar.lookupColumn(bitPos);
            String q = aggColumn.generateExprString(sqlQuery);
            sqlQuery.addSelectGroupBy(q);
            sqlQuery.addOrderBy(q, true, false, true);
            aggColumn.getTable().addToFrom(sqlQuery, false, true);
            return sqlQuery.toString();
        }

        hierarchy.addToFrom(sqlQuery, level.getKeyExp());
        String q = level.getKeyExp().getExpression(sqlQuery);
        sqlQuery.addSelectGroupBy(q);

        // in non empty mode the level table must be joined to the fact
        // table
        constraint.addLevelConstraint(sqlQuery, null, aggStar, level);

        if (levelCollapsed) {
            // if this is a collapsed level, add a join between key and aggstar
            RolapStar.Column starColumn =
                ((RolapCubeLevel) level).getStarKeyColumn();
            int bitPos = starColumn.getBitPosition();
            AggStar.Table.Column aggColumn = aggStar.lookupColumn(bitPos);
            RolapStar.Condition condition =
                new RolapStar.Condition(
                        level.getKeyExp(),
                        aggColumn.getExpression());
            sqlQuery.addWhere(condition.toString(sqlQuery));
            hierarchy.addToFromInverse(sqlQuery, level.getKeyExp());

            // also may need to join parent levels to make selection unique
            RolapCubeLevel parentLevel = (RolapCubeLevel)level.getParentLevel();
            boolean isUnique = level.isUnique();
            while (parentLevel != null && !parentLevel.isAll() && !isUnique) {
                hierarchy.addToFromInverse(sqlQuery, parentLevel.getKeyExp());
                starColumn = parentLevel.getStarKeyColumn();
                bitPos = starColumn.getBitPosition();
                aggColumn = aggStar.lookupColumn(bitPos);
                condition =
                    new RolapStar.Condition(
                            parentLevel.getKeyExp(),
                            aggColumn.getExpression());
                sqlQuery.addWhere(condition.toString(sqlQuery));
                parentLevel = parentLevel.getParentLevel();
            }
        }

        if (level.hasCaptionColumn()) {
            MondrianDef.Expression captionExp = level.getCaptionExp();
            if (!levelCollapsed) {
                hierarchy.addToFrom(sqlQuery, captionExp);
            }
            String captionSql = captionExp.getExpression(sqlQuery);
            sqlQuery.addSelectGroupBy(captionSql);
        }
        if (!levelCollapsed) {
            hierarchy.addToFrom(sqlQuery, level.getOrdinalExp());
        }
        String orderBy = level.getOrdinalExp().getExpression(sqlQuery);
        sqlQuery.addOrderBy(orderBy, true, false, true);
        if (!orderBy.equals(q)) {
            sqlQuery.addSelectGroupBy(orderBy);
        }

        RolapProperty[] properties = level.getProperties();
        for (RolapProperty property : properties) {
            final MondrianDef.Expression exp = property.getExp();
            if (!levelCollapsed) {
                hierarchy.addToFrom(sqlQuery, exp);
            }
            final String s = exp.getExpression(sqlQuery);
            String alias = sqlQuery.addSelect(s);
            // Some dialects allow us to eliminate properties from the
            // group by that are functionally dependent on the level value
            if (!sqlQuery.getDialect().allowsSelectNotInGroupBy()
                || !property.dependsOnLevelValue())
            {
                sqlQuery.addGroupBy(s, alias);
            }
        }
        return sqlQuery.toString();
    }

    private static AggStar chooseAggStar(
        MemberChildrenConstraint constraint,
        RolapMember member)
    {
        if (!MondrianProperties.instance().UseAggregates.get()
                || !(constraint instanceof SqlContextConstraint))
        {
            return null;
        }
        SqlContextConstraint contextConstraint =
                (SqlContextConstraint) constraint;
        Evaluator evaluator = contextConstraint.getEvaluator();
        RolapCube cube = (RolapCube) evaluator.getCube();
        RolapStar star = cube.getStar();
        final int starColumnCount = star.getColumnCount();
        BitKey measureBitKey = BitKey.Factory.makeBitKey(starColumnCount);
        BitKey levelBitKey = BitKey.Factory.makeBitKey(starColumnCount);

        // Convert global ordinal to cube based ordinal (the 0th dimension
        // is always [Measures])
        final Member[] members = evaluator.getNonAllMembers();

        // if measure is calculated, we can't continue
        if (!(members[0] instanceof RolapBaseCubeMeasure)) {
            return null;
        }
        RolapBaseCubeMeasure measure = (RolapBaseCubeMeasure)members[0];
        // we need to do more than this!  we need the rolap star ordinal, not
        // the rolap cube

        int bitPosition =
            ((RolapStar.Measure)measure.getStarMeasure()).getBitPosition();

        int ordinal = measure.getOrdinal();

        // childLevel will always end up being a RolapCubeLevel, but the API
        // calls into this method can be both shared RolapMembers and
        // RolapCubeMembers so this cast is necessary for now. Also note that
        // this method will never be called in the context of a virtual cube
        // so baseCube isn't necessary for retrieving the correct column

        // get the level using the current depth
        RolapCubeLevel childLevel =
            (RolapCubeLevel) member.getLevel().getChildLevel();

        RolapStar.Column column = childLevel.getStarKeyColumn();

        // set a bit for each level which is constrained in the context
        final CellRequest request =
            RolapAggregationManager.makeRequest(members);
        if (request == null) {
            // One or more calculated members. Cannot use agg table.
            return null;
        }
        // TODO: RME why is this using the array of constrained columns
        // from the CellRequest rather than just the constrained columns
        // BitKey (method getConstrainedColumnsBitKey)?
        RolapStar.Column[] columns = request.getConstrainedColumns();
        for (RolapStar.Column column1 : columns) {
            levelBitKey.set(column1.getBitPosition());
        }

        // set the masks
        levelBitKey.set(column.getBitPosition());
        measureBitKey.set(bitPosition);

        // find the aggstar using the masks
        AggStar aggStar = AggregationManager.instance().findAgg(
                star, levelBitKey, measureBitKey, new boolean[]{ false });

        return aggStar;
    }

    /**
     * Determine if a level contains more than a single column for its
     * data, such as an ordinal column or property column
     *
     * @param level the level to check
     * @return true if multiple relational columns are involved in this level
     */
    public static boolean levelContainsMultipleColumns(RolapLevel level) {
        if (level.isAll()) {
            return false;
        }
        MondrianDef.Expression keyExp = level.getKeyExp();
        MondrianDef.Expression ordinalExp = level.getOrdinalExp();
        MondrianDef.Expression captionExp = level.getCaptionExp();

        if (!keyExp.equals(ordinalExp)) {
            return true;
        }

        if (captionExp != null && !keyExp.equals(captionExp)) {
            return true;
        }

        RolapProperty[] properties = level.getProperties();
        for (RolapProperty property : properties) {
            if (!property.getExp().equals(keyExp)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determine if the given aggregate table has the dimension level
     * specified within in (AggStar.FactTable) it, aka collapsed,
     * or associated with foreign keys (AggStar.DimTable)
     *
     * @param aggStar aggregate star if exists
     * @param level level
     * @return true if agg table has level or not
     */
    public static boolean isLevelCollapsed(
            AggStar aggStar,
            RolapCubeLevel level)
    {
        boolean levelCollapsed = false;
        if (level.isAll()) {
            return levelCollapsed;
        }
        RolapStar.Column starColumn = level.getStarKeyColumn();
        int bitPos = starColumn.getBitPosition();
        AggStar.Table.Column aggColumn = aggStar.lookupColumn(bitPos);
        if (aggColumn.getTable() instanceof AggStar.FactTable) {
            levelCollapsed = true;
        }
        return levelCollapsed;
    }

    public void getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children)
    {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(null);
        getMemberChildren(parentMembers, children, constraint);
    }

    public void getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children,
        MemberChildrenConstraint mcc)
    {
        // try to fetch all children at once
        RolapLevel childLevel =
            getCommonChildLevelForDescendants(parentMembers);
        if (childLevel != null) {
            TupleConstraint lmc =
                sqlConstraintFactory.getDescendantsConstraint(
                    parentMembers, mcc);
            List<RolapMember> list =
                getMembersInLevel(childLevel, 0, Integer.MAX_VALUE, lmc);
            children.addAll(list);
            return;
        }

        // fetch them one by one
        for (RolapMember parentMember : parentMembers) {
            getMemberChildren(parentMember, children, mcc);
        }
    }

    public void getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children)
    {
        MemberChildrenConstraint constraint =
            sqlConstraintFactory.getMemberChildrenConstraint(null);
        getMemberChildren(parentMember, children, constraint);
    }

    public void getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        // allow parent child calculated members through
        // this fixes the non closure parent child hierarchy bug
        if (!parentMember.isAll()
            && parentMember.isCalculated()
            && !parentMember.getLevel().isParentChild())
        {
            return;
        }
        getMemberChildren2(parentMember, children, constraint);
    }

    /**
     * If all parents belong to the same level and no parent/child is involved,
     * returns that level; this indicates that all member children can be
     * fetched at once. Otherwise returns null.
     */
    private RolapLevel getCommonChildLevelForDescendants(
        List<RolapMember> parents)
    {
        // at least two members required
        if (parents.size() < 2) {
            return null;
        }
        RolapLevel parentLevel = null;
        RolapLevel childLevel = null;
        for (RolapMember member : parents) {
            // we can not fetch children of calc members
            if (member.isCalculated()) {
                return null;
            }
            // first round?
            if (parentLevel == null) {
                parentLevel = member.getLevel();
                // check for parent/child
                if (parentLevel.isParentChild()) {
                    return null;
                }
                childLevel = (RolapLevel) parentLevel.getChildLevel();
                if (childLevel == null) {
                    return null;
                }
                if (childLevel.isParentChild()) {
                    return null;
                }
            } else if (parentLevel != member.getLevel()) {
                return null;
            }
        }
        return childLevel;
    }

    private void getMemberChildren2(
        RolapMember parentMember,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        String sql;
        boolean parentChild;
        final RolapLevel parentLevel = parentMember.getLevel();
        RolapLevel childLevel;
        if (parentLevel.isParentChild()) {
            sql = makeChildMemberSqlPC(parentMember);
            parentChild = true;
            childLevel = parentLevel;
        } else {
            childLevel = (RolapLevel) parentLevel.getChildLevel();
            if (childLevel == null) {
                // member is at last level, so can have no children
                return;
            }
            if (childLevel.isParentChild()) {
                sql = makeChildMemberSql_PCRoot(parentMember);
                parentChild = true;
            } else {
                sql = makeChildMemberSql(parentMember, dataSource, constraint);
                parentChild = false;
            }
        }
        SqlStatement stmt =
            RolapUtil.executeQuery(
                dataSource, sql, "SqlMemberSource.getMemberChildren",
                "while building member cache");
        try {
            int limit = MondrianProperties.instance().ResultLimit.get();
            boolean checkCacheStatus = true;

            final List<SqlStatement.Accessor> accessors = stmt.getAccessors();
            ResultSet resultSet = stmt.getResultSet();
            RolapMember parentMember2 =
                parentMember instanceof RolapCubeMember
                    ? ((RolapCubeMember) parentMember).getRolapMember()
                    : parentMember;
            while (resultSet.next()) {
                ++stmt.rowCount;
                if (limit > 0 && limit < stmt.rowCount) {
                    // result limit exceeded, throw an exception
                    throw MondrianResource.instance().MemberFetchLimitExceeded
                        .ex(limit);
                }

                Object value = accessors.get(0).get();
                if (value == null) {
                    value = RolapUtil.sqlNullValue;
                }
                Object captionValue;
                int columnOffset = 1;
                if (childLevel.hasCaptionColumn()) {
                    // The columnOffset needs to take into account
                    // the caption column if one exists
                    captionValue = accessors.get(columnOffset++).get();
                } else {
                    captionValue = null;
                }
                Object key = cache.makeKey(parentMember2, value);
                RolapMember member = cache.getMember(key, checkCacheStatus);
                checkCacheStatus = false; /* Only check the first time */
                if (member == null) {
                    member =
                        makeMember(
                            parentMember2, childLevel, value, captionValue,
                            parentChild, stmt, key, columnOffset);
                }
                if (value == RolapUtil.sqlNullValue) {
                    children.toArray();
                    addAsOldestSibling(children, member);
                } else {
                    children.add(member);
                }
            }
        } catch (SQLException e) {
            throw stmt.handle(e);
        } finally {
            stmt.close();
        }
    }

    public RolapMember makeMember(
        RolapMember parentMember,
        RolapLevel childLevel,
        Object value,
        Object captionValue,
        boolean parentChild,
        SqlStatement stmt,
        Object key,
        int columnOffset)
        throws SQLException
    {
        RolapMemberBase member =
            new RolapMemberBase(parentMember, childLevel, value);
        if (!childLevel.getOrdinalExp().equals(childLevel.getKeyExp())) {
            member.setOrdinal(lastOrdinal++);
        }
        if (captionValue != null) {
            member.setCaption(captionValue.toString());
        }
        if (parentChild) {
            // Create a 'public' and a 'data' member. The public member is
            // calculated, and its value is the aggregation of the data member
            // and all of the children. The children and the data member belong
            // to the parent member; the data member does not have any
            // children.
            final RolapParentChildMember parentChildMember =
                childLevel.hasClosedPeer()
                ? new RolapParentChildMember(
                    parentMember, childLevel, value, member)
                : new RolapParentChildMemberNoClosure(
                    parentMember, childLevel, value, member);

            member = parentChildMember;
        }
        Property[] properties = childLevel.getProperties();
        final List<SqlStatement.Accessor> accessors = stmt.getAccessors();
        if (!childLevel.getOrdinalExp().equals(childLevel.getKeyExp())) {
            if (assignOrderKeys) {
                Object orderKey = accessors.get(columnOffset).get();
                setOrderKey(member, orderKey);
            }
            ++columnOffset;
        }
        for (int j = 0; j < properties.length; j++) {
            Property property = properties[j];
            member.setProperty(
                property.getName(),
                getPooledValue(accessors.get(columnOffset + j).get()));
        }
        cache.putMember(key, member);
        return member;
    }

    public RolapMember allMember() {
        final RolapHierarchy rolapHierarchy =
            hierarchy instanceof RolapCubeHierarchy
                ? ((RolapCubeHierarchy) hierarchy).getRolapHierarchy()
                : hierarchy;
        return rolapHierarchy.getAllMember();
    }

    /**
     * <p>Looks up an object (and if needed, stores it) in a cached value pool.
     * This permits us to reuse references to an existing object rather than
     * create new references to what are essentially duplicates.  The intent
     * is to allow the duplicate object to be garbage collected earlier, thus
     * keeping overall memory requirements down.</p>
     *
     * <p>If
     * {@link mondrian.olap.MondrianProperties#SqlMemberSourceValuePoolFactoryClass}
     * is not set, then valuePool will be null and no attempt to cache the
     * value will be made.  The method will simply return the incoming
     * object reference.</p>
     *
     * @param incoming An object to look up.  Must be immutable in usage,
     *        even if not declared as such.
     * @return a reference to a cached object equal to the incoming object,
     *        or to the incoming object if either no cached object was found,
     *        or caching is disabled.
     */
    private Object getPooledValue(Object incoming) {
        if (valuePool == null) {
            return incoming;
        } else {
            Object ret = this.valuePool.get(incoming);
            if (ret != null) {
                return ret;
            } else {
                this.valuePool.put(incoming, incoming);
                return incoming;
            }
        }
    }

    /**
     * Generates the SQL to find all root members of a parent-child hierarchy.
     * For example, <blockquote>
     *
     * <pre>SELECT "employee_id"
     * FROM "employee"
     * WHERE "supervisor_id" IS NULL
     * GROUP BY "employee_id"</pre>
     * </blockquote> retrieves the root members of the <code>[Employee]</code>
     * hierarchy.
     *
     * <p>Currently, parent-child hierarchies may have only one level (plus the
     * 'All' level).
     */
    private String makeChildMemberSql_PCRoot(RolapMember member) {
        SqlQuery sqlQuery =
            SqlQuery.newQuery(
                dataSource,
                "while generating query to retrieve children of parent/child "
                + "hierarchy member " + member);
        Util.assertTrue(
            member.isAll(),
            "In the current implementation, parent/child hierarchies must "
            + "have only one level (plus the 'All' level).");

        RolapLevel level = (RolapLevel) member.getLevel().getChildLevel();

        Util.assertTrue(!level.isAll(), "all level cannot be parent-child");
        Util.assertTrue(
                level.isUnique(), "parent-child level '"
                + level + "' must be unique");

        hierarchy.addToFrom(sqlQuery, level.getParentExp());
        String parentId = level.getParentExp().getExpression(sqlQuery);
        StringBuilder condition = new StringBuilder(64);
        condition.append(parentId);
        if (level.getNullParentValue() == null
            || level.getNullParentValue().equalsIgnoreCase("NULL"))
        {
            condition.append(" IS NULL");
        } else {
            // Quote the value if it doesn't seem to be a number.
            try {
                Util.discard(Double.parseDouble(level.getNullParentValue()));
                condition.append(" = ");
                condition.append(level.getNullParentValue());
            } catch (NumberFormatException e) {
                condition.append(" = ");
                Util.singleQuoteString(level.getNullParentValue(), condition);
            }
        }
        sqlQuery.addWhere(condition.toString());
        hierarchy.addToFrom(sqlQuery, level.getKeyExp());
        String childId = level.getKeyExp().getExpression(sqlQuery);
        sqlQuery.addSelectGroupBy(childId);
        hierarchy.addToFrom(sqlQuery, level.getOrdinalExp());
        String orderBy = level.getOrdinalExp().getExpression(sqlQuery);
        sqlQuery.addOrderBy(orderBy, true, false, true);
        if (!orderBy.equals(childId)) {
            sqlQuery.addSelectGroupBy(orderBy);
        }

        RolapProperty[] properties = level.getProperties();
        for (RolapProperty property : properties) {
            final MondrianDef.Expression exp = property.getExp();
            hierarchy.addToFrom(sqlQuery, exp);
            final String s = exp.getExpression(sqlQuery);
            String alias = sqlQuery.addSelect(s);
            // Some dialects allow us to eliminate properties from the group by
            // that are functionally dependent on the level value
            if (!sqlQuery.getDialect().allowsSelectNotInGroupBy()
                || !property.dependsOnLevelValue())
            {
                sqlQuery.addGroupBy(s, alias);
            }
        }
        return sqlQuery.toString();
    }

    /**
     * Generates the SQL statement to access the children of
     * <code>member</code> in a parent-child hierarchy. For example,
     * <blockquote>
     *
     * <pre>SELECT "employee_id"
     * FROM "employee"
     * WHERE "supervisor_id" = 5</pre>
     * </blockquote> retrieves the children of the member
     * <code>[Employee].[5]</code>.
     *
     * <p>See also {@link SqlTupleReader#makeLevelMembersSql}.
     */
    private String makeChildMemberSqlPC(RolapMember member) {
        SqlQuery sqlQuery =
            SqlQuery.newQuery(
                dataSource,
                "while generating query to retrieve children of "
                + "parent/child hierarchy member " + member);
        RolapLevel level = member.getLevel();

        Util.assertTrue(!level.isAll(), "all level cannot be parent-child");
        Util.assertTrue(
                level.isUnique(), "parent-child level '"
                + level + "' must be unique");

        hierarchy.addToFrom(sqlQuery, level.getParentExp());
        String parentId = level.getParentExp().getExpression(sqlQuery);

        StringBuilder buf = new StringBuilder();
        sqlQuery.getDialect().quote(buf, member.getKey(), level.getDatatype());
        sqlQuery.addWhere(parentId, " = ", buf.toString());

        hierarchy.addToFrom(sqlQuery, level.getKeyExp());
        String childId = level.getKeyExp().getExpression(sqlQuery);
        sqlQuery.addSelectGroupBy(childId);
        hierarchy.addToFrom(sqlQuery, level.getOrdinalExp());
        String orderBy = level.getOrdinalExp().getExpression(sqlQuery);
        sqlQuery.addOrderBy(orderBy, true, false, true);
        if (!orderBy.equals(childId)) {
            sqlQuery.addSelectGroupBy(orderBy);
        }

        RolapProperty[] properties = level.getProperties();
        for (RolapProperty property : properties) {
            final MondrianDef.Expression exp = property.getExp();
            hierarchy.addToFrom(sqlQuery, exp);
            final String s = exp.getExpression(sqlQuery);
            String alias = sqlQuery.addSelect(s);
            // Some dialects allow us to eliminate properties from the group by
            // that are functionally dependent on the level value
            if (!sqlQuery.getDialect().allowsSelectNotInGroupBy()
                || !property.dependsOnLevelValue())
            {
                sqlQuery.addGroupBy(s, alias);
            }
        }
        return sqlQuery.toString();
    }

    // implement MemberReader
    public RolapMember getLeadMember(RolapMember member, int n) {
        throw new UnsupportedOperationException();
    }

    public void getMemberRange(
        RolapLevel level,
        RolapMember startMember,
        RolapMember endMember,
        List<RolapMember> memberList)
    {
        throw new UnsupportedOperationException();
    }

    public int compare(
        RolapMember m1,
        RolapMember m2,
        boolean siblingsAreEqual)
    {
        throw new UnsupportedOperationException();
    }


    public TupleReader.MemberBuilder getMemberBuilder() {
        return this;
    }

    public RolapMember getDefaultMember() {
        // we expected the CacheMemberReader to implement this
        throw new UnsupportedOperationException();
    }

    public RolapMember getMemberParent(RolapMember member) {
        throw new UnsupportedOperationException();
    }

    // ~ -- Inner classes ------------------------------------------------------

    /**
     * Member of a parent-child dimension which has a closure table.
     *
     * <p>When looking up cells, this member will automatically be converted
     * to a corresponding member of the auxiliary dimension which maps onto
     * the closure table.
     */
    private static class RolapParentChildMember extends RolapMemberBase {
        private final RolapMember dataMember;
        private int depth = 0;

        public RolapParentChildMember(
            RolapMember parentMember,
            RolapLevel childLevel,
            Object value,
            RolapMember dataMember)
        {
            super(parentMember, childLevel, value);
            this.dataMember = dataMember;
            this.depth = (parentMember != null)
                ? parentMember.getDepth() + 1
                : 0;
        }

        public Member getDataMember() {
            return dataMember;
        }

        /**
         * @return the members's depth
         * @see mondrian.olap.Member#getDepth()
         */
        public int getDepth() {
            return depth;
        }

        public int getOrdinal() {
            return dataMember.getOrdinal();
        }
    }

    /**
     * Member of a parent-child dimension which has no closure table.
     *
     * <p>This member is calculated. When you ask for its value, it returns
     * an expression which aggregates the values of its child members.
     * This calculation is very inefficient, and we can only support
     * aggregatable measures ("count distinct" is non-aggregatable).
     * Unfortunately it's the best we can do without a closure table.
     */
    private static class RolapParentChildMemberNoClosure
        extends RolapParentChildMember
    {

        public RolapParentChildMemberNoClosure(
            RolapMember parentMember,
            RolapLevel childLevel, Object value, RolapMember dataMember)
        {
            super(parentMember, childLevel, value, dataMember);
        }

        protected boolean computeCalculated(final MemberType memberType) {
            return true;
        }

        public Exp getExpression() {
            return getHierarchy().getAggregateChildrenExpression();
        }
    }

    /**
     * <p>Interface definition for the pluggable factory used to decide
     * which implementation of {@link java.util.Map} to use to pool
     * reusable values.</p>
     */
    public interface ValuePoolFactory {
        /**
         * <p>Create a new {@link java.util.Map} to be used to pool values.
         * The value pool permits us to reuse references to existing objects
         * rather than create new references to what are essentially duplicates
         * of the same object.  The intent is to allow the duplicate object
         * to be garbage collected earlier, thus keeping overall memory
         * requirements down.</p>
         *
         * @param source The {@link SqlMemberSource} in which values are
         * being pooled.
         * @return a new value pool map
         */
        Map<Object, Object> create(SqlMemberSource source);
    }

    /**
     * Default {@link mondrian.rolap.SqlMemberSource.ValuePoolFactory}
     * implementation, used if
     * {@link mondrian.olap.MondrianProperties#SqlMemberSourceValuePoolFactoryClass}
     * is not set.
     */
    public static final class NullValuePoolFactory
        implements ValuePoolFactory
    {
        /**
         * {@inheritDoc}
         * <p>This version returns null, meaning that
         * by default values will not be pooled.</p>
         *
         * @param source {@inheritDoc}
         * @return {@inheritDoc}
         */
        public Map<Object, Object> create(SqlMemberSource source) {
            return null;
        }
    }

    /**
     * <p>Creates the ValuePoolFactory which is in turn used
     * to create property-value maps for member properties.</p>
     *
     * <p>The name of the ValuePoolFactory is drawn from
     * {@link mondrian.olap.MondrianProperties#SqlMemberSourceValuePoolFactoryClass}
     * in mondrian.properties.  If unset, it defaults to
     * {@link mondrian.rolap.SqlMemberSource.NullValuePoolFactory}. </p>
     */
    public static final class ValuePoolFactoryFactory
        extends ObjectFactory.Singleton<ValuePoolFactory>
    {
        /**
         * Single instance of the <code>ValuePoolFactoryFactory</code>.
         */
        private static final ValuePoolFactoryFactory factory;
        static {
            factory = new ValuePoolFactoryFactory();
        }

        /**
         * Access the <code>ValuePoolFactory</code> instance.
         *
         * @return the <code>Map</code>.
         */
        public static ValuePoolFactory getValuePoolFactory() {
            return factory.getObject();
        }

        /**
         * The constructor for the <code>ValuePoolFactoryFactory</code>.
         * This passes the <code>ValuePoolFactory</code> class to the
         * <code>ObjectFactory</code> base class.
         */
        @SuppressWarnings({"unchecked"})
        private ValuePoolFactoryFactory() {
            super((Class) ValuePoolFactory.class);
        }

        protected StringProperty getStringProperty() {
            return MondrianProperties.instance()
               .SqlMemberSourceValuePoolFactoryClass;
        }

        protected ValuePoolFactory getDefault(
            Class[] parameterTypes,
            Object[] parameterValues)
            throws CreationException
        {
            return new NullValuePoolFactory();
        }
    }
}

// End SqlMemberSource.java
