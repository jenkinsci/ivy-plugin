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
    private Logger logger = Logger.getLogger(IvyMessageImpl.class.getName());
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
        logger.log(logLevel,msg);
    }


    public void rawlog(String msg, int level) {
        log (msg, level);
    }



    @Override
    protected void doEndProgress(String msg) {
        log (msg, Message.MSG_INFO);

    }

    @Override
    protected void doProgress() {
        log (".", Message.MSG_INFO);
    }

}
