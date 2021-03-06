/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2005-2009 Julian Hyde and others
// Copyright (C) 2004-2005 SAS Institute, Inc.
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// sasebb, 16 December, 2004
*/
package mondrian.olap.fun;

import java.util.List;
import java.util.Collections;

import mondrian.olap.*;
import mondrian.calc.*;
import mondrian.calc.impl.AbstractListCalc;
import mondrian.mdx.ResolvedFunCall;


/**
 * Definition of the <code>NonEmptyCrossJoin</code> MDX function.
 *
 * @author jhyde
 * @version $Id$
 * @since Mar 23, 2006
 */
public class NonEmptyCrossJoinFunDef extends CrossJoinFunDef {
    static final ReflectiveMultiResolver Resolver = new ReflectiveMultiResolver(
            "NonEmptyCrossJoin",
            "NonEmptyCrossJoin(<Set1>, <Set2>)",
            "Returns the cross product of two sets, excluding empty tuples and tuples without associated fact table data.",
            new String[]{"fxxx"},
            NonEmptyCrossJoinFunDef.class);

    public NonEmptyCrossJoinFunDef(FunDef dummyFunDef) {
        super(dummyFunDef);
    }

    public Calc compileCall(final ResolvedFunCall call, ExpCompiler compiler) {
        final ListCalc listCalc1 = compiler.compileList(call.getArg(0));
        final ListCalc listCalc2 = compiler.compileList(call.getArg(1));
        return new AbstractListCalc(
            call, new Calc[] {listCalc1, listCalc2}, false)
        {
            public List evaluateList(Evaluator evaluator) {
                SchemaReader schemaReader = evaluator.getSchemaReader();
                // evaluate the arguments in non empty mode
                evaluator = evaluator.push(true);
                NativeEvaluator nativeEvaluator =
                    schemaReader.getNativeSetEvaluator(
                        call.getFunDef(), call.getArgs(), evaluator, this);
                if (nativeEvaluator != null) {
                    return (List) nativeEvaluator.execute(ResultStyle.LIST);
                }

                final List list1 = listCalc1.evaluateList(evaluator);
                if (list1.isEmpty()) {
                    return Collections.EMPTY_LIST;
                }
                final List list2 = listCalc2.evaluateList(evaluator);
                List<Member[]> result = crossJoin(list1, list2);

                // remove any remaining empty crossings from the result
                result = nonEmptyList(evaluator, result, call);
                return result;
            }

            public boolean dependsOn(Hierarchy hierarchy) {
                if (super.dependsOn(hierarchy)) {
                    return true;
                }
                // Member calculations generate members, which mask the actual
                // expression from the inherited context.
                if (listCalc1.getType().usesHierarchy(hierarchy, true)) {
                    return false;
                }
                if (listCalc2.getType().usesHierarchy(hierarchy, true)) {
                    return false;
                }
                // The implicit value expression, executed to figure out
                // whether a given tuple is empty, depends upon all dimensions.
                return true;
            }
        };
    }

}

// End NonEmptyCrossJoinFunDef.java
