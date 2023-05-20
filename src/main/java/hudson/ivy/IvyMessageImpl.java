/*
 * Copyright 2008-2011 Martin Ficker
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package hudson.ivy;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.ivy.util.AbstractMessageLogger;
import org.apache.ivy.util.Message;
import org.apache.ivy.util.MessageLogger;

/**
 * This implements Ivy's MessageLogger. We log all Messages to java.util.logging.
 * if we wouldn't provide an implementation, all messages go to err
 *
 */
public class IvyMessageImpl extends AbstractMessageLogger implements MessageLogger {
    private final Logger logger = Logger.getLogger(IvyMessageImpl.class.getName());

    @Override
    public void log(String msg, int level) {
        Level logLevel = Level.INFO;
        switch (level) {
            case Message.MSG_ERR:
                logLevel = Level.SEVERE;
                break;
            case Message.MSG_WARN:
                logLevel = Level.WARNING;
                break;
            case Message.MSG_INFO:
                logLevel = Level.INFO;
                break;
            case Message.MSG_VERBOSE:
                logLevel = Level.FINE;
                break;
            case Message.MSG_DEBUG:
                logLevel = Level.FINEST;
                break;
        }
        logger.log(logLevel, msg);
    }

    @Override
    public void rawlog(String msg, int level) {
        log(msg, level);
    }

    @Override
    protected void doEndProgress(String msg) {
        log(msg, Message.MSG_INFO);
    }

    @Override
    protected void doProgress() {
        log(".", Message.MSG_INFO);
    }
}
