const form = document.getElementById('registerForm');
const usernameInput = document.getElementById('username');
const emailInput = document.getElementById('email');
const passwordInput = document.getElementById('password');
const confirmPasswordInput = document.getElementById('confirmPassword');
const invitationCodeInput = document.getElementById('invitationCode');
const submitBtn = document.getElementById('submitBtn');
const errorMessage = document.getElementById('errorMessage');
const successMessage = document.getElementById('successMessage');

let invitationRequired = false;
let usernameTimeout = null;
let emailTimeout = null;

// Apply saved theme
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

// Check auth provider settings
fetch('/auth/provider')
    .then(response => response.json())
    .then(data => {
        invitationRequired = data.invitationRequired;
        if (invitationRequired) {
            document.getElementById('invitationGroup').style.display = 'block';
            document.getElementById('invitationNotice').style.display = 'block';
        }
    })
    .catch(err => console.log('Could not check auth provider'));

// Check if this is the first user
fetch('/auth/check-username?username=_test_')
    .then(() => {
        // If we can check, we might be first user - this is just a hint
    })
    .catch(() => {});

// Username validation
usernameInput.addEventListener('input', () => {
    clearTimeout(usernameTimeout);
    const username = usernameInput.value.trim();
    const hint = document.getElementById('usernameHint');

    if (username.length < 3) {
        usernameInput.classList.remove('success', 'error');
        hint.textContent = '3-30 characters, letters, numbers, underscores, hyphens';
        hint.classList.remove('error', 'success');
        return;
    }

    if (!/^[a-zA-Z0-9_-]{3,30}$/.test(username)) {
        usernameInput.classList.remove('success');
        usernameInput.classList.add('error');
        hint.textContent = 'Invalid characters. Use only letters, numbers, underscores, hyphens.';
        hint.classList.remove('success');
        hint.classList.add('error');
        return;
    }

    usernameTimeout = setTimeout(() => {
        fetch(`/auth/check-username?username=${encodeURIComponent(username)}`)
            .then(response => response.json())
            .then(data => {
                if (data.available) {
                    usernameInput.classList.remove('error');
                    usernameInput.classList.add('success');
                    hint.textContent = 'Username is available';
                    hint.classList.remove('error');
                    hint.classList.add('success');
                } else {
                    usernameInput.classList.remove('success');
                    usernameInput.classList.add('error');
                    hint.textContent = 'Username is already taken';
                    hint.classList.remove('success');
                    hint.classList.add('error');
                }
            })
            .catch(() => {
                hint.textContent = 'Could not verify username';
                hint.classList.remove('success', 'error');
            });
    }, 500);
});

// Email validation
emailInput.addEventListener('input', () => {
    clearTimeout(emailTimeout);
    const email = emailInput.value.trim();
    const hint = document.getElementById('emailHint');

    if (!email) {
        emailInput.classList.remove('success', 'error');
        hint.textContent = '';
        return;
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        emailInput.classList.remove('success');
        emailInput.classList.add('error');
        hint.textContent = 'Invalid email format';
        hint.classList.remove('success');
        hint.classList.add('error');
        return;
    }

    emailTimeout = setTimeout(() => {
        fetch(`/auth/check-email?email=${encodeURIComponent(email)}`)
            .then(response => response.json())
            .then(data => {
                if (data.available) {
                    emailInput.classList.remove('error');
                    emailInput.classList.add('success');
                    hint.textContent = 'Email is available';
                    hint.classList.remove('error');
                    hint.classList.add('success');
                } else {
                    emailInput.classList.remove('success');
                    emailInput.classList.add('error');
                    hint.textContent = 'Email is already registered';
                    hint.classList.remove('success');
                    hint.classList.add('error');
                }
            })
            .catch(() => {
                hint.textContent = '';
            });
    }, 500);
});

// Password validation
passwordInput.addEventListener('input', () => {
    const password = passwordInput.value;
    const lengthEl = document.getElementById('pwLength');

    if (password.length >= 6) {
        lengthEl.classList.add('valid');
    } else {
        lengthEl.classList.remove('valid');
    }

    validateConfirmPassword();
});

// Confirm password validation
confirmPasswordInput.addEventListener('input', validateConfirmPassword);

function validateConfirmPassword() {
    const password = passwordInput.value;
    const confirmPassword = confirmPasswordInput.value;
    const hint = document.getElementById('confirmHint');

    if (!confirmPassword) {
        confirmPasswordInput.classList.remove('success', 'error');
        hint.textContent = '';
        return;
    }

    if (password === confirmPassword) {
        confirmPasswordInput.classList.remove('error');
        confirmPasswordInput.classList.add('success');
        hint.textContent = 'Passwords match';
        hint.classList.remove('error');
        hint.classList.add('success');
    } else {
        confirmPasswordInput.classList.remove('success');
        confirmPasswordInput.classList.add('error');
        hint.textContent = 'Passwords do not match';
        hint.classList.remove('success');
        hint.classList.add('error');
    }
}

// Form submission
form.addEventListener('submit', async (e) => {
    e.preventDefault();

    errorMessage.classList.remove('show');
    successMessage.classList.remove('show');

    const username = usernameInput.value.trim();
    const email = emailInput.value.trim();
    const displayName = document.getElementById('displayName').value.trim();
    const password = passwordInput.value;
    const confirmPassword = confirmPasswordInput.value;
    const invitationCode = invitationCodeInput.value.trim();

    // Validate
    if (password !== confirmPassword) {
        errorMessage.textContent = 'Passwords do not match';
        errorMessage.classList.add('show');
        return;
    }

    if (password.length < 6) {
        errorMessage.textContent = 'Password must be at least 6 characters';
        errorMessage.classList.add('show');
        return;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = 'Creating account...';

    try {
        const response = await fetch('/auth/register', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username,
                email: email || null,
                displayName: displayName || null,
                password,
                invitationCode: invitationRequired ? invitationCode : null
            })
        });

        const data = await response.json();

        if (data.success) {
            successMessage.textContent = data.message || 'Registration successful! Redirecting to login...';
            successMessage.classList.add('show');
            form.reset();

            setTimeout(() => {
                window.location.href = '/login.html?registered=true';
            }, 1500);
        } else {
            errorMessage.textContent = data.error || 'Registration failed';
            errorMessage.classList.add('show');
        }
    } catch (err) {
        errorMessage.textContent = 'An error occurred. Please try again.';
        errorMessage.classList.add('show');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Create Account';
    }
});
