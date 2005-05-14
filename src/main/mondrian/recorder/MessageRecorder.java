/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2001-2005 Kana Software, Inc. and others.
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
//
// jhyde, 12 August, 2001
*/

package mondrian.rolap;

import mondrian.olap.MondrianException;

/** 
 * Implemetations of this type are used to record warnings and errors
 * during the processing of a task. Contexts can be added and removed.
 * This allows one to collect more than one warning/error, keep processing, 
 * and then the code that initiated the processing can determine what to do 
 * with the warnings/errors if they exist.
 * <p>
 * A typical usage might be:
 * <pre><code>
 *    void process(MessageRecorder msgRecorder) {
 *      msgRecorder.pushContextName(getName());
 *      try {
 *          // prcess task
 *          ....
 *          // need to generate warning message
 *          String msg = ... 
 *          msgRecorder.reportWarning(msg);
 *          ....
 *      } finally {
 *          msgRecorder.popContextName();
 *      }
 *    }
 * <code></pre>
 * <p>
 * Implementations must provide the means for extracting the error/warning
 * messages.
 * <p>
 * Code that is processing should not catch the MessageRecorder.RTException.
 * This Exception is thrown by the MessageRecorder when too many errors have
 * been seen. Throwing this Exception is the mechanism used to stop processing
 * and return to the initiating code. The initiating code should expect to 
 * catch the MessageRecorder.RTException Exception.
 * <pre><code>
 *    void initiatingCode(MessageRecorder msgRecorder) {
 *      // get MessageRecorder implementation
 *      MessageRecorder msgRecorder = .... 
 *      try {
 *          processingCode(msgRecorder);
 *      } catch (MessageRecorder.RTException mrex) {
 *          // empty 
 *      }
 *      if (msgRecorder.hasErrors()) {
 *          // handle errors
 *      } else if (msgRecorder.hasWarnings()) {
 *          // handle warnings
 *      }
 *    }
 * <code></pre>
 * <p>
 * The reporting methods all have variations that take an "info" Object.
 * This can be used to pass something, beyond a text message, from the point 
 * of warning/error to the initiating code.
 * <p>
 * Concerning logging, it is a rule that a message, if logged by the code
 * creating the MessageRecorder implementation, is logged at is reporting level,
 * errors are logged at the error log level, warnings at the warning level and
 * info at the info level. This allows the client code to "know" what log level
 * their messages might appear at.
 * 
 * @author <a>Richard M. Emberson</a>
 * @version 
 */
public interface MessageRecorder  {
    
    /** 
     * Exception thrown by MessageRecorder when too many errors have been
     * reported.
     */
    public final class RTException extends MondrianException { 
         protected RTException(String msg) {
            super(msg);
        }
    }

    /** 
     * Clear all context, warnings and errors from the MessageRecorder. 
     * After calling this method the MessageRecorder implemenation should
     * be in the same state as if it were just constructed.
     */
    void clear();

    /** 
     * Get the time when the MessageRecorder was created or the last time that
     * the clear method was called. 
     * 
     * @return the start time
     */
    long getStartTimeMillis();

    /** 
     * How long the MessageRecorder has been running since it was created or the
     * last time clear was called.
     * 
     * @return 
     */
    long getRunTimeMillis();

    /** 
     * Returns true if there are one or more informational messages.  
     * 
     * @return true if there are one or more infos.
     */
    boolean hasInformation();

    /** 
     * Returns true if there are one or more warning messages.  
     * 
     * @return true if there are one or more warnings.
     */
    boolean hasWarnings();

    /** 
     * Returns true if there are one or more error messages.  
     * 
     * @return true if there are one or more errors.
     */
    boolean hasErrors();

    /** 
     * Get the current context string. 
     * 
     * @return the context string.
     */
    String getContext();

    /** 
     * Add the name parameter to the current context. 
     * 
     * @param name 
     */
    void pushContextName(final String name);
    
    /** 
     * Remove the last context name added. 
     */
    void popContextName();

    /** 
     * This simply throws a RTException. A client calls this if 1) there is one
     * or more error messages reported and 2) the client wishes to stop
     * processing. Implementations of this method should only throw the
     * RTException if there have been errors reported - if there are no errors,
     * then this method does nothing.
     * 
     * @throws RTException 
     */
    void throwRTException() throws RTException;

    /** 
     * Add an Exception.  
     * 
     * @param ex the Exception added.
     * @throws RTException if too many error messages have been added.
     */
    void reportError(final Exception ex) throws RTException;

    /** 
     * Add an Exception and extra informaton. 
     * 
     * @param ex the Exception added.
     * @param info extra information (not meant to be part of printed message)
     * @throws RTException if too many error messages have been added.
     */
    void reportError(final Exception ex, final Object info) throws RTException;

    /** 
     * Add an error message. 
     * 
     * @param msg  the text of the error message.
     * @throws RTException if too many error messages have been added.
     */
    void reportError(final String msg) throws RTException;

    /** 
     * Add an error message and extra information. 
     * 
     * @param msg  the text of the error message.
     * @param info extra information (not meant to be part of printed message)
     * @throws RTException if too many error messages have been added.
     */
    void reportError(final String msg, final Object info) throws RTException;

    /** 
     * Add a warning message. 
     * 
     * @param msg  the text of the warning message.
     */
    void reportWarning(final String msg);

    /** 
     * Add a warning message and extra information. 
     * 
     * @param msg  the text of the warning message.
     * @param info extra information (not meant to be part of printed message)
     */
    void reportWarning(final String msg, final Object info);

    /** 
     * Add an informational message. 
     * 
     * @param msg  the text of the info message.
     */
    void reportInfo(final String msg);

    /** 
     * Add an informational message  and extra information. 
     * 
     * @param msg  the text of the info message.
     * @param info extra information (not meant to be part of printed message)
     */
    void reportInfo(final String msg, final Object info);
}
