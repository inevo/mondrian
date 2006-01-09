/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// (C) Copyright 1999-2005 Kana Software, Inc. and others.
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 21 April, 1999
*/

package mondrian.olap;

import mondrian.calc.Calc;
import mondrian.calc.ExpCompiler;
import mondrian.mdx.ResolvedFunCall;

import java.io.PrintWriter;

/**
 * <code>FunDef</code> is the definition of an MDX function. See also {@link
 * FunTable}.
 **/
public interface FunDef {
    /**
     * Returns the syntactic type of the function.
     */
    Syntax getSyntax();

    /**
     * Returns the name of this function.
     */
    String getName();

    /**
     * Returns the description of this function.
     */
    String getDescription();

    /**
     * Returns the {@link Category} code of the value returned by this
     * function.
     */
    int getReturnCategory();

    /**
     * Returns the types of the arguments of this function. Values are the same
     * as those returned by {@link Exp#getCategory()}. The 0<sup>th</sup>
     * argument of methods and properties are the object they are applied
     * to. Infix operators have two arguments, and prefix operators have one
     * argument.
     */
    int[] getParameterCategories();

    /**
     * Creates an expression which represents a call to this function with
     * a given set of arguments. The result is usually a {@link ResolvedFunCall} but
     * not always.
     */
    Exp createCall(Validator validator, Exp[] args);

    /**
     * Returns an English description of the signature of the function, for
     * example "&lt;Numeric Expression&gt; / &lt;Numeric Expression&gt;".
     */
    String getSignature();

    /**
     * Converts a function call into MDX source code.
     */
    void unparse(Exp[] args, PrintWriter pw);

    /**
     * Converts a call to this function into executable objects.
     *
     * <p>The result must implement the appropriate interface for the result
     * type. For example, a function which returns an integer must return
     * an object which implements {@link mondrian.calc.IntegerCalc}.
     */
    Calc compileCall(ResolvedFunCall call, ExpCompiler compiler);

}

// End FunDef.java
