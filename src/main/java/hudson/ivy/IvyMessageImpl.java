package hudson.ivy;

import java.util.logging.Level;
import java.util.logging.Logger;

import fr.jayasoft.ivy.util.Message;
import fr.jayasoft.ivy.util.MessageImpl;
/**
 * This implements Ivy's MessageImpl. We log all Messages to java.util.loggin.
 * if we wouldn't provide an implementation, all messages go to err
 *
 */
public class IvyMessageImpl implements MessageImpl {
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

    public void progress() {
        log (".", Message.MSG_INFO);
    }

    public void rawlog(String msg, int level) {
        log (msg, level);
    }

    public void endProgress(String msg) {
        log (msg, Message.MSG_INFO);

    }

}
