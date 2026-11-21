// Phát âm thanh PCM 16-bit (24 kHz) của Gemini theo hàng đợi liên tục,
// có thể xóa hàng đợi ngay lập tức khi ứng viên ngắt lời (barge-in).

export class PcmPlayer {
    constructor(context, { sampleRate = 24000, onStateChange = () => {} } = {}) {
        this.context = context;
        this.sampleRate = sampleRate;
        this.onStateChange = onStateChange;
        this.output = context.createGain();
        this.output.connect(context.destination);
        this.sources = new Set();
        this.nextStartTime = 0;
        this.idleWaiters = [];
    }

    get playing() {
        return this.sources.size > 0;
    }

    /**
     * Thêm một đoạn âm thanh (base64 PCM 16-bit little-endian) vào cuối hàng đợi.
     */
    enqueueBase64(base64) {
        const bytes = decodeBase64(base64);
        const sampleCount = Math.floor(bytes.byteLength / 2);

        if (sampleCount === 0) {
            return;
        }

        const view = new DataView(bytes.buffer, bytes.byteOffset, sampleCount * 2);
        const buffer = this.context.createBuffer(1, sampleCount, this.sampleRate);
        const channel = buffer.getChannelData(0);

        for (let i = 0; i < sampleCount; i++) {
            channel[i] = view.getInt16(i * 2, true) / 0x8000;
        }

        const source = this.context.createBufferSource();
        source.buffer = buffer;
        source.connect(this.output);

        const startAt = Math.max(this.context.currentTime + 0.03, this.nextStartTime);
        source.start(startAt);
        this.nextStartTime = startAt + buffer.duration;

        const wasPlaying = this.playing;
        this.sources.add(source);
        source.onended = () => this.release(source);

        if (!wasPlaying) {
            this.onStateChange(true);
        }
    }

    /**
     * Dừng và xóa toàn bộ âm thanh đang chờ phát.
     */
    flush() {
        for (const source of this.sources) {
            source.onended = null;
            try {
                source.stop();
            } catch {
                // Nguồn đã dừng.
            }
        }

        const wasPlaying = this.playing;
        this.sources.clear();
        this.nextStartTime = 0;

        if (wasPlaying) {
            this.onStateChange(false);
        }

        this.resolveIdle();
    }

    /**
     * Chờ phát hết hàng đợi (tối đa timeoutMs).
     */
    waitUntilIdle(timeoutMs = 8000) {
        if (!this.playing) {
            return Promise.resolve();
        }

        return new Promise(resolve => {
            const timer = setTimeout(resolve, timeoutMs);
            this.idleWaiters.push(() => {
                clearTimeout(timer);
                resolve();
            });
        });
    }

    release(source) {
        this.sources.delete(source);

        if (!this.playing) {
            this.onStateChange(false);
            this.resolveIdle();
        }
    }

    resolveIdle() {
        const waiters = this.idleWaiters;
        this.idleWaiters = [];
        waiters.forEach(resolve => resolve());
    }
}

function decodeBase64(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);

    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }

    return bytes;
}

/**
 * Mã hóa ArrayBuffer sang base64 (dùng cho âm thanh micro gửi lên Gemini).
 */
export function encodeBase64(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    let binary = '';

    for (let i = 0; i < bytes.length; i += 0x8000) {
        binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    }

    return btoa(binary);
}
