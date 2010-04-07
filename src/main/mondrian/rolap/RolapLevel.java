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
// jhyde, 10 August, 2001
*/

package mondrian.rolap;
import mondrian.olap.*;
import mondrian.resource.MondrianResource;
import mondrian.spi.Dialect;

import org.apache.log4j.Logger;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * <code>RolapLevel</code> implements {@link Level} for a ROLAP database.
 *
 * @author jhyde
 * @since 10 August, 2001
 * @version $Id$
 */
public class RolapLevel extends LevelBase {

    private static final Logger LOGGER = Logger.getLogger(RolapLevel.class);

    /**
     * The column or expression which yields the level's key.
     */
    protected MondrianDef.Expression keyExp;

    /**
     * The column or expression which yields the level's ordinal.
     */
    protected MondrianDef.Expression ordinalExp;

    /**
     * The column or expression which yields the level members' caption.
     */
    protected MondrianDef.Expression captionExp;

    private final Dialect.Datatype datatype;

    private final int flags;

    static final int FLAG_ALL = 0x02;

    /**
     * For SQL generator. Whether values of "column" are unique globally
     * unique (as opposed to unique only within the context of the parent
     * member).
     */
    static final int FLAG_UNIQUE = 0x04;

    private RolapLevel closedPeerLevel;

    private final RolapProperty[] properties;
    private final RolapProperty[] inheritedProperties;

    /**
     * Ths expression which gives the name of members of this level. If null,
     * members are named using the key expression.
     */
    protected MondrianDef.Expression nameExp;
    /** The expression which joins to the parent member in a parent-child
     * hierarchy, or null if this is a regular hierarchy. */
    protected MondrianDef.Expression parentExp;
    /** Value which indicates a null parent in a parent-child hierarchy. */
    private final String nullParentValue;

    /** Condition under which members are hidden. */
    private final HideMemberCondition hideMemberCondition;
    protected final MondrianDef.Closure xmlClosure;
    private final Map<String, Annotation> annotationMap;

    /**
     * Creates a level.
     *
     * @pre parentExp != null || nullParentValue == null
     * @pre properties != null
     * @pre levelType != null
     * @pre hideMemberCondition != null
     */
    RolapLevel(
        RolapHierarchy hierarchy,
        String name,
        String caption,
        String description,
        int depth,
        MondrianDef.Expression keyExp,
        MondrianDef.Expression nameExp,
        MondrianDef.Expression captionExp,
        MondrianDef.Expression ordinalExp,
        MondrianDef.Expression parentExp,
        String nullParentValue,
        MondrianDef.Closure xmlClosure,
        RolapProperty[] properties,
        int flags,
        Dialect.Datatype datatype,
        HideMemberCondition hideMemberCondition,
        LevelType levelType,
        String approxRowCount,
        Map<String, Annotation> annotationMap)
    {
        super(hierarchy, name, caption, description, depth, levelType);
        assert annotationMap != null;
        Util.assertPrecondition(properties != null, "properties != null");
        Util.assertPrecondition(
            hideMemberCondition != null,
            "hideMemberCondition != null");
        Util.assertPrecondition(levelType != null, "levelType != null");

        if (keyExp instanceof MondrianDef.Column) {
            checkColumn((MondrianDef.Column) keyExp);
        }
        this.annotationMap = annotationMap;
        this.approxRowCount = loadApproxRowCount(approxRowCount);
        this.flags = flags;
        this.datatype = datatype;
        this.keyExp = keyExp;
        if (nameExp != null) {
            if (nameExp instanceof MondrianDef.Column) {
                checkColumn((MondrianDef.Column) nameExp);
            }
        }
        this.nameExp = nameExp;
        if (captionExp != null) {
            if (captionExp instanceof MondrianDef.Column) {
                checkColumn((MondrianDef.Column) captionExp);
            }
        }
        this.captionExp = captionExp;
        if (ordinalExp != null) {
            if (ordinalExp instanceof MondrianDef.Column) {
                checkColumn((MondrianDef.Column) ordinalExp);
            }
            this.ordinalExp = ordinalExp;
        } else {
            this.ordinalExp = this.keyExp;
        }
        if (parentExp instanceof MondrianDef.Column) {
            checkColumn((MondrianDef.Column) parentExp);
        }
        this.parentExp = parentExp;
        if (parentExp != null) {
            Util.assertTrue(
                !isAll(),
                "'All' level '" + this + "' must not be parent-child");
            Util.assertTrue(
                isUnique(),
                "Parent-child level '" + this
                + "' must have uniqueMembers=\"true\"");
        }
        this.nullParentValue = nullParentValue;
        Util.assertPrecondition(
            parentExp != null || nullParentValue == null,
            "parentExp != null || nullParentValue == null");
        this.xmlClosure = xmlClosure;
        for (RolapProperty property : properties) {
            if (property.getExp() instanceof MondrianDef.Column) {
                checkColumn((MondrianDef.Column) property.getExp());
            }
        }
        this.properties = properties;
        List<Property> list = new ArrayList<Property>();
        for (Level level = this; level != null;
             level = level.getParentLevel())
        {
            final Property[] levelProperties = level.getProperties();
            for (final Property levelProperty : levelProperties) {
                Property existingProperty = lookupProperty(
                    list, levelProperty.getName());
                if (existingProperty == null) {
                    list.add(levelProperty);
                } else if (existingProperty.getType()
                    != levelProperty.getType())
                {
                    throw Util.newError(
                        "Property " + this.getName() + "."
                        + levelProperty.getName() + " overrides a "
                        + "property with the same name but different type");
                }
            }
        }
        this.inheritedProperties = list.toArray(new RolapProperty[list.size()]);

        Dimension dim = hierarchy.getDimension();
        if (dim.getDimensionType() == DimensionType.TimeDimension) {
            if (!levelType.isTime() && !isAll()) {
                throw MondrianResource.instance()
                    .NonTimeLevelInTimeHierarchy.ex(getUniqueName());
            }
        } else if (dim.getDimensionType() == null) {
            // there was no dimension type assigned to the dimension
            // - check later
        } else {
            if (levelType.isTime()) {
                throw MondrianResource.instance()
                    .TimeLevelInNonTimeHierarchy.ex(getUniqueName());
            }
        }
        this.hideMemberCondition = hideMemberCondition;
    }

    public RolapHierarchy getHierarchy() {
        return (RolapHierarchy) hierarchy;
    }

    public Map<String, Annotation> getAnnotationMap() {
        return annotationMap;
    }

    private int loadApproxRowCount(String approxRowCount) {
        boolean notNullAndNumeric =
            approxRowCount != null
                && approxRowCount.matches("^\\d+$");
        if (notNullAndNumeric) {
            return Integer.parseInt(approxRowCount);
        } else {
            // if approxRowCount is not set, return MIN_VALUE to indicate
            return Integer.MIN_VALUE;
        }
    }

    protected Logger getLogger() {
        return LOGGER;
    }

    String getTableName() {
        String tableName = null;

        MondrianDef.Expression expr = getKeyExp();
        if (expr instanceof MondrianDef.Column) {
            MondrianDef.Column mc = (MondrianDef.Column) expr;
            tableName = mc.getTableAlias();
        }
        return tableName;
    }

    public MondrianDef.Expression getKeyExp() {
        return keyExp;
    }

    MondrianDef.Expression getOrdinalExp() {
        return ordinalExp;
    }

    public MondrianDef.Expression getCaptionExp() {
        return captionExp;
    }

    public boolean hasCaptionColumn() {
        return captionExp != null;
    }

    final int getFlags() {
        return flags;
    }

    HideMemberCondition getHideMemberCondition() {
        return hideMemberCondition;
    }

    public final boolean isUnique() {
        return (flags & FLAG_UNIQUE) != 0;
    }

    final Dialect.Datatype getDatatype() {
        return datatype;
    }

    final String getNullParentValue() {
        return nullParentValue;
    }

    /**
     * Returns whether this level is parent-child.
     */
    public boolean isParentChild() {
        return parentExp != null;
    }

    MondrianDef.Expression getParentExp() {
        return parentExp;
    }

    // RME: this has to be public for two of the DrillThroughTest test.
    public
    MondrianDef.Expression getNameExp() {
        return nameExp;
    }

    private Property lookupProperty(List<Property> list, String propertyName) {
        for (Property property : list) {
            if (property.getName().equals(propertyName)) {
                return property;
            }
        }
        return null;
    }

    RolapLevel(
        RolapHierarchy hierarchy,
        int depth,
        MondrianDef.Level xmlLevel)
    {
        this(
            hierarchy,
            xmlLevel.name,
            xmlLevel.caption,
            xmlLevel.description,
            depth,
            xmlLevel.getKeyExp(),
            xmlLevel.getNameExp(),
            xmlLevel.getCaptionExp(),
            xmlLevel.getOrdinalExp(),
            xmlLevel.getParentExp(), xmlLevel.nullParentValue,
            xmlLevel.closure, createProperties(xmlLevel),
            (xmlLevel.uniqueMembers ? FLAG_UNIQUE : 0),
            xmlLevel.getDatatype(),
            HideMemberCondition.valueOf(xmlLevel.hideMemberIf),
            LevelType.valueOf(xmlLevel.levelType),
            xmlLevel.approxRowCount,
            RolapHierarchy.createAnnotationMap(xmlLevel.annotations));

        if (!Util.isEmpty(xmlLevel.caption)) {
            setCaption(xmlLevel.caption);
        }
        if (!Util.isEmpty(xmlLevel.formatter)) {
            // there is a special member formatter class
            try {
                Class<MemberFormatter> clazz =
                    (Class<MemberFormatter>) Class.forName(xmlLevel.formatter);
                Constructor<MemberFormatter> ctor = clazz.getConstructor();
                memberFormatter = ctor.newInstance();
            } catch (Exception e) {
                throw MondrianResource.instance().MemberFormatterLoadFailed.ex(
                    xmlLevel.formatter, getUniqueName(), e);
            }
        }
    }

    // helper for constructor
    private static RolapProperty[] createProperties(
        MondrianDef.Level xmlLevel)
    {
        List<RolapProperty> list = new ArrayList<RolapProperty>();
        final MondrianDef.Expression nameExp = xmlLevel.getNameExp();

        if (nameExp != null) {
            list.add(
                new RolapProperty(
                    Property.NAME.name, Property.Datatype.TYPE_STRING,
                    nameExp, null, null, null, true));
        }
        for (int i = 0; i < xmlLevel.properties.length; i++) {
            MondrianDef.Property property = xmlLevel.properties[i];
            list.add(
                new RolapProperty(
                    property.name,
                    convertPropertyTypeNameToCode(property.type),
                    xmlLevel.getPropertyExp(i),
                    property.formatter,
                    property.caption,
                    xmlLevel.properties[i].dependsOnLevelValue,
                    false));
        }
        return list.toArray(new RolapProperty[list.size()]);
    }

    private static Property.Datatype convertPropertyTypeNameToCode(
        String type)
    {
        if (type.equals("String")) {
            return Property.Datatype.TYPE_STRING;
        } else if (type.equals("Numeric")) {
            return Property.Datatype.TYPE_NUMERIC;
        } else if (type.equals("Boolean")) {
            return Property.Datatype.TYPE_BOOLEAN;
        } else {
            throw Util.newError("Unknown property type '" + type + "'");
        }
    }

    private void checkColumn(MondrianDef.Column nameColumn) {
        final RolapHierarchy rolapHierarchy = (RolapHierarchy) hierarchy;
        if (nameColumn.table == null) {
            final MondrianDef.Relation table = rolapHierarchy.getUniqueTable();
            if (table == null) {
                throw Util.newError(
                    "must specify a table for level " + getUniqueName()
                    + " because hierarchy has more than one table");
            }
            nameColumn.table = table.getAlias();
        } else {
            if (!rolapHierarchy.tableExists(nameColumn.table)) {
                throw Util.newError(
                    "Table '" + nameColumn.table + "' not found");
            }
        }
    }

    void init(MondrianDef.CubeDimension xmlDimension) {
        if (xmlClosure != null) {
            final RolapDimension dimension = ((RolapHierarchy) hierarchy)
                .createClosedPeerDimension(this, xmlClosure, xmlDimension);
            closedPeerLevel =
                    (RolapLevel) dimension.getHierarchies()[0].getLevels()[1];
        }
    }

    public final boolean isAll() {
        return (flags & FLAG_ALL) != 0;
    }

    public boolean areMembersUnique() {
        return (depth == 0) || (depth == 1) && hierarchy.hasAll();
    }

    public String getTableAlias() {
        return keyExp.getTableAlias();
    }

    public RolapProperty[] getProperties() {
        return properties;
    }

    public Property[] getInheritedProperties() {
        return inheritedProperties;
    }

    public int getApproxRowCount() {
        return approxRowCount;
    }

    /**
     * Conditions under which a level's members may be hidden (thereby creating
     * a <dfn>ragged hierarchy</dfn>).
     */
    public enum HideMemberCondition {
        /** A member always appears. */
        Never,

        /** A member doesn't appear if its name is null or empty. */
        IfBlankName,

        /** A member appears unless its name matches its parent's. */
        IfParentsName
    }

    public OlapElement lookupChild(SchemaReader schemaReader, Id.Segment name) {
        return lookupChild(schemaReader, name, MatchType.EXACT);
    }

    public OlapElement lookupChild(
        SchemaReader schemaReader, Id.Segment name, MatchType matchType)
    {
        List<Member> levelMembers = schemaReader.getLevelMembers(this, true);
        if (levelMembers.size() > 0) {
            Member parent = levelMembers.get(0).getParentMember();
            return
                RolapUtil.findBestMemberMatch(
                    levelMembers,
                    (RolapMember) parent,
                    this,
                    name,
                    matchType,
                    false);
        }
        return null;
    }

    /**
     * Indicates that level is not ragged and not a parent/child level.
     */
    public boolean isSimple() {
        // most ragged hierarchies are not simple -- see isTooRagged.
        if (isTooRagged()) {
            return false;
        }
        if (isParentChild()) {
            return false;
        }
        // does not work for measures
        if (isMeasure()) {
            return false;
        }
        return true;
    }

    /**
     * Determines whether the specified level is too ragged for native
     * evaluation, which is able to handle one special case of a ragged
     * hierarchy: when the level specified in the query is the leaf level of
     * the hierarchy and HideMemberCondition for the level is IfBlankName.
     * This is true even if higher levels of the hierarchy can be hidden
     * because even in that case the only column that needs to be read is the
     * column that holds the leaf. IfParentsName can't be handled even at the
     * leaf level because in the general case we aren't reading the column
     * that holds the parent. Also, IfBlankName can't be handled for non-leaf
     * levels because we would have to read the column for the next level
     * down for members with blank names.
     *
     * @return true if the specified level is too ragged for native
     *         evaluation.
     */
    private boolean isTooRagged() {
        // Is this the special case of raggedness that native evaluation
        // is able to handle?
        if (getDepth() == getHierarchy().getLevels().length - 1) {
            switch (getHideMemberCondition()) {
            case Never:
            case IfBlankName:
                return false;
            default:
                return true;
            }
        }
        // Handle the general case in the traditional way.
        return getHierarchy().isRagged();
    }


    /**
     * Returns true when the level is part of a parent/child hierarchy and has
     * an equivalent closed level.
     */
    boolean hasClosedPeer() {
        return closedPeerLevel != null;
    }

    public RolapLevel getClosedPeer() {
        return closedPeerLevel;
    }

    public static RolapLevel lookupLevel(
        RolapLevel[] levels,
        String levelName)
    {
        for (RolapLevel level : levels) {
            if (level.getName().equals(levelName)) {
                return level;
            }
        }
        return null;
    }
}

// End RolapLevel.java
