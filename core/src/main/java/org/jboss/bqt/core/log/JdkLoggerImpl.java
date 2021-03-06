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

import java.util.logging.Logger;
import org.jboss.bqt.core.util.StringHelper;

/**
 * Logger that delivers messages to a JDK logger
 * 
 * @since 2.5
 */
public class JdkLoggerImpl extends org.jboss.bqt.core.Logger {

    private final java.util.logging.Logger logger;

    public JdkLoggerImpl( String category ) {
        logger = Logger.getLogger(category);
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    private void log( java.util.logging.Level level,
                      String message,
                      Throwable ex ) {
        if (logger.isLoggable(level)) {
            Throwable dummyException = new Throwable();
            StackTraceElement locations[] = dummyException.getStackTrace();
            String className = "unknown";
            String methodName = "unknown";
            int depth = 2;
            if (locations != null && locations.length > depth) {
                StackTraceElement caller = locations[depth];
                className = caller.getClassName();
                methodName = caller.getMethodName();
            }
            if (ex == null) {
                logger.logp(level, className, methodName, message);
            } else {
                logger.logp(level, className, methodName, message, ex);
            }
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isLoggable(java.util.logging.Level.FINER);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(java.util.logging.Level.FINE);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(java.util.logging.Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(java.util.logging.Level.WARNING);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(java.util.logging.Level.SEVERE);
    }
    
    @Override
    public void debug( String message) {
        log(java.util.logging.Level.FINE, StringHelper.createString(message, (Object[]) null), null);
    }    

    @Override
    public void debug( String message,
                       Object... params ) {
        log(java.util.logging.Level.FINE, StringHelper.createString(message, params), null);
    }

    @Override
    public void debug( Throwable t,
                       String message,
                       Object... params ) {
        log(java.util.logging.Level.FINE, StringHelper.createString(message, params), t);
    }

    @Override
    public void error( String message,
                       Object... params ) {
        log(java.util.logging.Level.SEVERE, StringHelper.createString(message, params), null);
    }

    @Override
    public void error( Throwable t,
                       String message,
                       Object... params ) {
        log(java.util.logging.Level.SEVERE, StringHelper.createString(message, params), t);
    }
    
    @Override
    public void info( String message) {
        log(java.util.logging.Level.INFO, StringHelper.createString(message, (Object[]) null), null);
    }    

    @Override
    public void info( String message,
                      Object... params ) {
        log(java.util.logging.Level.INFO, StringHelper.createString(message, params), null);
    }

    @Override
    public void info( Throwable t,
                      String message,
                      Object... params ) {
        log(java.util.logging.Level.INFO, StringHelper.createString(message, params), t);
    }

    @Override
    public void trace( String message,
                       Object... params ) {
        log(java.util.logging.Level.FINER, StringHelper.createString(message, params), null);
    }

    @Override
    public void trace( Throwable t,
                       String message,
                       Object... params ) {
        // TODO Auto-generated method stub
        log(java.util.logging.Level.FINER, StringHelper.createString(message, params), t);

    }

    @Override
    public void warn( String message,
                      Object... params ) {
        log(java.util.logging.Level.WARNING, StringHelper.createString(message, params), null);
    }

    @Override
    public void warn( Throwable t,
                      String message,
                      Object... params ) {
        log(java.util.logging.Level.WARNING, StringHelper.createString(message, params), t);

    }
}
