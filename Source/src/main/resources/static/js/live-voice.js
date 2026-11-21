// Phòng phỏng vấn - chế độ giọng nói: trình duyệt kết nối trực tiếp tới Gemini Live API
// bằng ephemeral token do máy chủ cấp (âm thanh không đi qua Spring).

import { PcmPlayer, encodeBase64 } from './audio/pcm-player.js';

// Phiên bản SDK được cố định để tránh thay đổi ngoài ý muốn.
const GENAI_SDK_URL = 'https://cdn.jsdelivr.net/npm/@google/genai@3.6.1/+esm';
const WORKLET_URL = new URL('./audio/pcm-recorder-worklet.js', import.meta.url);

const INPUT_MIME_TYPE = 'audio/pcm;rate=16000';
const FLUSH_INTERVAL_MS = 15_000;
const ONE_MINUTE_SECONDS = 60;
const DEFAULT_ERROR = 'Đã có lỗi xảy ra. Vui lòng thử lại.';

const room = document.querySelector('[data-live-room]');
const apiBase = room.dataset.apiBase;
const reportUrl = room.dataset.reportUrl;
const questionIds = (room.dataset.questionIds || '').split(',').filter(Boolean);
const kickoffText = room.dataset.kickoffText || '';
const oneMinuteText = room.dataset.oneMinuteText || '';
const resumeText = room.dataset.resumeText || '';
const handleStorageKey = `resumate-live-handle-${room.dataset.interviewId}`;

const ui = {
    startPanel: room.querySelector('[data-voice-start]'),
    startButton: room.querySelector('[data-voice-start-button]'),
    livePanel: room.querySelector('[data-voice-live]'),
    unsupported: room.querySelector('[data-voice-unsupported]'),
    unsupportedText: room.querySelector('[data-unsupported-text]'),
    avatar: room.querySelector('[data-avatar]'),
    interviewerState: room.querySelector('[data-interviewer-state]'),
    micLevel: room.querySelector('[data-mic-level]'),
    muteButton: room.querySelector('[data-mute-button]'),
    captionsButton: room.querySelector('[data-captions-button]'),
    captionsPanel: room.querySelector('[data-captions-panel]'),
    captionLog: room.querySelector('[data-caption-log]'),
    connection: room.querySelector('[data-connection-status]'),
    progress: room.querySelector('[data-progress]'),
    timer: room.querySelector('[data-timer]'),
    timerBox: room.querySelector('[data-timer-box]'),
    endButton: room.querySelector('[data-end-button]'),
    errorBox: room.querySelector('[data-error]'),
    errorText: room.querySelector('[data-error-text]'),
    retryButton: room.querySelector('[data-retry-button]'),
    endingOverlay: document.querySelector('[data-ending-overlay]'),
};

const state = {
    sdk: null,
    session: null,
    generation: 0,          // Tăng mỗi lần kết nối để bỏ qua sự kiện của kết nối cũ.
    token: null,
    tokenExpiresAt: 0,
    model: null,
    apiVersion: 'v1beta',
    handle: sessionStorage.getItem(handleStorageKey),
    reconnecting: false,
    ended: false,
    muted: false,
    audioContext: null,
    micStream: null,
    player: null,
    turns: [],
    startNewInterviewerTurn: true,
    currentQuestionId: null,
    deadline: 0,
    timerId: null,
    flushId: null,
    warnedOneMinute: false,
    dirty: false,
    confirmTimer: null,
};

// ===================== Tiện ích =====================

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
        // Lỗi 4xx (hết lượt cấp token, đã kết thúc...) thì không thử lại.
        this.fatal = status >= 400 && status < 500;
    }
}

async function post(path, body) {
    let response;

    try {
        response = await fetch(`${apiBase}${path}`, {
            method: 'POST',
            headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
            keepalive: path === '/transcript' || path === '/finish',
        });
    } catch {
        throw new ApiError('Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng.', 0);
    }

    if (response.status === 204) return null;

    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new ApiError(data.error || DEFAULT_ERROR, response.status);
    return data;
}

function showError(message, retry) {
    ui.errorText.textContent = message;
    ui.retryButton.hidden = !retry;
    ui.retryButton.onclick = retry || null;
    ui.errorBox.hidden = false;
}

function hideError() {
    ui.errorBox.hidden = true;
}

function setConnection(text, tone) {
    ui.connection.textContent = text;
    ui.connection.dataset.tone = tone;
}

function setInterviewerSpeaking(speaking) {
    ui.avatar.classList.toggle('speaking', speaking);
    ui.interviewerState.textContent = speaking ? 'Đang nói' : 'Đang nghe';
}

function updateProgress() {
    const index = questionIds.indexOf(state.currentQuestionId);
    ui.progress.textContent = `Câu ${index + 1}/${questionIds.length}`;
}

function showUnsupported(message) {
    ui.startPanel.hidden = true;
    ui.livePanel.hidden = true;
    ui.unsupportedText.textContent = message;
    ui.unsupported.hidden = false;
}

function supportProblem() {
    if (!window.isSecureContext) {
        return 'Trình duyệt chỉ cho phép dùng micro trên kết nối an toàn (HTTPS hoặc localhost).';
    }

    if (!navigator.mediaDevices?.getUserMedia) {
        return 'Trình duyệt của bạn không hỗ trợ ghi âm từ micro.';
    }

    if (!window.AudioContext || !window.AudioWorkletNode) {
        return 'Trình duyệt của bạn không hỗ trợ xử lý âm thanh (AudioWorklet). Hãy dùng Chrome hoặc Edge mới nhất.';
    }

    return null;
}

function micErrorMessage(error) {
    switch (error?.name) {
        case 'NotAllowedError':
        case 'SecurityError':
            return 'Bạn chưa cho phép dùng micro. Hãy bật quyền micro cho trang này rồi tải lại trang.';
        case 'NotFoundError':
            return 'Không tìm thấy micro. Hãy kết nối micro rồi thử lại.';
        case 'NotReadableError':
            return 'Micro đang được ứng dụng khác sử dụng. Hãy đóng ứng dụng đó rồi thử lại.';
        default:
            return 'Không thể sử dụng micro trên trình duyệt này.';
    }
}

// ===================== Phụ đề và transcript =====================

function appendTranscript(role, text) {
    if (!text) return;

    let turn = state.turns[state.turns.length - 1];
    const needsNewTurn = !turn || turn.role !== role
            || (role === 'INTERVIEWER' && state.startNewInterviewerTurn);

    if (needsNewTurn) {
        turn = { role, text: '', timestamp: new Date().toISOString(), element: null };
        state.turns.push(turn);
    }

    if (role === 'INTERVIEWER') {
        state.startNewInterviewerTurn = false;
    }

    turn.text += text;
    state.dirty = true;
    renderTurn(turn);
}

function renderTurn(turn) {
    if (!turn.element) {
        turn.element = document.createElement('p');
        turn.element.className = `caption ${turn.role === 'CANDIDATE' ? 'from-candidate' : 'from-interviewer'}`;

        const label = document.createElement('strong');
        label.textContent = turn.role === 'CANDIDATE' ? 'Bạn: ' : 'Người phỏng vấn: ';

        turn.element.append(label, document.createElement('span'));
        ui.captionLog.append(turn.element);
    }

    // textContent để không chèn HTML từ transcript.
    turn.element.lastChild.textContent = turn.text.replace(/\s+/g, ' ').trim();
    ui.captionLog.scrollTop = ui.captionLog.scrollHeight;
}

function transcriptPayload() {
    return {
        turns: state.turns
            .map(turn => ({
                role: turn.role,
                text: turn.text.replace(/\s+/g, ' ').trim(),
                timestamp: turn.timestamp,
            }))
            .filter(turn => turn.text),
        currentQuestionId: state.currentQuestionId,
    };
}

async function flushTranscript(force = false) {
    if (!state.dirty && !force) return;
    state.dirty = false;

    try {
        await post('/transcript', transcriptPayload());
    } catch {
        // Lần gửi sau sẽ thử lại.
        state.dirty = true;
    }
}

// ===================== Âm thanh =====================

async function startAudio() {
    state.micStream = await navigator.mediaDevices.getUserMedia({
        audio: {
            channelCount: 1,
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true,
        },
    });

    state.audioContext = new AudioContext();
    await state.audioContext.resume();
    await state.audioContext.audioWorklet.addModule(WORKLET_URL);

    const source = state.audioContext.createMediaStreamSource(state.micStream);
    const recorder = new AudioWorkletNode(state.audioContext, 'pcm-recorder');
    const silent = state.audioContext.createGain();
    silent.gain.value = 0;

    source.connect(recorder);
    recorder.connect(silent);
    silent.connect(state.audioContext.destination);
    recorder.port.onmessage = event => handleMicChunk(event.data);

    state.player = new PcmPlayer(state.audioContext, {
        onStateChange: speaking => setInterviewerSpeaking(speaking),
    });
}

function handleMicChunk({ pcm, level }) {
    const shownLevel = state.muted ? 0 : Math.min(1, level * 4);
    ui.micLevel.style.width = `${Math.round(shownLevel * 100)}%`;

    if (!state.session || state.muted || state.reconnecting || state.ended) {
        return;
    }

    try {
        state.session.sendRealtimeInput({
            audio: { data: encodeBase64(pcm), mimeType: INPUT_MIME_TYPE },
        });
    } catch {
        // Kết nối đang đóng; sự kiện onclose sẽ xử lý kết nối lại.
    }
}

function stopAudio() {
    state.micStream?.getTracks().forEach(track => track.stop());
    state.player?.flush();
    state.audioContext?.close().catch(() => {});
}

function sendText(text) {
    if (!text || !state.session) return;

    try {
        state.session.sendRealtimeInput({ text });
    } catch {
        // Bỏ qua nếu kết nối đang đóng.
    }
}

// ===================== Kết nối Gemini Live =====================

async function loadSdk() {
    if (!state.sdk) {
        try {
            state.sdk = await import(GENAI_SDK_URL);
        } catch {
            throw new ApiError('Không tải được thư viện kết nối giọng nói. Vui lòng kiểm tra mạng.', 0);
        }
    }

    return state.sdk;
}

async function mintToken() {
    const data = await post('/token');
    state.token = data.token;
    state.model = data.model;
    state.apiVersion = data.apiVersion || 'v1beta';
    state.tokenExpiresAt = new Date(data.expiresAt).getTime();
}

function tokenUsable() {
    return state.token && Date.now() < state.tokenExpiresAt - 30_000;
}

async function openSession(handle) {
    const { GoogleGenAI, Modality } = await loadSdk();
    const generation = ++state.generation;

    // Cấu hình chính (system instruction, công cụ, transcription) đã bị khóa trong token.
    const ai = new GoogleGenAI({ apiKey: state.token, httpOptions: { apiVersion: state.apiVersion } });

    const session = await ai.live.connect({
        model: state.model,
        config: {
            responseModalities: [Modality.AUDIO],
            sessionResumption: handle ? { handle } : {},
        },
        callbacks: {
            onmessage: message => {
                if (generation === state.generation) handleMessage(message);
            },
            onerror: () => {},
            onclose: event => {
                if (generation === state.generation) handleClose(event);
            },
        },
    });

    if (generation !== state.generation) {
        session.close();
        return false;
    }

    state.session = session;
    return true;
}

/**
 * Thử kết nối theo thứ tự: tiếp tục phiên cũ bằng handle (token cũ, rồi token mới),
 * cuối cùng mở phiên mới. Trả về true nếu tiếp tục được ngữ cảnh cũ.
 */
async function connectWithFallback() {
    const plans = state.handle
        ? [{ handle: state.handle, freshToken: false }, { handle: state.handle, freshToken: true },
            { handle: null, freshToken: true }]
        : [{ handle: null, freshToken: false }, { handle: null, freshToken: true }];

    let lastError = null;

    for (const plan of plans) {
        try {
            if (plan.freshToken || !tokenUsable()) {
                await mintToken();
            }

            if (await openSession(plan.handle)) {
                return Boolean(plan.handle);
            }
        } catch (error) {
            lastError = error;

            if (error.fatal) break;
        }
    }

    throw lastError || new ApiError('Không thể kết nối tới người phỏng vấn AI.', 0);
}

async function connect({ initial }) {
    state.reconnecting = true;
    setConnection(initial ? 'Đang kết nối...' : 'Đang kết nối lại...', 'pending');

    try {
        const resumed = await connectWithFallback();
        state.reconnecting = false;
        setConnection('Đã kết nối', 'ok');
        hideError();

        if (!resumed) {
            // Phiên mới: chào ứng viên, hoặc tiếp tục từ câu hỏi hiện tại nếu đã có hội thoại.
            sendText(state.turns.length === 0
                ? kickoffText
                : `${resumeText} ${state.currentQuestionId || questionIds[0] || ''}`);
        }
    } catch (error) {
        state.reconnecting = false;
        setConnection('Mất kết nối', 'error');

        if (initial && !error.fatal) {
            // Lần kết nối đầu thất bại: cho phép thử lại thay vì kết thúc ngay.
            showError(error.message || DEFAULT_ERROR, () => {
                hideError();
                connect({ initial: true });
            });
            return;
        }

        // Không kết nối lại được: lưu transcript và chuyển tới trang kết quả.
        showError(`${error.message || DEFAULT_ERROR} Buổi phỏng vấn sẽ được kết thúc và lưu lại.`);
        await endInterview('connection');
    }
}

function scheduleReconnect() {
    if (state.ended || state.reconnecting) return;

    // Bỏ qua sự kiện của kết nối cũ.
    const oldSession = state.session;
    state.generation++;
    state.session = null;

    try {
        oldSession?.close();
    } catch {
        // Kết nối đã đóng.
    }

    connect({ initial: false });
}

function handleClose() {
    state.session = null;
    scheduleReconnect();
}

function handleMessage(message) {
    if (message.sessionResumptionUpdate) {
        const { resumable, newHandle } = message.sessionResumptionUpdate;

        if (resumable && newHandle) {
            state.handle = newHandle;
            sessionStorage.setItem(handleStorageKey, newHandle);
        }
    }

    if (message.goAway) {
        // Máy chủ sắp đóng kết nối (~10 phút/kết nối): chủ động kết nối lại bằng handle.
        scheduleReconnect();
        return;
    }

    if (message.toolCall) {
        handleToolCall(message.toolCall);
    }

    const content = message.serverContent;
    if (!content) return;

    if (content.interrupted) {
        // Ứng viên ngắt lời: dừng phát ngay.
        state.player.flush();
        state.startNewInterviewerTurn = true;
    }

    for (const part of content.modelTurn?.parts || []) {
        const data = part.inlineData;

        if (data?.data && (data.mimeType || '').startsWith('audio/pcm')) {
            state.player.enqueueBase64(data.data);
        }
    }

    if (content.inputTranscription?.text) {
        appendTranscript('CANDIDATE', content.inputTranscription.text);
        state.startNewInterviewerTurn = true;
    }

    if (content.outputTranscription?.text) {
        appendTranscript('INTERVIEWER', content.outputTranscription.text);
    }

    if (content.turnComplete) {
        state.startNewInterviewerTurn = true;
    }
}

function handleToolCall(toolCall) {
    const responses = [];
    let shouldEnd = false;

    for (const call of toolCall.functionCalls || []) {
        const args = call.args || {};
        let response = { ok: true };

        if (call.name === 'mark_question') {
            if (questionIds.includes(args.questionId)) {
                if (args.status === 'ASKED') {
                    state.currentQuestionId = args.questionId;
                    updateProgress();
                }
                state.dirty = true;
            } else {
                response = { ok: false, error: 'unknown questionId' };
            }
        } else if (call.name === 'end_interview') {
            shouldEnd = true;
        } else {
            response = { ok: false, error: 'unknown function' };
        }

        responses.push({ id: call.id, name: call.name, response });
    }

    try {
        state.session?.sendToolResponse({ functionResponses: responses });
    } catch {
        // Kết nối đang đóng.
    }

    if (shouldEnd) {
        endInterview('tool');
    }
}

// ===================== Đồng hồ =====================

function startTimer(remainingSeconds) {
    state.deadline = Date.now() + remainingSeconds * 1000;
    state.warnedOneMinute = remainingSeconds <= ONE_MINUTE_SECONDS;
    clearInterval(state.timerId);
    tick();
    state.timerId = setInterval(tick, 500);
}

function tick() {
    const remaining = Math.max(0, Math.ceil((state.deadline - Date.now()) / 1000));
    ui.timer.textContent = `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}`;
    ui.timerBox.classList.toggle('warning', remaining <= ONE_MINUTE_SECONDS);

    if (!state.warnedOneMinute && remaining <= ONE_MINUTE_SECONDS) {
        state.warnedOneMinute = true;
        sendText(oneMinuteText);
    }

    if (remaining === 0) {
        clearInterval(state.timerId);
        endInterview('timeout');
    }
}

// ===================== Bắt đầu / kết thúc =====================

async function startInterview() {
    hideError();
    ui.startButton.disabled = true;
    ui.startButton.textContent = 'Đang chuẩn bị...';

    try {
        await startAudio();
    } catch (error) {
        stopAudio();
        showUnsupported(micErrorMessage(error));
        return;
    }

    let roomState;

    try {
        roomState = await post('/start');
    } catch (error) {
        stopAudio();
        ui.startButton.disabled = false;
        ui.startButton.textContent = 'Vào phỏng vấn';
        showError(error.message);
        return;
    }

    // Khôi phục hội thoại đã lưu (khi tải lại trang).
    for (const turn of roomState.turns || []) {
        const restored = { role: turn.role, text: turn.text, timestamp: turn.timestamp, element: null };
        state.turns.push(restored);
        renderTurn(restored);
    }

    state.currentQuestionId = roomState.progress?.currentQuestionId || null;
    updateProgress();

    ui.startPanel.hidden = true;
    ui.livePanel.hidden = false;
    ui.captionsPanel.hidden = false;
    ui.endButton.disabled = false;
    setInterviewerSpeaking(false);

    if (roomState.ended) {
        await endInterview('timeout');
        return;
    }

    startTimer(roomState.remainingSeconds);
    state.flushId = setInterval(() => flushTranscript(), FLUSH_INTERVAL_MS);
    await connect({ initial: true });
}

async function endInterview(reason) {
    if (state.ended) return;
    state.ended = true;

    clearInterval(state.timerId);
    clearInterval(state.flushId);
    ui.endButton.disabled = true;
    ui.muteButton.disabled = true;

    // Kết thúc do người phỏng vấn chủ động: đợi phát xong lời chào.
    if (reason === 'tool') {
        await state.player?.waitUntilIdle(8000);
    }

    const session = state.session;
    state.generation++;
    state.session = null;

    try {
        session?.close();
    } catch {
        // Kết nối đã đóng.
    }

    stopAudio();
    setConnection('Đã kết thúc', 'idle');
    ui.endingOverlay.classList.add('active');
    ui.endingOverlay.setAttribute('aria-hidden', 'false');

    await flushTranscript(true);

    try {
        await post('/finish');
    } catch {
        // Trang kết quả vẫn hiển thị được kể cả khi gọi finish lỗi.
    }

    sessionStorage.removeItem(handleStorageKey);
    window.location.href = reportUrl;
}

// ===================== Sự kiện giao diện =====================

ui.startButton.addEventListener('click', startInterview);

ui.muteButton.addEventListener('click', () => {
    state.muted = !state.muted;
    ui.muteButton.textContent = state.muted ? 'Bật micro' : 'Tắt micro';
    ui.muteButton.classList.toggle('active', state.muted);
    ui.micLevel.style.width = '0%';

    if (state.muted && state.session) {
        try {
            // Báo cho Gemini biết luồng âm thanh tạm dừng để kết thúc lượt nói hiện tại.
            state.session.sendRealtimeInput({ audioStreamEnd: true });
        } catch {
            // Bỏ qua.
        }
    }
});

ui.captionsButton.addEventListener('click', () => {
    const visible = ui.captionsPanel.hidden;
    ui.captionsPanel.hidden = !visible;
    ui.captionsButton.textContent = visible ? 'Ẩn phụ đề' : 'Hiện phụ đề';
    ui.captionsButton.setAttribute('aria-pressed', String(visible));
});

// Nhấn "Kết thúc" hai lần để xác nhận.
ui.endButton.addEventListener('click', () => {
    if (ui.endButton.classList.contains('confirming')) {
        clearTimeout(state.confirmTimer);
        endInterview('user');
        return;
    }

    ui.endButton.classList.add('confirming');
    ui.endButton.textContent = 'Nhấn lần nữa để kết thúc';
    state.confirmTimer = setTimeout(() => {
        ui.endButton.classList.remove('confirming');
        ui.endButton.textContent = 'Kết thúc';
    }, 3000);
});

// Đóng tab / chuyển trang: gửi transcript bằng sendBeacon để không mất dữ liệu.
window.addEventListener('pagehide', () => {
    if (state.ended || state.turns.length === 0) return;

    navigator.sendBeacon(`${apiBase}/transcript`,
        new Blob([JSON.stringify(transcriptPayload())], { type: 'application/json' }));
});

const problem = supportProblem();

if (problem) {
    showUnsupported(problem);
}
