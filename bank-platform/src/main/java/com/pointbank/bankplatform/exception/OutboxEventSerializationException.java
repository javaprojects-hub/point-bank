package com.pointbank.bankplatform.exception;

public class OutboxEventSerializationException extends RuntimeException {

    public OutboxEventSerializationException(Throwable cause) {
        super("Unable to serialize the money transfer event for the transactional outbox.", cause);
    }
}
