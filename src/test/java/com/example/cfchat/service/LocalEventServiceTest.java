package com.example.cfchat.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LocalEventServiceTest {

    @Test
    void broadcast_deliversToSubscribedListeners() {
        LocalEventService service = new LocalEventService();
        AtomicReference<String> received = new AtomicReference<>();

        service.subscribe("test.channel", (channel, message) -> received.set(message));
        service.broadcast("test.channel", "hello");

        assertThat(received.get()).isEqualTo("hello");
    }

    @Test
    void broadcast_doesNotDeliverToOtherChannels() {
        LocalEventService service = new LocalEventService();
        AtomicReference<String> received = new AtomicReference<>();

        service.subscribe("other.channel", (channel, message) -> received.set(message));
        service.broadcast("test.channel", "hello");

        assertThat(received.get()).isNull();
    }

    @Test
    void broadcast_deliversToMultipleListeners() {
        LocalEventService service = new LocalEventService();
        AtomicInteger count = new AtomicInteger(0);

        service.subscribe("test.channel", (ch, msg) -> count.incrementAndGet());
        service.subscribe("test.channel", (ch, msg) -> count.incrementAndGet());
        service.broadcast("test.channel", "hello");

        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void broadcast_noListeners_doesNotThrow() {
        LocalEventService service = new LocalEventService();

        assertThatCode(() -> service.broadcast("no.listeners", "hello"))
                .doesNotThrowAnyException();
    }

    @Test
    void subscribe_passesChannelToListener() {
        LocalEventService service = new LocalEventService();
        AtomicReference<String> receivedChannel = new AtomicReference<>();

        service.subscribe("my.channel", (channel, message) -> receivedChannel.set(channel));
        service.broadcast("my.channel", "hello");

        assertThat(receivedChannel.get()).isEqualTo("my.channel");
    }

    @Test
    void broadcast_multipleChannels_onlyMatchingListenersCalled() {
        LocalEventService service = new LocalEventService();
        AtomicInteger channelACount = new AtomicInteger(0);
        AtomicInteger channelBCount = new AtomicInteger(0);

        service.subscribe("channel.a", (ch, msg) -> channelACount.incrementAndGet());
        service.subscribe("channel.b", (ch, msg) -> channelBCount.incrementAndGet());

        service.broadcast("channel.a", "msg1");
        service.broadcast("channel.a", "msg2");
        service.broadcast("channel.b", "msg3");

        assertThat(channelACount.get()).isEqualTo(2);
        assertThat(channelBCount.get()).isEqualTo(1);
    }

    @Test
    void broadcast_listenerThrowsException_otherListenersStillCalled() {
        LocalEventService service = new LocalEventService();
        AtomicInteger successCount = new AtomicInteger(0);

        service.subscribe("test.channel", (ch, msg) -> {
            throw new RuntimeException("Listener error");
        });
        service.subscribe("test.channel", (ch, msg) -> successCount.incrementAndGet());

        assertThatCode(() -> service.broadcast("test.channel", "hello"))
                .doesNotThrowAnyException();
        assertThat(successCount.get()).isEqualTo(1);
    }

    @Test
    void broadcast_passesCorrectMessage() {
        LocalEventService service = new LocalEventService();
        AtomicReference<String> receivedMessage = new AtomicReference<>();

        service.subscribe("msg.channel", (ch, msg) -> receivedMessage.set(msg));
        service.broadcast("msg.channel", "specific-payload-123");

        assertThat(receivedMessage.get()).isEqualTo("specific-payload-123");
    }

    @Test
    void subscribe_multipleTimes_allListenersRegistered() {
        LocalEventService service = new LocalEventService();
        AtomicInteger count = new AtomicInteger(0);

        EventService.EventListener listener1 = (ch, msg) -> count.incrementAndGet();
        EventService.EventListener listener2 = (ch, msg) -> count.incrementAndGet();
        EventService.EventListener listener3 = (ch, msg) -> count.incrementAndGet();

        service.subscribe("test", listener1);
        service.subscribe("test", listener2);
        service.subscribe("test", listener3);

        service.broadcast("test", "go");

        assertThat(count.get()).isEqualTo(3);
    }
}
