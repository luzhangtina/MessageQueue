package com.example;

import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by tina on 2019/2/4.
 */
public class InMemoryQueue {
    private final ConcurrentLinkedQueue<Message> queue;

    /** The timer's period is 1 second
     *  The timer aims to find out messages which are not be deleted during visibleTimeout,
     *  Then push those messages in queue top again.
     *  Those messages will be allowed to be pulled by consumers again. */
    private Timer timer;
    private TimerTask timerTask;

    public InMemoryQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
    }

    public ConcurrentLinkedQueue<Message> getQueue() {
        return this.queue;
    }

    public int size() {
        ConcurrentLinkedQueue<Message> queue = getQueue();
        return queue.size();
    }

    /** Push the message in queue */
    public synchronized void push(Message message) {
        ConcurrentLinkedQueue<Message> queue = getQueue();

        /** If the queue is Empty, start the 1 second timer.
         *  The timer's aim is to scan the queue,
         *  then make the messages which are not be deleted during the visibleTimeout visibility again */
        if (queue.isEmpty()) {
            startTimer();
        }

        queue.offer(message);
    }

    /** Get First Message from queue */
    public synchronized Message pull(int visibilityTimeout) {
        ConcurrentLinkedQueue<Message> queue = getQueue();

        /** Find the first visible message from queue */
        Message messageFromQueue = null;
        Iterator<Message> iterator = queue.iterator();
        while (iterator.hasNext()) {
            messageFromQueue = iterator.next();
            if (messageFromQueue.getVisibleDate() == null) {
                break;
            }
        }

        /** If the queue is empty, or if there is no visible message, return null */
        if (messageFromQueue == null || messageFromQueue.getVisibleDate() != null) {
            return null;
        }

        /** Make the message invisible by setting the visible date of the message */
        Date date = QueueProperties.createVisibleDate(visibilityTimeout);
        messageFromQueue.setVisibleDate(date);
        return messageFromQueue;
    }

    /** If message with the specified receiptHandle is in the queue and invisible,
     *  delete the message, return true. Otherwise, return false */
    public synchronized boolean delete(String receiptHandle) {
        boolean result = false;
        ConcurrentLinkedQueue<Message> queue = getQueue();

        /** Find the invisible message which has the specified receiptHandle from queue */
        Message messageFromQueue;
        Iterator<Message> iterator = queue.iterator();
        while (iterator.hasNext()) {
            messageFromQueue = iterator.next();
            if (messageFromQueue.getVisibleDate() != null
                    && messageFromQueue.getReceiptHandle().equals(receiptHandle)) {
                    queue.remove(messageFromQueue);
                    result = true;
                    break;
            }
        }

        /** If the queue is empty, stop the timer */
        if (queue.isEmpty()) {
            stopTimer();
        }

        return result;
    }

    public void updateMsgIntoVisibleState() {
        ConcurrentLinkedQueue<Message> myQueue = getQueue();

        /** Find the invisible messages in the queue.
         *  if the message's invisible times out,
         *  make the message visible again by setting the visibleDate to null */
        Iterator<Message> iterator = myQueue.iterator();
        while (iterator.hasNext()) {
            Message myMessage = iterator.next();
            Date visibleDate = myMessage.getVisibleDate();
            if (visibleDate != null
                    && visibleDate.before(new Date())) {
                    myMessage.setVisibleDate(null);
            }
        }
    }

    private Timer getTimer() {
        return this.timer;
    }

    private TimerTask getTimerTask() {
        return this.timerTask;
    }

    private void setTimer(Timer timer) {
        this.timer = timer;
    }

    private void setTimerTask(TimerTask timerTask) {
        this.timerTask = timerTask;
    }

    private void startTimer() {
        Timer timer = getTimer();
        if (timer == null) {
            timer = new Timer();
            setTimer(timer);
        }

        TimerTask timerTask = getTimerTask();
        if (timerTask == null) {
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    updateMsgIntoVisibleState();
                }
            };
            setTimerTask(timerTask);
        }

        timer.schedule(timerTask, 0, 1000);
    }

    private void stopTimer() {
        TimerTask timerTask = getTimerTask();
        if (timerTask != null) {
            timerTask.cancel();
            setTimerTask(null);
        }

        Timer timer = getTimer();
        if (timer != null) {
            timer.cancel();
            setTimer(null);
        }
    }
}
