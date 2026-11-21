package tech.voice.icety.adapter.in.voice;

public record VoiceInput(String type, String turnId, String format, Integer sampleRate, Integer channels,
                         Boolean playbackInterrupted) { }
