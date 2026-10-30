package com.pointbank.bankplatform.service;

public record ClaimedOutboxEvent(String eventId, String payload) {
}
