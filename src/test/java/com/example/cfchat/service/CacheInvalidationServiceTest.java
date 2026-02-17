package com.example.cfchat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheInvalidationServiceTest {

    @Mock
    private EventService eventService;

    @Mock
    private SystemSettingService systemSettingService;

    private CacheInvalidationService cacheInvalidationService;

    @BeforeEach
    void setUp() {
        cacheInvalidationService = new CacheInvalidationService(eventService, systemSettingService);
        // Invoke @PostConstruct manually since Mockito does not call it
        cacheInvalidationService.init();
    }

    @Test
    void init_subscribesToSettingsChannel() {
        verify(eventService).subscribe(eq(CacheInvalidationService.CHANNEL_SETTINGS), any());
    }

    @Test
    void init_subscribesToModelsChannel() {
        verify(eventService).subscribe(eq(CacheInvalidationService.CHANNEL_MODELS), any());
    }

    @Test
    void init_subscribesToUsersChannel() {
        verify(eventService).subscribe(eq(CacheInvalidationService.CHANNEL_USERS), any());
    }

    @Test
    void notifySettingsChanged_broadcastsToCorrectChannel() {
        cacheInvalidationService.notifySettingsChanged("test.key");

        verify(eventService).broadcast(CacheInvalidationService.CHANNEL_SETTINGS, "test.key");
    }

    @Test
    void notifyModelsChanged_broadcastsInvalidate() {
        cacheInvalidationService.notifyModelsChanged();

        verify(eventService).broadcast(CacheInvalidationService.CHANNEL_MODELS, "invalidate");
    }

    @Test
    void notifyUserChanged_broadcastsUserId() {
        cacheInvalidationService.notifyUserChanged("user-123");

        verify(eventService).broadcast(CacheInvalidationService.CHANNEL_USERS, "user-123");
    }

    @Test
    void getCachedSetting_cachesOnFirstCall() {
        when(systemSettingService.getSetting("key1", "default")).thenReturn("value1");

        String result1 = cacheInvalidationService.getCachedSetting("key1", "default");
        String result2 = cacheInvalidationService.getCachedSetting("key1", "default");

        assertThat(result1).isEqualTo("value1");
        assertThat(result2).isEqualTo("value1");
        // Should only call systemSettingService once due to caching
        verify(systemSettingService, times(1)).getSetting("key1", "default");
    }

    @Test
    void getCachedSetting_differentKeys_eachFetchedOnce() {
        when(systemSettingService.getSetting("key1", "d1")).thenReturn("val1");
        when(systemSettingService.getSetting("key2", "d2")).thenReturn("val2");

        String r1 = cacheInvalidationService.getCachedSetting("key1", "d1");
        String r2 = cacheInvalidationService.getCachedSetting("key2", "d2");

        assertThat(r1).isEqualTo("val1");
        assertThat(r2).isEqualTo("val2");
        verify(systemSettingService, times(1)).getSetting("key1", "d1");
        verify(systemSettingService, times(1)).getSetting("key2", "d2");
    }

    @Test
    void generationCounters_startAtZero() {
        assertThat(cacheInvalidationService.getSettingsGeneration()).isEqualTo(0);
        assertThat(cacheInvalidationService.getModelsGeneration()).isEqualTo(0);
    }

    @Test
    void settingsEvent_incrementsSettingsGeneration() {
        // Capture the listener registered for the settings channel
        ArgumentCaptor<EventService.EventListener> listenerCaptor = ArgumentCaptor.forClass(EventService.EventListener.class);
        verify(eventService).subscribe(eq(CacheInvalidationService.CHANNEL_SETTINGS), listenerCaptor.capture());

        long before = cacheInvalidationService.getSettingsGeneration();
        listenerCaptor.getValue().onMessage(CacheInvalidationService.CHANNEL_SETTINGS, "some.key");

        assertThat(cacheInvalidationService.getSettingsGeneration()).isEqualTo(before + 1);
    }

    @Test
    void modelsEvent_incrementsModelsGeneration() {
        // Capture the listener registered for the models channel
        ArgumentCaptor<EventService.EventListener> listenerCaptor = ArgumentCaptor.forClass(EventService.EventListener.class);
        verify(eventService).subscribe(eq(CacheInvalidationService.CHANNEL_MODELS), listenerCaptor.capture());

        long before = cacheInvalidationService.getModelsGeneration();
        listenerCaptor.getValue().onMessage(CacheInvalidationService.CHANNEL_MODELS, "invalidate");

        assertThat(cacheInvalidationService.getModelsGeneration()).isEqualTo(before + 1);
    }

    @Test
    void settingsEvent_clearsCachedSettings() {
        // First, populate the cache
        when(systemSettingService.getSetting("cached.key", "def")).thenReturn("original");
        cacheInvalidationService.getCachedSetting("cached.key", "def");
        verify(systemSettingService, times(1)).getSetting("cached.key", "def");

        // Capture the settings listener and fire an invalidation event
        ArgumentCaptor<EventService.EventListener> listenerCaptor = ArgumentCaptor.forClass(EventService.EventListener.class);
        verify(eventService).subscribe(eq(CacheInvalidationService.CHANNEL_SETTINGS), listenerCaptor.capture());
        listenerCaptor.getValue().onMessage(CacheInvalidationService.CHANNEL_SETTINGS, "cached.key");

        // Now, re-fetch should call systemSettingService again since cache was cleared
        when(systemSettingService.getSetting("cached.key", "def")).thenReturn("updated");
        String result = cacheInvalidationService.getCachedSetting("cached.key", "def");

        assertThat(result).isEqualTo("updated");
        verify(systemSettingService, times(2)).getSetting("cached.key", "def");
    }

    @Test
    void channelConstants_haveExpectedValues() {
        assertThat(CacheInvalidationService.CHANNEL_SETTINGS).isEqualTo("cache.settings");
        assertThat(CacheInvalidationService.CHANNEL_MODELS).isEqualTo("cache.models");
        assertThat(CacheInvalidationService.CHANNEL_USERS).isEqualTo("cache.users");
    }
}
