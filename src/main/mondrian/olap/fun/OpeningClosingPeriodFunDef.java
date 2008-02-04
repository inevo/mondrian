/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2002-2002 Kana Software, Inc.
// Copyright (C) 2002-2007 Julian Hyde and others
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 26 February, 2002
*/
package mondrian.olap.fun;

import mondrian.olap.*;
import mondrian.olap.type.MemberType;
import mondrian.olap.type.Type;
import mondrian.resource.MondrianResource;
import mondrian.calc.*;
import mondrian.calc.impl.AbstractMemberCalc;
import mondrian.calc.impl.DimensionCurrentMemberCalc;
import mondrian.mdx.ResolvedFunCall;

/**
 * Definition of the <code>OpeningPeriod</code> and <code>ClosingPeriod</code>
 * builtin functions.
 *
 * @author jhyde
 * @since 2005/8/14
 * @version $Id$
 */
class OpeningClosingPeriodFunDef extends FunDefBase {
    private final boolean opening;

    static final Resolver OpeningPeriodResolver = new MultiResolver(
            "OpeningPeriod",
            "OpeningPeriod([<Level>[, <Member>]])",
            "Returns the first descendant of a member at a level.",
            new String[] {"fm", "fml", "fmlm"}) {
        protected FunDef createFunDef(
                Exp[] args, FunDef dummyFunDef) {
            return new OpeningClosingPeriodFunDef(
                    dummyFunDef, true);
        }
    };

    static final Resolver ClosingPeriodResolver = new MultiResolver(
            "ClosingPeriod",
            "ClosingPeriod([<Level>[, <Member>]])",
            "Returns the last descendant of a member at a level.",
            new String[] {"fm", "fml", "fmlm", "fmm"}) {
        protected FunDef createFunDef(
                Exp[] args, FunDef dummyFunDef) {
            return new OpeningClosingPeriodFunDef(
                    dummyFunDef, false);
        }
    };

    public OpeningClosingPeriodFunDef(
            FunDef dummyFunDef,
            boolean opening) {
        super(dummyFunDef);
        this.opening = opening;
    }

    public Type getResultType(Validator validator, Exp[] args) {
        if (args.length == 0) {
            // With no args, the default implementation cannot
            // guess the hierarchy, so we supply the Time
            // dimension.
            Dimension defaultTimeDimension = 
                validator.getQuery().getCube().getTimeDimension();
            if (defaultTimeDimension == null) {
                throw MondrianResource.instance().
                            NoTimeDimensionInCube.ex(getName());
            }
            Hierarchy hierarchy = defaultTimeDimension
                    .getHierarchy();
            return MemberType.forHierarchy(hierarchy);
        }
        return super.getResultType(validator, args);
    }

    public Calc compileCall(ResolvedFunCall call, ExpCompiler compiler) {
        final Exp[] args = call.getArgs();
        final LevelCalc levelCalc;
        final MemberCalc memberCalc;
        Dimension defaultTimeDimension = null;
        switch (args.length) {
        case 0:
            defaultTimeDimension = 
                compiler.getEvaluator().getCube().getTimeDimension();
            if (defaultTimeDimension == null) {
                throw MondrianResource.instance().
                            NoTimeDimensionInCube.ex(getName());
            }
            memberCalc = new DimensionCurrentMemberCalc(defaultTimeDimension);
            levelCalc = null;
            break;
        case 1:
            defaultTimeDimension = 
                compiler.getEvaluator().getCube().getTimeDimension();
            if (defaultTimeDimension == null) {
                throw MondrianResource.instance().
                            NoTimeDimensionInCube.ex(getName());
            }
            levelCalc = compiler.compileLevel(call.getArg(0));
            memberCalc = new DimensionCurrentMemberCalc(defaultTimeDimension);
            break;
        default:
            levelCalc = compiler.compileLevel(call.getArg(0));
            memberCalc = compiler.compileMember(call.getArg(1));
            break;
        }

        // Make sure the member and the level come from the same dimension.
        if (levelCalc != null) {
            final Dimension memberDimension = memberCalc.getType().getDimension();
            final Dimension levelDimension = levelCalc.getType().getDimension();
            if (!memberDimension.equals(levelDimension)) {
                throw MondrianResource.instance().
                    FunctionMbrAndLevelHierarchyMismatch.ex(
                    opening ? "OpeningPeriod" : "ClosingPeriod",
                    levelDimension.getUniqueName(),
                    memberDimension.getUniqueName());
            }
        }
        return new AbstractMemberCalc(call, new Calc[] {levelCalc, memberCalc}) {
            public Member evaluateMember(Evaluator evaluator) {
                Member member = memberCalc.evaluateMember(evaluator);

                // If the level argument is present, use it. Otherwise use the
                // level immediately after that of the member argument.
                Level level;
                if (levelCalc == null) {
                    int targetDepth = member.getLevel().getDepth() + 1;
                    Level[] levels = member.getHierarchy().getLevels();

                    if (levels.length <= targetDepth) {
                        return member.getHierarchy().getNullMember();
                    }
                    level = levels[targetDepth];
                } else {
                    level = levelCalc.evaluateLevel(evaluator);
                }

                // Shortcut if the level is above the member.
                if (level.getDepth() < member.getLevel().getDepth()) {
                    return member.getHierarchy().getNullMember();
                }

                // Shortcut if the level is the same as the member
                if (level == member.getLevel()) {
                    return member;
                }

                return getDescendant(evaluator.getSchemaReader(), member,
                        level, opening);
            }
        };
    }

    /**
     * Returns the first or last descendant of the member at the target level.
     * This method is the implementation of both OpeningPeriod and ClosingPeriod.
     * @param schemaReader The schema reader to use to evaluate the function.
     * @param member The member from which the descendant is to be found.
     * @param targetLevel The level to stop at.
     * @param returnFirstDescendant Flag indicating whether to return the first
     * or last descendant of the member.
     * @return A member.
     * @pre member.getLevel().getDepth() < level.getDepth();
     */
    static Member getDescendant(
            SchemaReader schemaReader,
            Member member,
            Level targetLevel,
            boolean returnFirstDescendant) {
        Member[] children;

        final int targetLevelDepth = targetLevel.getDepth();
        assertPrecondition(member.getLevel().getDepth() < targetLevelDepth,
                "member.getLevel().getDepth() < targetLevel.getDepth()");

        for (;;) {
            children = schemaReader.getMemberChildren(member);

            if (children.length == 0) {
                return targetLevel.getHierarchy().getNullMember();
            }

            member = children[returnFirstDescendant ? 0 : (children.length - 1)];

            if (member.getLevel().getDepth() == targetLevelDepth) {
                if (member.isHidden()) {
                    return member.getHierarchy().getNullMember();
                } else {
                    return member;
                }
            }
        }
    }

}

// End OpeningClosingPeriodFunDef.java
