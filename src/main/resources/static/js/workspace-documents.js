// workspace/documents page — upload/list/delete + YouTube import, safe DOM
(function () {
    function showToast(msg, type) {
        if (window.showAdminToast) window.showAdminToast(msg, type || 'info');
    }

    function createEl(tag, className, textContent) {
        var el = document.createElement(tag);
        if (className) el.className = className;
        if (textContent) el.textContent = textContent;
        return el;
    }

    function formatFileSize(bytes) {
        if (!bytes) return '0 B';
        var sizes = ['B', 'KB', 'MB', 'GB'];
        var i = Math.floor(Math.log(bytes) / Math.log(1024));
        return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
    }

    function renderDocuments(docs) {
        var list = document.getElementById('documentList');
        list.textContent = '';
        document.getElementById('totalDocs').textContent = docs.length;
        var totalChunks = docs.reduce(function (sum, d) { return sum + (d.chunkCount || 0); }, 0);
        document.getElementById('totalChunks').textContent = totalChunks;

        if (docs.length === 0) {
            list.appendChild(createEl('p', '', 'No documents uploaded yet.'));
            list.firstChild.style.color = 'var(--text-muted)';
            return;
        }

        docs.forEach(function (d) {
            var card = createEl('div', 'doc-card');
            card.dataset.id = d.id;

            var info = createEl('div', 'doc-card-info');
            info.appendChild(createEl('div', 'doc-card-name', d.originalFilename || d.filename || 'Document'));

            var meta = createEl('div', 'doc-card-meta');
            meta.appendChild(createEl('span', '', formatFileSize(d.fileSize || 0)));
            meta.appendChild(createEl('span', '', (d.chunkCount || 0) + ' chunks'));
            meta.appendChild(createEl('span', '',
                (d.uploadedAt || d.createdAt) ? new Date(d.uploadedAt || d.createdAt).toLocaleDateString() : ''));
            info.appendChild(meta);

            var actions = createEl('div', 'doc-card-actions');
            var deleteBtn = createEl('button', 'action-btn danger delete-doc-btn', 'Delete');
            deleteBtn.dataset.id = d.id;
            actions.appendChild(deleteBtn);

            card.appendChild(info);
            card.appendChild(actions);
            list.appendChild(card);
        });
    }

    async function loadDocuments() {
        try {
            var resp = await fetch('/api/documents');
            if (!resp.ok) throw new Error();
            renderDocuments(await resp.json());
        } catch (e) {
            console.error('Failed to load documents:', e);
        }
    }

    async function uploadFiles(files) {
        if (!files || files.length === 0) return;
        var progress = document.getElementById('uploadProgress');
        var fill = document.getElementById('progressFill');
        var text = document.getElementById('progressText');
        progress.style.display = '';

        for (var i = 0; i < files.length; i++) {
            var file = files[i];
            text.textContent = 'Uploading ' + file.name + '...';
            fill.style.width = Math.round((i / files.length) * 100) + '%';

            var formData = new FormData();
            formData.append('file', file);
            try {
                var resp = await fetch('/api/documents/upload', { method: 'POST', body: formData });
                if (!resp.ok) throw new Error('Upload failed for ' + file.name);
                fill.style.width = Math.round(((i + 1) / files.length) * 100) + '%';
            } catch (e) {
                showToast('Failed to upload ' + file.name, 'error');
            }
        }

        fill.style.width = '100%';
        text.textContent = 'Upload complete!';
        setTimeout(function () { progress.style.display = 'none'; fill.style.width = '0%'; }, 2000);
        showToast(files.length + ' file(s) uploaded', 'success');
        loadDocuments();
    }

    async function deleteDocument(id) {
        if (!confirm('Delete this document?')) return;
        try {
            await fetch('/api/documents/' + id, { method: 'DELETE' });
            showToast('Document deleted', 'success');
            loadDocuments();
        } catch (e) {
            showToast('Failed to delete document', 'error');
        }
    }

    async function importYouTube() {
        var url = document.getElementById('youtubeUrlInput').value.trim();
        if (!url) { showToast('Please enter a YouTube URL', 'error'); return; }
        try {
            var resp = await fetch('/api/documents/youtube', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ url: url })
            });
            if (!resp.ok) throw new Error();
            document.getElementById('youtubeUrlInput').value = '';
            showToast('YouTube transcript imported', 'success');
            loadDocuments();
        } catch (e) {
            showToast('Failed to import YouTube transcript', 'error');
        }
    }

    var uploadBox = document.getElementById('uploadBox');
    var fileInput = document.getElementById('fileInput');

    uploadBox.addEventListener('click', function () { fileInput.click(); });
    fileInput.addEventListener('change', function () {
        uploadFiles(fileInput.files);
        fileInput.value = '';
    });

    uploadBox.addEventListener('dragover', function (e) {
        e.preventDefault();
        uploadBox.classList.add('drag-over');
    });
    uploadBox.addEventListener('dragleave', function () { uploadBox.classList.remove('drag-over'); });
    uploadBox.addEventListener('drop', function (e) {
        e.preventDefault();
        uploadBox.classList.remove('drag-over');
        uploadFiles(e.dataTransfer.files);
    });

    document.getElementById('importYoutubeBtn').addEventListener('click', importYouTube);
    document.getElementById('documentList').addEventListener('click', function (e) {
        var btn = e.target.closest('.delete-doc-btn');
        if (btn) deleteDocument(btn.dataset.id);
    });

    loadDocuments();
})();
