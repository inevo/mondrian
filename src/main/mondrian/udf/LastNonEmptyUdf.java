/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2005-2007 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.udf;

import mondrian.olap.*;
import mondrian.olap.type.*;
import mondrian.rolap.RolapUtil;
import mondrian.spi.UserDefinedFunction;

import java.util.List;

/**
 * Definition of the user-defined function "LastNonEmpty".
 *
 * @author jhyde
 * @version $Id$
 */
public class LastNonEmptyUdf implements UserDefinedFunction {

    public String getName() {
        return "LastNonEmpty";
    }

    public String getDescription() {
        return "Returns the last member of a set whose value is not empty";
    }

    public Syntax getSyntax() {
        return Syntax.Function;
    }

    public Type getReturnType(Type[] parameterTypes) {
        // Return type is the same as the elements of the first parameter.
        // For example,
        //    LastNonEmpty({[Time].[1997], [Time].[1997].[Q1]},
        //                 [Measures].[Unit Sales])
        // will return a member of the [Time] dimension.
        SetType setType = (SetType) parameterTypes[0];
        MemberType memberType = (MemberType) setType.getElementType();
        return memberType;
    }

    public Type[] getParameterTypes() {
        return new Type[] {
            // The first argument must be a set of members (of any hierarchy).
            new SetType(MemberType.Unknown),
            // The second argument must be a member.
            MemberType.Unknown,
        };
    }

    public Object execute(Evaluator evaluator, Argument[] arguments) {
//System.out.println("LastNonEmpty.execute: TOP");
boolean b = evaluator.isNonEmpty();
try {
evaluator.setNonEmpty(false);
        final Argument memberListExp = arguments[0];
        final List memberList = (List) memberListExp.evaluate(evaluator);
        final Argument exp = arguments[1];
        int nullCount = 0;
        int missCount = 0;
        for (int i = memberList.size() - 1; i >= 0; --i) {
            Member member = (Member) memberList.get(i);
//System.out.println("LastNonEmpty.execute: member="+member);
            // Create an evaluator with the member as its context.
            Evaluator subEvaluator = evaluator.push(member);
            int missCountBefore = subEvaluator.getMissCount();
            final Object o = exp.evaluateScalar(subEvaluator);
            int missCountAfter = subEvaluator.getMissCount();
            if (Util.isNull(o)) {
                ++nullCount;
                continue;
            }
            if (missCountAfter > missCountBefore) {
                // There was a cache miss while evaluating the expression, so
                // the result is bogus. It would be a mistake to give up after
                // one cache miss, because then it would take us N
                // evaluate/fetch passes to move back through N members, which
                // is way too many.
                //
                // Carry on until we have seen as many misses as we have seen
                // null cells. The effect of this policy is that each pass
                // examines twice as many cells as the previous pass. Thus
                // we can move back through N members in log2(N) passes.
                ++missCount;
                if (missCount < 2*nullCount+1) {
                    continue;
                }
//System.out.println("LastNonEmpty.execute: AFTER missCountBefore="+missCountBefore + ", missCountAfter=" +missCountAfter +", nullCount="+nullCount);
            }
            if (o == RolapUtil.valueNotReadyException) {
                // Value is not in the cache yet, so we don't know whether
                // it will be empty. Carry on...
                continue;
            }
            if (o instanceof RuntimeException) {
                RuntimeException runtimeException = (RuntimeException) o;
                if (o == RolapUtil.valueNotReadyException) {
                    // Value is not in the cache yet, so we don't know whether
                    // it will be empty. Carry on...
                    continue;
                }
                return runtimeException;
            }
//System.out.println("LastNonEmpty.execute: RETURN member="+member);
            return member;
        }
        // Not found. Return the hierarchy's 'null member'.
        // It is possible that a MemberType has a Dimension but
        // no hierarchy, so we have to just get the type's Dimension's
        // default hierarchy and return it's null member.
        final Hierarchy hierarchy = memberListExp.getType().getHierarchy();
        return (hierarchy == null)
            ? memberListExp.getType().getDimension().
                getHierarchies()[0].getNullMember()
            : hierarchy.getNullMember();
} finally {
evaluator.setNonEmpty(b);
}
    }

    public String[] getReservedWords() {
        // This function does not require any reserved words.
        return null;
    }
}

// End LastNonEmptyUdf.java
