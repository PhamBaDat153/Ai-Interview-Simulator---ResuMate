// AudioWorkletProcessor ghi âm micro: nhận Float32 theo sampleRate của AudioContext,
// giảm mẫu xuống 16 kHz, chuyển sang PCM 16-bit và gửi từng khối ~100 ms về luồng chính.

const TARGET_RATE = 16000;
const CHUNK_SAMPLES = 1600; // 100 ms ở 16 kHz

class PcmRecorderProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        // Số mẫu đầu vào ứng với một mẫu đầu ra (ví dụ 48 kHz → 3).
        this.ratio = Math.max(1, sampleRate / TARGET_RATE);
        this.accumulated = 0;
        this.sum = 0;
        this.count = 0;
        this.squareSum = 0;
        this.chunk = new Int16Array(CHUNK_SAMPLES);
        this.length = 0;
    }

    process(inputs) {
        const channel = inputs[0] && inputs[0][0];

        if (!channel) {
            return true;
        }

        for (let i = 0; i < channel.length; i++) {
            // Lấy trung bình các mẫu trong mỗi khoảng để giảm mẫu (lọc thông thấp đơn giản).
            this.sum += channel[i];
            this.count++;
            this.accumulated++;

            if (this.accumulated >= this.ratio) {
                this.accumulated -= this.ratio;
                const value = Math.max(-1, Math.min(1, this.sum / this.count));
                this.sum = 0;
                this.count = 0;

                this.chunk[this.length++] = value < 0 ? value * 0x8000 : value * 0x7fff;
                this.squareSum += value * value;

                if (this.length === CHUNK_SAMPLES) {
                    this.emit();
                }
            }
        }

        return true;
    }

    emit() {
        const level = Math.sqrt(this.squareSum / this.length);
        const pcm = this.chunk.buffer;

        // Chuyển quyền sở hữu buffer sang luồng chính để không phải sao chép.
        this.port.postMessage({ pcm, level }, [pcm]);

        this.chunk = new Int16Array(CHUNK_SAMPLES);
        this.length = 0;
        this.squareSum = 0;
    }
}

registerProcessor('pcm-recorder', PcmRecorderProcessor);
