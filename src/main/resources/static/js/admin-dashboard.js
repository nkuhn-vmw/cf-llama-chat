// Admin dashboard home — active user count card
(function () {
    fetch('/api/admin/active-users')
        .then(function (res) {
            if (!res.ok) throw new Error('Failed to load active users');
            return res.json();
        })
        .then(function (data) {
            document.getElementById('activeUsersCount').textContent =
                data.count != null ? data.count : data;
        })
        .catch(function () {
            document.getElementById('activeUsersCount').textContent = '--';
        });
})();
