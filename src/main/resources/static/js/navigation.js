// Navigation Handler
const views = {
    setup: document.getElementById('setup-view'),
    interview: document.getElementById('interview-view'),
    report: document.getElementById('report-view')
};

// Report data storage
let reportData = null;

function switchView(viewName) {
    Object.values(views).forEach(el => {
        el.classList.remove('active');
    });

    const target = views[viewName];
    setTimeout(() => target.classList.add('active'), 50);
}

function handleStart(e) {
    e.preventDefault();
    
    // Get form values
    const candidateName = document.getElementById('candidate-name').value.trim();
    const positionSelect = document.getElementById('job-position');
    const customPosition = document.getElementById('custom-position');
    
    let position = positionSelect.value;
    if (position === 'custom' && customPosition) {
        position = customPosition.value.trim() || 'Software Developer';
    }
    
    const difficulty = document.querySelector('input[name="difficulty"]:checked').value;
    
    // Store session data for WebSocket connection
    currentSession = {
        candidateName: candidateName || 'Candidate',
        position: position,
        difficulty: difficulty
    };
    
    // Update UI
    document.getElementById('session-role').innerText = position;
    
    // Clear previous transcript
    clearLiveTranscript();
    
    // Switch to interview view
    switchView('interview');
    startInterviewSimulation();
}

function displayReport(data) {
    reportData = data;
    
    // Update score display
    const scoreEl = document.getElementById('final-score');
    if (scoreEl) {
        scoreEl.innerText = data.overallScore || 0;
    }
    
    // Update score circle (SVG)
    updateScoreCircle(data.overallScore || 0);
    
    // Update verdict badge
    updateVerdictBadge(data.verdict || 'MAYBE');
    
    // Update detailed scores
    updateDetailedScores(data);
    
    // Update strengths and improvements
    updateFeedbackLists(data);
    
    // Update transcript
    updateTranscriptDisplay(data.transcript || '');
    
    // Update session ID
    const reportIdEl = document.getElementById('report-id');
    if (reportIdEl && data.sessionId) {
        reportIdEl.innerText = data.sessionId.substring(0, 8);
    }
}

function updateScoreCircle(score) {
    const circle = document.querySelector('#report-view svg circle:nth-child(2)');
    if (circle) {
        // Calculate stroke-dashoffset based on score (440 is full circle)
        const offset = 440 - (440 * score / 100);
        circle.style.strokeDashoffset = offset;
        
        // Update color based on score
        if (score >= 80) {
            circle.classList.remove('text-blue-500', 'text-yellow-500', 'text-red-500');
            circle.classList.add('text-green-500');
        } else if (score >= 60) {
            circle.classList.remove('text-green-500', 'text-yellow-500', 'text-red-500');
            circle.classList.add('text-blue-500');
        } else if (score >= 40) {
            circle.classList.remove('text-green-500', 'text-blue-500', 'text-red-500');
            circle.classList.add('text-yellow-500');
        } else {
            circle.classList.remove('text-green-500', 'text-blue-500', 'text-yellow-500');
            circle.classList.add('text-red-500');
        }
    }
}

function updateVerdictBadge(verdict) {
    const badgeEl = document.querySelector('#report-view .mt-6 span');
    if (!badgeEl) return;
    
    const verdictMap = {
        'STRONG_HIRE': { text: 'STRONG HIRE', class: 'bg-green-500/10 text-green-500 border-green-500/20' },
        'HIRE': { text: 'HIRE', class: 'bg-blue-500/10 text-blue-500 border-blue-500/20' },
        'MAYBE': { text: 'MAYBE', class: 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20' },
        'NO_HIRE': { text: 'NO HIRE', class: 'bg-red-500/10 text-red-500 border-red-500/20' }
    };
    
    const config = verdictMap[verdict] || verdictMap['MAYBE'];
    badgeEl.innerText = config.text;
    badgeEl.className = `px-3 py-1 rounded-full text-xs font-bold border ${config.class}`;
}

function updateDetailedScores(data) {
    // Update score bars
    const commScore = data.communicationScore || 0;
    const techScore = data.technicalScore || 0;
    const confScore = data.confidenceScore || 0;
    
    // Update text displays
    const commScoreEl = document.getElementById('comm-score');
    const techScoreEl = document.getElementById('tech-score');
    const confScoreEl = document.getElementById('conf-score');
    
    if (commScoreEl) commScoreEl.innerText = commScore;
    if (techScoreEl) techScoreEl.innerText = techScore;
    if (confScoreEl) confScoreEl.innerText = confScore;
    
    // Update progress bars with animation delay
    setTimeout(() => {
        const commBar = document.getElementById('comm-bar');
        const techBar = document.getElementById('tech-bar');
        const confBar = document.getElementById('conf-bar');
        
        if (commBar) commBar.style.width = `${commScore}%`;
        if (techBar) techBar.style.width = `${techScore}%`;
        if (confBar) confBar.style.width = `${confScore}%`;
    }, 100);
    
    // Update detailed analysis text
    const analysisEl = document.getElementById('detailed-analysis');
    if (analysisEl && data.detailedAnalysis) {
        analysisEl.innerText = data.detailedAnalysis;
    }
}

function updateFeedbackLists(data) {
    // Parse strengths and improvements (they come as JSON strings)
    let strengths = [];
    let improvements = [];
    
    try {
        strengths = typeof data.strengths === 'string' ? JSON.parse(data.strengths) : data.strengths;
    } catch (e) {
        strengths = [data.strengths];
    }
    
    try {
        improvements = typeof data.improvements === 'string' ? JSON.parse(data.improvements) : data.improvements;
    } catch (e) {
        improvements = [data.improvements];
    }
    
    // Update strengths list
    const strengthsList = document.querySelector('#report-view .bg-green-500\\/20').closest('.bg-slate-800\\/50').querySelector('ul');
    if (strengthsList && Array.isArray(strengths)) {
        strengthsList.innerHTML = strengths.map(s => 
            `<li class="flex items-center gap-2"><i class="fa-solid fa-check text-green-500"></i> ${s}</li>`
        ).join('');
    }
    
    // Update improvements list
    const improvementsList = document.querySelector('#report-view .bg-yellow-500\\/20').closest('.bg-slate-800\\/50').querySelector('ul');
    if (improvementsList && Array.isArray(improvements)) {
        improvementsList.innerHTML = improvements.map(i => 
            `<li class="flex items-center gap-2"><i class="fa-solid fa-triangle-exclamation text-yellow-500"></i> ${i}</li>`
        ).join('');
    }
}

function updateTranscriptDisplay(transcript) {
    const container = document.getElementById('transcript-container');
    if (!container) return;
    
    container.innerHTML = '';
    
    if (!transcript || transcript.trim() === '') {
        container.innerHTML = '<p class="text-slate-500 italic">No transcript available.</p>';
        return;
    }
    
    // Parse transcript lines
    const lines = transcript.split('\n').filter(line => line.trim());
    
    lines.forEach(line => {
        const div = document.createElement('div');
        div.className = 'flex gap-4';
        
        if (line.includes('[Interviewer]:')) {
            const text = line.replace('[Interviewer]:', '').trim();
            div.innerHTML = `
                <div class="font-bold text-blue-400 w-16 shrink-0">AI</div>
                <div class="text-slate-300">${text}</div>
            `;
        } else if (line.includes('[Candidate]:')) {
            const text = line.replace('[Candidate]:', '').trim();
            div.innerHTML = `
                <div class="font-bold text-green-400 w-16 shrink-0">You</div>
                <div class="text-slate-300">${text}</div>
            `;
        } else {
            div.innerHTML = `<div class="text-slate-400">${line}</div>`;
        }
        
        container.appendChild(div);
    });
}

function restartApp() {
    // Disconnect WebSocket
    disconnectWebSocket();
    
    // Reset form
    document.getElementById('setup-form').reset();
    
    // Reset custom position visibility
    const customPositionContainer = document.getElementById('custom-position-container');
    if (customPositionContainer) {
        customPositionContainer.classList.add('hidden');
    }
    
    // Clear report data
    reportData = null;
    
    // Reset connection overlay
    const connectionOverlay = document.getElementById('connection-overlay');
    if (connectionOverlay) {
        connectionOverlay.style.display = 'flex';
        connectionOverlay.style.opacity = '1';
        connectionOverlay.querySelector('p').innerText = 'Establishing Secure Websocket...';
    }
    
    switchView('setup');
}

// Handle custom position selection
function setupPositionSelect() {
    const positionSelect = document.getElementById('job-position');
    const customContainer = document.getElementById('custom-position-container');
    
    if (positionSelect && customContainer) {
        positionSelect.addEventListener('change', function() {
            if (this.value === 'custom') {
                customContainer.classList.remove('hidden');
            } else {
                customContainer.classList.add('hidden');
            }
        });
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', setupPositionSelect);

