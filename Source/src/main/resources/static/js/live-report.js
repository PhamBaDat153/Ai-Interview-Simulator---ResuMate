// Trang báo cáo: in / lưu PDF và mở toàn bộ mục chi tiết khi in.

const printButton = document.querySelector('[data-print-button]');
let closedDetails = [];

if (printButton) {
    printButton.addEventListener('click', () => window.print());
}

// Khi in, mở tất cả các mục <details> để PDF có đủ nội dung, in xong thì trả lại như cũ.
window.addEventListener('beforeprint', () => {
    closedDetails = [...document.querySelectorAll('details:not([open])')];
    closedDetails.forEach(details => { details.open = true; });
});

window.addEventListener('afterprint', () => {
    closedDetails.forEach(details => { details.open = false; });
    closedDetails = [];
});
