/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */
package org.jboss.bqt.core.log;

import org.slf4j.LoggerFactory;

/**
 * Logger that delivers messages to a Log4J logger
 * 
 */
public class SLF4JLoggerImpl extends org.jboss.bqt.core.Logger {
    private final org.slf4j.Logger logger;

    public SLF4JLoggerImpl( String category ) {
        logger = LoggerFactory.getLogger(category);
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void warn( Throwable t,
                      String message,
                      Object... params ) {
        if (!isWarnEnabled()) return;
        if (t == null) {
            warn(message, params);
            return;
        }
        if (message == null) {
            logger.warn(null, t);
            return;
        }
        logger.warn(message, params, t);
    }

    @Override
    public void warn( String message,
                      Object... params ) {
        if (!isWarnEnabled()) return;
        if (message == null) return;
        logger.warn(message, params);
    }

    @Override
    public void debug( String message) {
        debug(message, (Object) null);
    }    

    /**
     * Log a message at the DEBUG level according to the specified format and (optional) parameters. The message should contain a
     * pair of empty curly braces for each of the parameter, which should be passed in the correct order. This method is efficient
     * and avoids superfluous object creation when the logger is disabled for the DEBUG level.
     * 
     * @param message the message string
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void debug( String message,
                       Object... params ) {
        if (!isDebugEnabled()) return;
        if (message == null) return;
        logger.debug(message, params);
    }

    /**
     * Log an exception (throwable) at the DEBUG level with an accompanying message. If the exception is null, then this method
     * calls {@link #debug(String, Object...)}.
     * 
     * @param t the exception (throwable) to log
     * @param message the message accompanying the exception
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void debug( Throwable t,
                       String message,
                       Object... params ) {
        if (!isDebugEnabled()) return;
        if (t == null) {
            debug(message, params);
            return;
        }
        if (message == null) {
            logger.debug(null, t);
            return;
        }
        logger.debug(message, params, t);
    }

    /**
     * Log a message at the ERROR level according to the specified format and (optional) parameters. The message should contain a
     * pair of empty curly braces for each of the parameter, which should be passed in the correct order. This method is efficient
     * and avoids superfluous object creation when the logger is disabled for the ERROR level.
     * 
     * @param message the message string
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void error( String message,
                       Object... params ) {
        if (!isErrorEnabled()) return;
        if (message == null) return;
        logger.error(message, params);
    }

    /**
     * Log an exception (throwable) at the ERROR level with an accompanying message. If the exception is null, then this method
     * calls {@link #error(String, Object...)}.
     * 
     * @param t the exception (throwable) to log
     * @param message the message accompanying the exception
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void error( Throwable t,
                       String message,
                       Object... params ) {
        if (!isErrorEnabled()) return;
        if (t == null) {
            error(message, params);
            return;
        }
        if (message == null) {
            logger.error(null, t);
            return;
        }
        logger.error(message, params, t);
    }
    
    @Override
    public void info( String message) {
    	info(message, (Object) null);
    }

    /**
     * Log a message at the INFO level according to the specified format and (optional) parameters. The message should contain a
     * pair of empty curly braces for each of the parameter, which should be passed in the correct order. This method is efficient
     * and avoids superfluous object creation when the logger is disabled for the INFO level.
     * 
     * @param message the message string
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void info( String message,
                      Object... params ) {
        if (!isInfoEnabled()) return;
        if (message == null) return;
        logger.info(message, params);
    }

    /**
     * Log an exception (throwable) at the INFO level with an accompanying message. If the exception is null, then this method
     * calls {@link #info(String, Object...)}.
     * 
     * @param t the exception (throwable) to log
     * @param message the message accompanying the exception
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void info( Throwable t,
                      String message,
                      Object... params ) {
        if (!isInfoEnabled()) return;
        if (t == null) {
            info(message, params);
            return;
        }
        if (message == null) {
            logger.info(null, t);
            return;
        }
        logger.info(message, params, t);
    }

    /**
     * Log a message at the TRACE level according to the specified format and (optional) parameters. The message should contain a
     * pair of empty curly braces for each of the parameter, which should be passed in the correct order. This method is efficient
     * and avoids superfluous object creation when the logger is disabled for the TRACE level.
     * 
     * @param message the message string
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void trace( String message,
                       Object... params ) {
        if (!isTraceEnabled()) return;
        if (message == null) return;
        logger.trace(message, params);
    }

    /**
     * Log an exception (throwable) at the TRACE level with an accompanying message. If the exception is null, then this method
     * calls {@link #trace(String, Object...)}.
     * 
     * @param t the exception (throwable) to log
     * @param message the message accompanying the exception
     * @param params the parameter values that are to replace the variables in the format string
     */
    @Override
    public void trace( Throwable t,
                       String message,
                       Object... params ) {
        if (!isTraceEnabled()) return;
        if (t == null) {
            this.trace(message, params);
            return;
        }
        if (message == null) {
            logger.trace(null, t);
            return;
        }
        logger.trace(message, params, t);
    }

}
