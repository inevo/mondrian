/*
// $Id$
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// Copyright (C) 2006-2009 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.olap.fun;

import mondrian.calc.Calc;
import mondrian.calc.ExpCompiler;
import mondrian.calc.StringCalc;
import mondrian.calc.impl.AbstractMemberCalc;
import mondrian.calc.impl.AbstractTupleCalc;
import mondrian.mdx.ResolvedFunCall;
import mondrian.mdx.DimensionExpr;
import mondrian.mdx.HierarchyExpr;
import mondrian.olap.*;
import mondrian.olap.type.*;
import mondrian.resource.MondrianResource;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of the <code>StrToTuple</code> MDX function.
 *
 * @author jhyde
 * @version $Id$
 * @since Mar 23, 2006
 */
class StrToTupleFunDef extends FunDefBase {
    static final ResolverImpl Resolver = new ResolverImpl();

    private StrToTupleFunDef(int[] parameterTypes) {
        super(
            "StrToTuple",
            null,
            "Constructs a tuple from a string.",
            Syntax.Function, Category.Tuple, parameterTypes);
    }

    public Calc compileCall(ResolvedFunCall call, ExpCompiler compiler) {
        final StringCalc stringCalc = compiler.compileString(call.getArg(0));
        Type elementType = call.getType();
        if (elementType instanceof MemberType) {
            final Hierarchy hierarchy = elementType.getHierarchy();
            return new AbstractMemberCalc(call, new Calc[] {stringCalc}) {
                public Member evaluateMember(Evaluator evaluator) {
                    String string = stringCalc.evaluateString(evaluator);
                    return parseMember(evaluator, string, hierarchy);
                }
            };
        } else {
            TupleType tupleType = (TupleType) elementType;
            final Hierarchy[] hierarchies =
                new Hierarchy[tupleType.elementTypes.length];
            for (int i = 0; i < tupleType.elementTypes.length; i++) {
                hierarchies[i] = tupleType.elementTypes[i].getHierarchy();
            }
            return new AbstractTupleCalc(call, new Calc[] {stringCalc}) {
                public Member[] evaluateTuple(Evaluator evaluator) {
                    String string = stringCalc.evaluateString(evaluator);
                    return parseTuple(evaluator, string, hierarchies);
                }
            };
        }
    }

    public Exp createCall(Validator validator, Exp[] args) {
        final int argCount = args.length;
        if (argCount <= 1) {
            throw MondrianResource.instance().MdxFuncArgumentsNum.ex(getName());
        }
        for (int i = 1; i < argCount; i++) {
            final Exp arg = args[i];
            if (arg instanceof DimensionExpr) {
                // if arg is a dimension, switch to dimension's default
                // hierarchy
                DimensionExpr dimensionExpr = (DimensionExpr) arg;
                Dimension dimension = dimensionExpr.getDimension();
                args[i] = new HierarchyExpr(dimension.getHierarchy());
            } else if (arg instanceof HierarchyExpr) {
                // nothing
            } else {
                throw MondrianResource.instance().MdxFuncNotHier.ex(
                    i + 1, getName());
            }
        }
        return super.createCall(validator, args);
    }

    public Type getResultType(Validator validator, Exp[] args) {
        switch (args.length) {
        case 1:
            // This is a call to the standard version of StrToTuple,
            // which doesn't give us any hints about type.
            return new TupleType(null);

        case 2:
            final Type argType = args[1].getType();
            return new MemberType(
                argType.getDimension(),
                argType.getHierarchy(),
                argType.getLevel(),
                null);

        default: {
            // This is a call to Mondrian's extended version of
            // StrToTuple, of the form
            //   StrToTuple(s, <Hier1>, ... , <HierN>)
            //
            // The result is a tuple
            //  (<Hier1>, ... ,  <HierN>)
            final List<MemberType> list = new ArrayList<MemberType>();
            for (int i = 1; i < args.length; i++) {
                Exp arg = args[i];
                final Type type = arg.getType();
                list.add(TypeUtil.toMemberType(type));
            }
            final MemberType[] types =
                list.toArray(new MemberType[list.size()]);
            TupleType.checkHierarchies(types);
            return new TupleType(types);
        }
        }
    }

    private static class ResolverImpl extends ResolverBase {
        ResolverImpl() {
            super(
                "StrToTuple",
                "StrToTuple(<String Expression>)",
                "Constructs a tuple from a string.",
                Syntax.Function);
        }

        public FunDef resolve(
                Exp[] args,
                Validator validator,
                List<Conversion> conversions)
        {
            if (args.length < 1) {
                return null;
            }
            Type type = args[0].getType();
            if (!(type instanceof StringType)) {
                return null;
            }
            for (int i = 1; i < args.length; i++) {
                Exp exp = args[i];
                if (!(exp instanceof DimensionExpr
                    || exp instanceof HierarchyExpr))
                {
                    return null;
                }
            }
            int[] argTypes = new int[args.length];
            argTypes[0] = Category.String;
            for (int i = 1; i < argTypes.length; i++) {
                argTypes[i] = Category.Hierarchy;
            }
            return new StrToTupleFunDef(argTypes);
        }

        public FunDef getFunDef() {
            return new StrToTupleFunDef(new int[] {Category.String});
        }
    }
}

// End StrToTupleFunDef.java
