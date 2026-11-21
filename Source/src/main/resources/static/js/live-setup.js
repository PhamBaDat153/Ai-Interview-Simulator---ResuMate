// Trang thiết lập phỏng vấn trực tiếp: chọn file, đếm ký tự, ẩn/hiện giọng đọc, màn hình chờ.

const MAX_FILE_BYTES = 5 * 1024 * 1024;

const form = document.querySelector('.live-setup-form');
const loadingOverlay = document.querySelector('[data-loading-overlay]');
const submitButton = document.querySelector('[data-submit-button]');

const formatSize = bytes => bytes < 1024 * 1024
    ? `${Math.max(1, Math.round(bytes / 1024))} KB`
    : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

const formatNumber = value => value.toLocaleString('vi-VN');

// Khu vực chọn / kéo thả file.
document.querySelectorAll('input[type="file"]').forEach(input => {
    const drop = document.querySelector(`[data-file-drop="${input.id}"]`);
    const title = drop.querySelector('[data-file-title]');
    const help = drop.querySelector('[data-file-help]');
    const meta = drop.querySelector('[data-file-meta]');
    const remove = drop.querySelector('[data-file-remove]');
    const defaultTitle = title.textContent;
    const defaultHelp = help.textContent;

    const reset = event => {
        if (event) event.preventDefault();
        input.value = '';
        drop.classList.remove('has-file', 'dragging', 'has-error');
        title.textContent = defaultTitle;
        help.textContent = defaultHelp;
        meta.textContent = '';
    };

    const showFile = file => {
        if (!file) return;

        if (file.size > MAX_FILE_BYTES) {
            reset();
            drop.classList.add('has-error');
            meta.textContent = `"${file.name}" vượt quá 5MB, vui lòng chọn file khác.`;
            return;
        }

        drop.classList.remove('has-error');
        drop.classList.add('has-file');
        title.textContent = '✓ File đã được chọn';
        help.textContent = file.name;
        meta.textContent = formatSize(file.size);
    };

    input.addEventListener('change', () => showFile(input.files[0]));
    ['dragenter', 'dragover'].forEach(type => drop.addEventListener(type, event => {
        event.preventDefault();
        drop.classList.add('dragging');
    }));
    ['dragleave', 'drop'].forEach(type => drop.addEventListener(type, event => {
        event.preventDefault();
        drop.classList.remove('dragging');
    }));
    drop.addEventListener('drop', event => {
        const file = event.dataTransfer.files[0];
        if (file) {
            input.files = event.dataTransfer.files;
            showFile(file);
        }
    });
    remove.addEventListener('click', reset);
});

// Bộ đếm ký tự cho ô CV / JD.
document.querySelectorAll('textarea[data-char-limit]').forEach(textarea => {
    const counter = document.querySelector(`[data-char-counter="${textarea.id}"]`);
    const limit = Number(textarea.dataset.charLimit);

    const update = () => {
        const length = textarea.value.length;
        counter.textContent = `${formatNumber(length)} / ${formatNumber(limit)} ký tự`;
        counter.classList.toggle('near-limit', length > limit * 0.9);
    };

    textarea.addEventListener('input', update);
    update();
});

// Chỉ hiện lựa chọn giọng đọc khi chọn hình thức giọng nói.
const voiceField = document.querySelector('[data-voice-field]');
const modeInputs = document.querySelectorAll('[data-mode-input]');

const updateVoiceField = () => {
    const selected = document.querySelector('[data-mode-input]:checked');
    voiceField.hidden = !selected || selected.value !== 'VOICE';
};

modeInputs.forEach(input => input.addEventListener('change', updateVoiceField));
updateVoiceField();

// Màn hình chờ trong lúc AI tạo kế hoạch phỏng vấn.
form.addEventListener('submit', () => {
    loadingOverlay.classList.add('active');
    loadingOverlay.setAttribute('aria-hidden', 'false');
    submitButton.disabled = true;
    submitButton.innerHTML = '<span class="button-spinner"></span> Đang chuẩn bị...';
});

// Khi người dùng quay lại trang bằng nút Back, bỏ trạng thái đang tải.
window.addEventListener('pageshow', event => {
    if (event.persisted) {
        loadingOverlay.classList.remove('active');
        loadingOverlay.setAttribute('aria-hidden', 'true');
        submitButton.disabled = false;
        submitButton.innerHTML = 'Tạo buổi phỏng vấn <span>→</span>';
    }
});
