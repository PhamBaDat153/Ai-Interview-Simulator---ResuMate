// Phòng phỏng vấn - chế độ nhắn tin: khung chat, đồng hồ đếm ngược, tiến độ, kết thúc.

const room = document.querySelector('[data-live-room]');
const apiBase = room.dataset.apiBase;
const reportUrl = room.dataset.reportUrl;
const totalQuestions = Number(room.dataset.total);

const chatLog = room.querySelector('[data-chat-log]');
const chatEmpty = room.querySelector('[data-chat-empty]');
const chatForm = room.querySelector('[data-chat-form]');
const chatInput = room.querySelector('[data-chat-input]');
const sendButton = room.querySelector('[data-send-button]');
const typing = room.querySelector('[data-typing]');
const progressLabel = room.querySelector('[data-progress]');
const timerLabel = room.querySelector('[data-timer]');
const timerBox = room.querySelector('[data-timer-box]');
const endButton = room.querySelector('[data-end-button]');
const errorBox = room.querySelector('[data-error]');
const errorText = room.querySelector('[data-error-text]');
const retryButton = room.querySelector('[data-retry-button]');
const endingOverlay = document.querySelector('[data-ending-overlay]');

const ROLE_LABELS = { INTERVIEWER: 'Người phỏng vấn', CANDIDATE: 'Bạn' };
const DEFAULT_ERROR = 'Đã có lỗi xảy ra. Vui lòng thử lại.';

let deadline = null;
let timerId = null;
let ended = false;
let pending = null;      // Promise của tin nhắn đang gửi
let confirmTimer = null;

// ===================== Gọi API =====================

async function post(path, body) {
    let response;

    try {
        response = await fetch(`${apiBase}${path}`, {
            method: 'POST',
            headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new Error('Không thể kết nối tới máy chủ. Vui lòng kiểm tra mạng.');
    }

    if (response.status === 204) return null;

    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || DEFAULT_ERROR);
    return data;
}

// ===================== Giao diện =====================

function showError(message, retry) {
    errorText.textContent = message;
    retryButton.hidden = !retry;
    retryButton.onclick = retry || null;
    errorBox.hidden = false;
}

function hideError() {
    errorBox.hidden = true;
}

function formatTime(date) {
    return date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });
}

function addBubble(role, text, timestamp) {
    chatEmpty.hidden = true;

    const bubble = document.createElement('article');
    bubble.className = `chat-bubble ${role === 'CANDIDATE' ? 'from-candidate' : 'from-interviewer'}`;

    const meta = document.createElement('header');
    meta.textContent = `${ROLE_LABELS[role] || role} · ${formatTime(timestamp ? new Date(timestamp) : new Date())}`;

    const body = document.createElement('p');
    body.textContent = text;   // Luôn dùng textContent để tránh chèn HTML.

    bubble.append(meta, body);
    chatLog.append(bubble);
    chatLog.scrollTop = chatLog.scrollHeight;
    return bubble;
}

function updateProgress(progress) {
    if (!progress) return;
    progressLabel.textContent = `Câu ${progress.current}/${progress.total || totalQuestions}`;
}

function setComposerEnabled(enabled) {
    chatInput.disabled = !enabled;
    sendButton.disabled = !enabled;
    if (enabled) chatInput.focus();
}

// ===================== Đồng hồ =====================

function startTimer(remainingSeconds) {
    deadline = Date.now() + remainingSeconds * 1000;
    clearInterval(timerId);
    tick();
    timerId = setInterval(tick, 500);
}

function tick() {
    const remaining = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
    const minutes = Math.floor(remaining / 60);
    const seconds = String(remaining % 60).padStart(2, '0');

    timerLabel.textContent = `${minutes}:${seconds}`;
    timerBox.classList.toggle('warning', remaining <= 60);

    if (remaining === 0) {
        clearInterval(timerId);
        endInterview();
    }
}

// ===================== Luồng phỏng vấn =====================

async function startRoom() {
    hideError();
    typing.hidden = false;

    try {
        const state = await post('/start');
        typing.hidden = true;

        chatLog.querySelectorAll('.chat-bubble').forEach(bubble => bubble.remove());
        state.turns.forEach(turn => addBubble(turn.role, turn.text, turn.timestamp));
        updateProgress(state.progress);
        startTimer(state.remainingSeconds);

        if (state.ended) {
            endInterview();
            return;
        }

        endButton.disabled = false;
        setComposerEnabled(true);
    } catch (error) {
        typing.hidden = true;
        chatEmpty.textContent = 'Chưa kết nối được với người phỏng vấn.';
        showError(error.message, startRoom);
    }
}

async function sendMessage() {
    const text = chatInput.value.trim();
    if (!text || ended || pending) return;

    hideError();
    setComposerEnabled(false);
    chatInput.value = '';

    const bubble = addBubble('CANDIDATE', text);
    typing.hidden = false;

    pending = post('/messages', { text });

    try {
        const data = await pending;

        if (data.reply) addBubble('INTERVIEWER', data.reply);
        updateProgress(data.progress);

        if (data.ended) {
            endInterview();
        } else {
            setComposerEnabled(true);
        }
    } catch (error) {
        // Gửi lỗi: trả lại nội dung để ứng viên gửi lại.
        bubble.remove();
        chatInput.value = text;
        showError(error.message);
        setComposerEnabled(!ended);
    } finally {
        typing.hidden = true;
        pending = null;
    }
}

async function endInterview() {
    if (ended) return;
    ended = true;

    clearInterval(timerId);
    setComposerEnabled(false);
    endButton.disabled = true;

    // Chờ tin nhắn đang gửi (nếu có) để không mất câu trả lời cuối.
    if (pending) {
        await pending.catch(() => null);
    }

    // Đợi một chút để ứng viên kịp đọc lời chào kết thúc.
    await new Promise(resolve => setTimeout(resolve, 1500));

    endingOverlay.classList.add('active');
    endingOverlay.setAttribute('aria-hidden', 'false');

    try {
        await post('/finish');
    } catch {
        // Trang kết quả vẫn hiển thị được kể cả khi gọi finish lỗi.
    }

    window.location.href = reportUrl;
}

// ===================== Sự kiện =====================

chatForm.addEventListener('submit', event => {
    event.preventDefault();
    sendMessage();
});

chatInput.addEventListener('keydown', event => {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
        event.preventDefault();
        sendMessage();
    }
});

// Nhấn "Kết thúc" hai lần để xác nhận (tránh bấm nhầm).
endButton.addEventListener('click', () => {
    if (endButton.classList.contains('confirming')) {
        clearTimeout(confirmTimer);
        endInterview();
        return;
    }

    endButton.classList.add('confirming');
    endButton.textContent = 'Nhấn lần nữa để kết thúc';
    confirmTimer = setTimeout(() => {
        endButton.classList.remove('confirming');
        endButton.textContent = 'Kết thúc';
    }, 3000);
});

startRoom();
