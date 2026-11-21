const $ = id => document.getElementById(id);
const labels = {queued:'Fala na fila…', transcribing:'Transcrevendo…', processing:'Consultando o atendimento…', speaking:'Reproduzindo resposta…'};
let callId, socket, context, stream, recorder, player, source, mute, heartbeat, recordingTimer;
let recording = false, connecting = false, starting = false, queued = false, latestTurn, awaitingStart, sentDone = false, playing = false;
let turnBusy = false;
let turnFailed = false;
const turns = new Map();

function status(text) { $('status').textContent = text; }
function error(text) { $('error').textContent = text; }
function controls() {
  const connected = socket?.readyState === WebSocket.OPEN;
  $('connect').disabled = connecting || !!callId;
  $('record').disabled = !connected || recording || starting || queued;
  $('send').disabled = !connected || !recording;
  $('disconnect').disabled = !callId;
  $('record').dataset.recording = String(recording);
}
async function api(path, body) {
  const response = await fetch(path, {method:'POST', headers:{'Content-Type':'application/json'},
    body:body === undefined ? undefined : JSON.stringify(body), signal:AbortSignal.timeout(10000)});
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.detail || `Falha HTTP ${response.status}`);
  return data;
}
function showMessage(id, role, text) {
  $('history').querySelector('.empty')?.remove();
  const key = `${id}:${role}`;
  let element = turns.get(key);
  if (!element) {
    element = document.createElement('article');
    element.className = role;
    const title = document.createElement('strong');
    title.textContent = role === 'user' ? 'Você' : 'Atendimento';
    element.append(title, document.createElement('p'));
    $('history').append(element);
    turns.set(key, element);
    if (turns.size > 100) {
      const oldest = turns.keys().next().value;
      turns.get(oldest).remove();
      turns.delete(oldest);
    }
  }
  element.querySelector('p').textContent = text;
}
function clearPlayback() {
  player?.port.postMessage({type:'clear'});
  playing = false;
  sentDone = false;
}
function handleEvent(event) {
  if (event.type === 'input.started' && event.turnId === latestTurn) {
    awaitingStart?.resolve();
  } else if (event.type === 'error') {
    error(event.text || 'Falha no canal de voz.');
    if (awaitingStart && (!event.turnId || event.turnId === latestTurn)) awaitingStart.reject(new Error(event.text));
    if (!event.turnId || event.turnId === latestTurn) {
      turnFailed = true;
      queued = false;
      if (recording) {
        recorder.port.postMessage('stop');
        recording = false;
        clearTimeout(recordingTimer);
      }
      clearPlayback();
      status('Não foi possível concluir esta fala. Confira a conversa e a agenda.');
    }
  } else if (event.type === 'transcript') {
    showMessage(event.turnId, 'user', event.text);
  } else if (event.type === 'reply') {
    showMessage(event.turnId, 'assistant', event.text);
  } else if (event.turnId === latestTurn) {
    if (event.type === 'state') {
      queued = event.text === 'queued';
      status(labels[event.text] || event.text);
    } else if (event.type === 'audio.delta') {
      const binary = atob(event.audio);
      const bytes = Uint8Array.from(binary, char => char.charCodeAt(0)).buffer;
      playing = true;
      player.port.postMessage({type:'audio', bytes}, [bytes]);
    } else if (event.type === 'turn.done') {
      queued = false;
      turnBusy = false;
      sentDone = true;
      if (!playing && !turnFailed) status('Pronto para outra fala');
    }
  }
  controls();
}
async function connect() {
  connecting = true;
  error('');
  controls();
  try {
    stream = await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true, noiseSuppression:true, channelCount:1}});
    context = new AudioContext({sampleRate:24000});
    if (context.sampleRate !== 24000) throw new Error('Este navegador não disponibilizou áudio em 24000 Hz. Use Chrome ou Chromium.');
    await context.resume();
    await context.audioWorklet.addModule('/voice-worklet.js');
    recorder = new AudioWorkletNode(context, 'voice-recorder');
    player = new AudioWorkletNode(context, 'voice-player', {outputChannelCount:[1]});
    mute = context.createGain();
    mute.gain.value = 0;
    source = context.createMediaStreamSource(stream);
    source.connect(recorder).connect(mute).connect(context.destination);
    player.connect(context.destination);
    recorder.port.onmessage = ({data}) => {
      if (data.type === 'audio' && socket?.readyState === WebSocket.OPEN) {
        if (socket.bufferedAmount > 262144) {
          error('A conexão não conseguiu acompanhar o áudio. Ligue novamente.');
          void disconnect();
        } else {
          socket.send(data.bytes);
        }
      } else if (data.type === 'stopped' && recording) {
        recording = false;
        queued = true;
        turnBusy = true;
        socket.send(JSON.stringify({type:'input.commit', turnId:latestTurn}));
        status('Enviando fala…');
        controls();
      }
    };
    player.port.onmessage = ({data}) => {
      if (data.type === 'idle') {
        playing = false;
        if (sentDone && !recording && !starting && !turnFailed) status('Pronto para outra fala');
      } else if (data.type === 'overflow') {
        error('A resposta excedeu o buffer de áudio. O texto continua na conversa.');
        void disconnect();
      }
    };
    const call = await api('/api/calls', {callId:`voice-${crypto.randomUUID()}`});
    callId = call.callId;
    $('call-id').textContent = callId;
    socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/api/calls/${encodeURIComponent(callId)}/voice`);
    const opened = socket;
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('Tempo de conexão esgotado.')), 10000);
      opened.onmessage = message => {
        const event = JSON.parse(message.data);
        if (event.type === 'ready') { clearTimeout(timer); resolve(); }
        else handleEvent(event);
      };
      opened.onerror = () => { clearTimeout(timer); reject(new Error('Não foi possível abrir o canal de voz.')); };
      opened.onclose = () => {
        clearTimeout(timer);
        reject(new Error('O canal de voz foi encerrado.'));
        if (socket === opened && callId) {
          error('A conexão foi encerrada. Confira a agenda antes de repetir uma operação.');
          void disconnect();
        }
      };
    });
    heartbeat = setInterval(async () => {
      const activeId = callId;
      try { await api(`/api/calls/${encodeURIComponent(activeId)}/heartbeat`); }
      catch (failure) {
        if (callId === activeId) { error(failure.message); void disconnect(); }
      }
    }, call.heartbeatIntervalSeconds * 1000);
    status('Conectado. Clique em Gravar para falar.');
  } catch (failure) {
    error(failure.message || 'Não foi possível acessar o microfone.');
    await disconnect();
  } finally {
    connecting = false;
    controls();
  }
}
async function startRecording() {
  if (starting || recording || queued || socket?.readyState !== WebSocket.OPEN) return;
  starting = true;
  turnFailed = false;
  const playbackInterrupted = playing || turnBusy;
  latestTurn = crypto.randomUUID();
  clearPlayback();
  error('');
  controls();
  try {
    await context.resume();
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('A gravação não foi aceita pelo servidor.')), 5000);
      awaitingStart = {resolve:() => {clearTimeout(timer); resolve();}, reject:failure => {clearTimeout(timer); reject(failure);}};
      socket.send(JSON.stringify({type:'input.start', turnId:latestTurn, format:'pcm16', sampleRate:24000, channels:1, playbackInterrupted}));
    });
    if (!callId || socket?.readyState !== WebSocket.OPEN || !recorder) throw new Error('Ligação encerrada.');
    recording = true;
    recorder.port.postMessage('start');
    status('Gravando… clique em Enviar fala para terminar.');
    recordingTimer = setTimeout(sendRecording, 29500);
  } catch (failure) {
    error(failure.message);
  } finally {
    starting = false;
    awaitingStart = null;
    controls();
  }
}
function sendRecording() {
  if (!recording) return;
  clearTimeout(recordingTimer);
  $('send').disabled = true;
  recorder.port.postMessage('stop');
}
async function disconnect() {
  const ended = callId;
  callId = null;
  clearInterval(heartbeat);
  clearTimeout(recordingTimer);
  awaitingStart?.reject(new Error('Ligação encerrada.'));
  awaitingStart = null;
  recording = queued = starting = false;
  turnBusy = false;
  latestTurn = null;
  clearPlayback();
  const oldSocket = socket;
  socket = null;
  oldSocket?.close();
  stream?.getTracks().forEach(track => track.stop());
  source?.disconnect();
  recorder?.disconnect();
  player?.disconnect();
  mute?.disconnect();
  if (context && context.state !== 'closed') await context.close();
  stream = context = recorder = player = source = mute = null;
  $('call-id').textContent = 'Nenhuma ligação iniciada';
  status('Ligação encerrada');
  controls();
  if (ended) {
    try { await api(`/api/calls/${encodeURIComponent(ended)}/end`); }
    catch { error('Não foi possível confirmar o encerramento. Reservas temporárias expiram sem heartbeat.'); }
  }
}
$('connect').addEventListener('click', () => void connect());
$('record').addEventListener('click', () => void startRecording());
$('send').addEventListener('click', sendRecording);
$('disconnect').addEventListener('click', () => void disconnect());
window.addEventListener('pagehide', () => {
  stream?.getTracks().forEach(track => track.stop());
  if (callId) navigator.sendBeacon(`/api/calls/${encodeURIComponent(callId)}/end`);
  socket?.close();
});
try {
  const response = await fetch('/api/voice/status');
  const config = await response.json();
  if (!config.enabled) {
    status('Voz desativada');
    error('Inicie o serviço de áudio local, configure VOICE_ENABLED=true no .env e reinicie a aplicação.');
  } else if (!navigator.mediaDevices || !window.AudioWorkletNode || !window.isSecureContext) {
    status('Navegador incompatível');
    error('Abra esta página em localhost ou HTTPS usando Chrome/Chromium com suporte a microfone e AudioWorklet.');
  } else {
    status('Pronto para ligar');
    $('connect').disabled = false;
  }
} catch {
  status('Serviço indisponível');
  error('Não foi possível consultar o serviço de voz.');
}
