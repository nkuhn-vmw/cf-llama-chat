// Check URL params for error/logout/registered messages
const params = new URLSearchParams(window.location.search);
if (params.has('error')) {
    document.getElementById('errorMessage').classList.remove('hidden');
}
if (params.has('logout')) {
    document.getElementById('logoutMessage').classList.remove('hidden');
}
if (params.has('registered')) {
    document.getElementById('registeredMessage').classList.remove('hidden');
}

// Check if SSO is available
fetch('/auth/provider')
    .then(response => response.json())
    .then(data => {
        if (data.ssoEnabled) {
            document.getElementById('ssoSection').classList.remove('hidden');
        }
    })
    .catch(err => console.log('Could not check SSO status'));

// Apply saved theme preference
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);
