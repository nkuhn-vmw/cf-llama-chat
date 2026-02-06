// Apply saved theme
const savedTheme = localStorage.getItem('theme') || 'dark';
document.body.setAttribute('data-theme', savedTheme);

// Animate bar charts
function animateBars(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const bars = container.querySelectorAll('.bar-fill');
    let maxValue = 0;

    bars.forEach(bar => {
        const value = parseFloat(bar.dataset.value) || 0;
        if (value > maxValue) maxValue = value;
    });

    if (maxValue === 0) return;

    bars.forEach(bar => {
        const value = parseFloat(bar.dataset.value) || 0;
        const percentage = (value / maxValue) * 100;
        setTimeout(() => {
            bar.style.width = percentage + '%';
        }, 100);
    });
}

// Animate bars in a specific container
function animateBarsInContainer(container) {
    const bars = container.querySelectorAll('.bar-fill');
    let maxValue = 0;

    bars.forEach(bar => {
        const value = parseFloat(bar.dataset.value) || 0;
        if (value > maxValue) maxValue = value;
    });

    if (maxValue === 0) return;

    bars.forEach(bar => {
        const value = parseFloat(bar.dataset.value) || 0;
        const percentage = (value / maxValue) * 100;
        bar.style.width = percentage + '%';
    });
}

// Show time period for tokens by model
function showTimePeriod(period, clickedTab) {
    // Hide all periods
    document.querySelectorAll('[id^="tokensByPeriod-"]').forEach(el => {
        el.style.display = 'none';
    });

    // Show selected period
    const container = document.getElementById('tokensByPeriod-' + period);
    if (container) {
        container.style.display = 'flex';
        animateBarsInContainer(container);
    }

    // Update tab styling
    document.querySelectorAll('.time-period-tab').forEach(tab => {
        tab.classList.remove('active');
    });
    if (clickedTab) {
        clickedTab.classList.add('active');
    }
}

// Clear all metrics
async function clearAllMetrics() {
    if (!confirm('Are you sure you want to clear ALL usage metrics? This action cannot be undone.')) {
        return;
    }

    try {
        const response = await fetch('/api/admin/metrics', {
            method: 'DELETE'
        });

        if (response.ok) {
            const data = await response.json();
            alert(`Successfully cleared ${data.recordsDeleted} metric records.`);
            window.location.reload();
        } else {
            const error = await response.json();
            alert('Failed to clear metrics: ' + (error.error || 'Unknown error'));
        }
    } catch (error) {
        console.error('Error clearing metrics:', error);
        alert('Failed to clear metrics. Please try again.');
    }
}

// Animate all charts on page load
document.addEventListener('DOMContentLoaded', () => {
    // Chat metrics charts
    animateBars('userTokensChart');
    animateBars('userResponseTimeChart');
    animateBars('userTpsChart');
    animateBars('tokensByPeriod-all');
    animateBars('globalTpsChart');
    animateBars('globalTtftChart');

    // Embedding metrics charts
    animateBars('userEmbeddingChart');
    animateBars('globalEmbeddingChart');
    animateBars('globalEmbeddingTimeChart');
});

// Bind event listeners (CSP-compliant, no inline handlers)
document.querySelectorAll('.time-period-tab').forEach(tab => {
    tab.addEventListener('click', function() {
        showTimePeriod(this.dataset.period, this);
    });
});

document.querySelector('.danger-btn')?.addEventListener('click', clearAllMetrics);
