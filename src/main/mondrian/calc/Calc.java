/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// (C) Copyright 2006-2006 Julian Hyde
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.calc;

import mondrian.olap.Evaluator;
import mondrian.olap.Dimension;
import mondrian.olap.type.Type;

/**
 * <code>Calc</code> is the base class for all calculable expressions.<p/>
 *
 * <h3>Logical and physical expression languages</h3>
 *
 * Mondrian has two expression languages:<ul>
 * <li>The logical language of parsed MDX fragments ({@link mondrian.olap.Exp}).
 * <li>The phyiscal language of compiled expressions ({@link Calc}).
 * </ul></p>
 *
 * The two languages allow us to separate logical (how an
 * MDX expression was specified) from physical (how it is to be evaluated).
 * The physical language is more strongly typed, and certain constructs which
 * are implicit in the logical language (such as the addition of calls
 * to the <code>&lt;Member&gt;.CurrentMember</code> function) are made
 * explicit in the physical language.<p/>
 *
 * <h3>Compilation</h3>
 *
 * Expressions are generally created from using an expression compiler
 * ({@link ExpCompiler}). There are often more than one evaluation strategy
 * for a given expression, and compilation process gives us an opportunity to
 * choose the optimal one.<p/>
 *
 * <h3>Implementing expressions</h3>
 *
 * The <code>Calc</code> interface has sub-interfaces for various types:
 * {@link IntegerCalc},
 * {@link BooleanCalc},
 * {@link DoubleCalc},
 * {@link StringCalc} are scalar expressions;
 * {@link MemberCalc},
 * {@link LevelCalc},
 * {@link HierarchyCalc},
 * {@link DimensionCalc} yield elements of the OLAP model.<p/>
 *
 * Each of these sub-interfaces has an abstract implementation:
 * {@link mondrian.calc.impl.AbstractIntegerCalc},
 * {@link mondrian.calc.impl.AbstractBooleanCalc},
 * {@link mondrian.calc.impl.AbstractDoubleCalc},
 * {@link mondrian.calc.impl.AbstractStringCalc},
 * {@link mondrian.calc.impl.AbstractMemberCalc},
 * {@link mondrian.calc.impl.AbstractLevelCalc},
 * {@link mondrian.calc.impl.AbstractHierarchyCalc},
 * {@link mondrian.calc.impl.AbstractDimensionCalc}.<p/>
 *
 * {@link mondrian.calc.impl.GenericCalc} is an adapter which implements all of these interfaces
 * and will try to convert any given result to the correct type. Use it
 * sparingly: if you know the expected result type, it is better to write a
 * class which implements a specific <code><em>Type</em>Calc</code> interface.
 *
 * @author jhyde
 * @since Sep 26, 2005
 * @version $Id$
 */
public interface Calc {
    /**
     * Evaluates this expression.
     *
     * @param evaluator Provides dimensional context in which to evaluate
     *                  this expression
     * @return Result of expression evaluation
     */
    Object evaluate(Evaluator evaluator);

    /**
     * Returns whether this expression depends upon a given dimension.<p/>
     *
     * If it does not depend on the dimension, then re-evaluating the
     * expression with a different member of this context must produce the
     * same answer.<p/>
     *
     * Some examples:<ul>
     *
     * <li>The expression
     * <blockquote><code>[Measures].[Unit Sales]</code></blockquote>
     * depends on all dimensions except <code>[Measures]</code>.
     *
     * <li>The boolean expression
     * <blockquote><code>([Measures].[Unit Sales],
     * [Time].[1997]) &gt; 1000</code></blockquote>
     * depends on all dimensions except [Measures] and [Time].
     *
     * <li>The list expression
     * <blockquote><code>Filter([Store].[USA].Children,
     * [Measures].[Unit Sales] &lt; 50)</code></pre></blockquote>
     * depends upon all dimensions <em>except</em> [Store] and [Measures].
     * How so? Normally the scalar expression would depend upon all dimensions
     * except [Measures], but the <code>Filter</code> function sets the [Store]
     * context before evaluating the scalar expression, so it is not inherited
     * from the surrounding context.
     *
     * </ul><p/>
     *
     * @param dimension Dimension
     * @return Whether this expression's result depends upon the current member
     *   of the dimension
     */
    boolean dependsOn(Dimension dimension);

    /**
     * Returns the type of this expression.
     */
    Type getType();

    /**
     * Prints this expression, by accepting a visiting {@link CalcWriter}.
     *
     * @param calcWriter Writer
     */
    void accept(CalcWriter calcWriter);
}

// End Calc.java
