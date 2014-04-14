package com.github.t1.jms.browser;

import static javax.ws.rs.core.MediaType.*;

import java.net.URI;
import java.text.DateFormat;
import java.util.*;

import javax.jms.*;
import javax.jms.Queue;
import javax.naming.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Context;

import com.github.t1.html.*;

@Path("/")
public class QueuesResource {
    @Context
    UriInfo uriInfo;

    @GET
    @Produces(TEXT_HTML)
    public String queues() throws NamingException {
        List<Queue> queues = new ArrayList<>();
        scanJndiForQueues(queues, "");
        return queuesToHtml(queues);
    }

    private void scanJndiForQueues(List<Queue> out, String path) throws NamingException {
        InitialContext context = new InitialContext();
        Object resource = context.lookup(path);
        if (isSubContext(resource)) {
            NamingEnumeration<Binding> list = context.listBindings(path);
            while (list.hasMoreElements()) {
                Binding binding = list.nextElement();
                scanJndiForQueues(out, path + "/" + binding.getName());
            }
        } else if (resource instanceof Queue) {
            out.add((Queue) resource);
        } // else ignore Topics
    }

    private boolean isSubContext(Object object) {
        return javax.naming.Context.class.isAssignableFrom(object.getClass());
    }

    private URI uri(Queue queue) {
        UriBuilder messagePath = uriInfo.getBaseUriBuilder().path(QueuesResource.class, "queue");
        return messagePath.build(name(queue));
    }

    private String name(Queue queue) {
        try {
            return queue.getQueueName();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private String queuesToHtml(List<Queue> queues) {
        try (Html html = new Html("Queues")) {
            for (Queue queue : queues) {
                try (UL ul = html.ul()) {
                    try (LI li = ul.li()) {
                        try (A a = li.a().href(uri(queue))) {
                            a.print(name(queue));
                        }
                    }
                }
            }
            return html.toString();
        }
    }

    @GET
    @Path("{queue}")
    @Produces(TEXT_HTML)
    public String queue(@PathParam("queue") String queueName) {
        try (JmsSession session = new JmsSession()) {
            Queue queue = session.createQueue(queueName);
            QueueBrowser browser = session.createBrowser(queue);
            return messagesToHtml(browser);
        }
    }

    private String messagesToHtml(QueueBrowser browser) {
        try (Html html = new Html("Queue: " + browser.getQueue().getQueueName())) {
            int count = 0;
            try (UL ul = html.ul()) {
                @SuppressWarnings("unchecked")
                Enumeration<Message> enumeration = browser.getEnumeration();
                while (enumeration.hasMoreElements()) {
                    Message message = enumeration.nextElement();
                    count++;
                    try (LI li = ul.li()) {
                        try (A a = li.a().href(uri(message))) {
                            a.print(toString(message.getJMSTimestamp()));
                        }
                    }
                }
            }
            html.println("count: " + count);
            return html.toString();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private URI uri(Message message) throws JMSException {
        Queue queue = (Queue) message.getJMSDestination();
        UriBuilder messagePath = uriInfo.getBaseUriBuilder().path(QueuesResource.class, "message");
        return messagePath.build(name(queue), message.getJMSMessageID());
    }

    private String toString(long timestamp) {
        return DateFormat.getDateTimeInstance().format(new Date(timestamp));
    }

    @GET
    @Path("{queue}/{messageId}")
    @Produces(TEXT_HTML)
    public String message(@PathParam("queue") String queueName, @PathParam("messageId") String messageId) {
        Message message = getMessage(queueName, messageId);
        return messageToHtml(message);
    }

    private Message getMessage(String queueName, String messageId) {
        try (JmsSession session = new JmsSession()) {
            Queue queue = session.createQueue(queueName);
            return getMessage(session, queue, messageId);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private Message getMessage(JmsSession session, Queue queue, String messageId) throws JMSException {
        @SuppressWarnings("unchecked")
        Enumeration<Message> enumeration = session.createBrowser(queue).getEnumeration();
        while (enumeration.hasMoreElements()) {
            Message message = enumeration.nextElement();
            if (messageId.equals(message.getJMSMessageID())) {
                return message;
            }
        }
        throw new RuntimeException("message not found: " + messageId + " in " + name(queue));
    }

    private String messageToHtml(Message message) {
        try (Html html = new Html(name(message.getJMSDestination()) + ": " + message.getJMSMessageID())) {
            html.hr();
            printMessageHeader(message, html);
            html.hr();
            printMessageProperties(message, html);
            html.hr();
            printMessageBody(message, html);
            html.hr();
            return html.toString();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private String name(Destination destination) throws JMSException {
        if (destination instanceof Queue) {
            Queue queue = (Queue) destination;
            return queue.getQueueName();
        }
        if (destination instanceof Topic) {
            Topic topic = (Topic) destination;
            return topic.getTopicName();
        }
        throw new UnsupportedOperationException("unexpected destinaiton type: " + destination);
    }

    private void printMessageHeader(Message message, Html html) throws JMSException {
        field("correlationId", message.getJMSCorrelationID(), html);
        field("deliveryMode", message.getJMSDeliveryMode(), html);
        field("expiration", message.getJMSExpiration(), html);
        field("priority", message.getJMSPriority(), html);
        field("redelivered", message.getJMSRedelivered(), html);
        field("replyTo", message.getJMSReplyTo(), html);
        // field("timestamp", new Instant(message.getJMSTimestamp()), html);
        field("type", message.getJMSType(), html);
    }

    private void printMessageProperties(Message message, Html html) throws JMSException {
        @SuppressWarnings("unchecked")
        Enumeration<String> names = message.getPropertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            field(name, message.getStringProperty(name), html);
        }
    }

    private void printMessageBody(Message message, Html html) throws JMSException {
        if (message instanceof BytesMessage) {
            BytesMessage bytesMessage = (BytesMessage) message;
            html.println("bytes: " + bytesMessage.getBodyLength());
        } else if (message instanceof MapMessage) {
            MapMessage mapMessage = (MapMessage) message;
            @SuppressWarnings("unchecked")
            Enumeration<String> names = mapMessage.getMapNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                field(name, mapMessage.getString(name), html);
            }
        } else if (message instanceof ObjectMessage) {
            ObjectMessage objectMessage = (ObjectMessage) message;
            html.println("object: " + objectMessage.getObject());
        } else if (message instanceof StreamMessage) {
            html.println("stream");
        } else if (message instanceof TextMessage) {
            TextMessage textMessage = (TextMessage) message;
            html.printLiteral(textMessage.getText());
        } else {
            html.println("unsupported message body type in " + message.getJMSMessageID());
        }
    }

    private void field(String name, Object value, Html html) {
        if (value != null) {
            try (B b = html.b()) {
                b.print(name);
                b.print(": ");
            }
            html.print(value.toString());
            html.br();
        }
    }
}
