// Phòng chờ (chế độ giọng nói): kiểm tra hỗ trợ trình duyệt, xin quyền micro và hiển thị mức âm lượng.

const panel = document.querySelector('[data-mic-check]');

if (panel) {
    const status = panel.querySelector('[data-mic-status]');
    const meter = panel.querySelector('[data-mic-meter]');
    const testButton = panel.querySelector('[data-mic-test]');
    const fallback = panel.querySelector('[data-text-fallback]');
    const startLink = document.querySelector('[data-start-link]');

    let stream = null;
    let context = null;
    let frameId = null;
    let heardVoice = false;

    const setStatus = (text, tone) => {
        status.textContent = text;
        status.dataset.tone = tone;
    };

    const supportProblem = () => {
        if (!window.isSecureContext) {
            return 'Trình duyệt chỉ cho phép dùng micro trên kết nối an toàn (HTTPS hoặc localhost).';
        }
        if (!navigator.mediaDevices?.getUserMedia) {
            return 'Trình duyệt của bạn không hỗ trợ ghi âm từ micro.';
        }
        if (!window.AudioContext || !window.AudioWorkletNode) {
            return 'Trình duyệt của bạn không hỗ trợ xử lý âm thanh. Hãy dùng Chrome hoặc Edge mới nhất.';
        }
        return null;
    };

    const stop = () => {
        cancelAnimationFrame(frameId);
        stream?.getTracks().forEach(track => track.stop());
        context?.close().catch(() => {});
        stream = null;
        context = null;
        meter.style.width = '0%';
    };

    const showFallback = message => {
        setStatus(message, 'error');
        testButton.hidden = true;
        fallback.hidden = false;
    };

    const measure = analyser => {
        const samples = new Float32Array(analyser.fftSize);

        const loop = () => {
            analyser.getFloatTimeDomainData(samples);
            let sum = 0;
            for (const sample of samples) sum += sample * sample;
            const level = Math.min(1, Math.sqrt(sum / samples.length) * 4);

            meter.style.width = `${Math.round(level * 100)}%`;

            if (!heardVoice && level > 0.15) {
                heardVoice = true;
                setStatus('Micro hoạt động tốt. Bạn đã sẵn sàng!', 'ok');
            }

            frameId = requestAnimationFrame(loop);
        };

        loop();
    };

    testButton.addEventListener('click', async () => {
        if (stream) {
            stop();
            testButton.textContent = 'Kiểm tra micro';
            return;
        }

        try {
            stream = await navigator.mediaDevices.getUserMedia({
                audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
            });
        } catch (error) {
            const message = error?.name === 'NotAllowedError'
                ? 'Bạn chưa cho phép dùng micro. Hãy bật quyền micro cho trang này rồi thử lại.'
                : error?.name === 'NotFoundError'
                    ? 'Không tìm thấy micro. Hãy kết nối micro rồi thử lại.'
                    : 'Không thể sử dụng micro trên trình duyệt này.';
            setStatus(message, 'error');
            fallback.hidden = false;
            return;
        }

        context = new AudioContext();
        const analyser = context.createAnalyser();
        analyser.fftSize = 2048;
        context.createMediaStreamSource(stream).connect(analyser);

        heardVoice = false;
        testButton.textContent = 'Dừng kiểm tra';
        setStatus('Hãy nói thử vài câu...', 'pending');
        measure(analyser);
    });

    // Giải phóng micro trước khi vào phòng phỏng vấn.
    startLink?.addEventListener('click', stop);
    window.addEventListener('pagehide', stop);

    const problem = supportProblem();
    if (problem) {
        showFallback(problem);
    }
}
