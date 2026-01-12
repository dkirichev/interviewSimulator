// Navigation Handler
const views = {
    setup: document.getElementById('setup-view'),
    interview: document.getElementById('interview-view'),
    report: document.getElementById('report-view')
};

function switchView(viewName) {
    Object.values(views).forEach(el => {
        el.classList.remove('active');
    });

    const target = views[viewName];
    setTimeout(() => target.classList.add('active'), 50);
}

function handleStart(e) {
    e.preventDefault();
    const role = document.getElementById('job-position').value;
    document.getElementById('session-role').innerText = role;
    
    switchView('interview');
    startInterviewSimulation();
}

function restartApp() {
    document.getElementById('setup-form').reset();
    switchView('setup');
    
    const connectionOverlay = document.getElementById('connection-overlay');
    connectionOverlay.style.display = 'flex';
    connectionOverlay.style.opacity = '1';
}
