class VoiceRecorder extends AudioWorkletProcessor {
  constructor() {
    super();
    this.recording = false;
    this.samples = new Int16Array(2400);
    this.offset = 0;
    this.port.onmessage = ({data}) => {
      if (data === 'start') {
        this.offset = 0;
        this.recording = true;
      } else if (data === 'stop') {
        this.recording = false;
        this.flush();
        this.port.postMessage({type:'stopped'});
      }
    };
  }
  flush() {
    if (!this.offset) return;
    const bytes = new ArrayBuffer(this.offset * 2);
    const view = new DataView(bytes);
    for (let i = 0; i < this.offset; i++) view.setInt16(i * 2, this.samples[i], true);
    this.port.postMessage({type:'audio', bytes}, [bytes]);
    this.offset = 0;
  }
  process(inputs) {
    const input = inputs[0]?.[0];
    if (this.recording && input) {
      for (const value of input) {
        const sample = Math.max(-1, Math.min(1, value));
        this.samples[this.offset++] = Math.round(sample < 0 ? sample * 32768 : sample * 32767);
        if (this.offset === this.samples.length) this.flush();
      }
    }
    return true;
  }
}

class VoicePlayer extends AudioWorkletProcessor {
  constructor() {
    super();
    this.buffer = new Float32Array(24000 * 30);
    this.read = 0;
    this.write = 0;
    this.count = 0;
    this.active = false;
    this.port.onmessage = ({data}) => {
      if (data.type === 'clear') {
        this.count = this.read = this.write = 0;
        this.active = false;
      } else if (data.type === 'audio') {
        const pcm = new DataView(data.bytes);
        if (pcm.byteLength % 2 || this.count + pcm.byteLength / 2 > this.buffer.length) {
          this.port.postMessage({type:'overflow'});
          return;
        }
        for (let i = 0; i < pcm.byteLength; i += 2) {
          this.buffer[this.write] = pcm.getInt16(i, true) / 32768;
          this.write = (this.write + 1) % this.buffer.length;
          this.count++;
        }
        this.active = true;
      }
    };
  }
  process(inputs, outputs) {
    const output = outputs[0][0];
    for (let i = 0; i < output.length; i++) {
      output[i] = this.count ? this.buffer[this.read] : 0;
      if (this.count) {
        this.read = (this.read + 1) % this.buffer.length;
        this.count--;
      }
    }
    if (!this.count && this.active) {
      this.active = false;
      this.port.postMessage({type:'idle'});
    }
    return true;
  }
}
registerProcessor('voice-recorder', VoiceRecorder);
registerProcessor('voice-player', VoicePlayer);
