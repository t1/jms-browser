package com.github.t1.jms.browser;

import javax.jms.*;
import javax.naming.*;

/** {@link AutoCloseable} adapter for the JMS {@link Session}, hiding some {@link JMSException}s. */
public class JmsSession implements AutoCloseable {
    private final Session session;

    public JmsSession() {
        this.session = createSession();
    }

    private Session createSession() {
        try {
            ConnectionFactory factory = (ConnectionFactory) InitialContext.doLookup("ConnectionFactory");
            return factory.createConnection().createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (NamingException | JMSException e) {
            throw new RuntimeException(e);
        }
    }

    public QueueBrowser createBrowser(Queue queue) {
        try {
            return session.createBrowser(queue);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    public Queue createQueue(String queueName) {
        try {
            return session.createQueue(queueName);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}
