// Navigation Handler
const views = {
    setup: document.getElementById('setup-view'),
    interview: document.getElementById('interview-view'),
    report: document.getElementById('report-view')
};

// Report data storage
let reportData = null;

// CV data storage
let cvExtractedText = null;
let selectedCvFile = null;

function switchView(viewName) {
    Object.values(views).forEach(el => {
        el.classList.remove('active');
    });

    const target = views[viewName];
    setTimeout(() => target.classList.add('active'), 50);
}

// Loading overlay functions
function showLoading(status, substatus) {
    const overlay = document.getElementById('loading-overlay');
    const statusEl = document.getElementById('loading-status');
    const substatusEl = document.getElementById('loading-substatus');
    
    if (overlay) {
        overlay.classList.remove('hidden');
        overlay.classList.add('flex');
    }
    if (statusEl) statusEl.innerText = status || 'Preparing interview...';
    if (substatusEl) substatusEl.innerText = substatus || 'Please wait';
}


function hideLoading() {
    const overlay = document.getElementById('loading-overlay');
    if (overlay) {
        overlay.classList.add('hidden');
        overlay.classList.remove('flex');
    }
}


function updateLoadingStep(step, status) {
    const stepEl = document.getElementById('step-' + step);
    if (!stepEl) return;
    
    stepEl.classList.remove('text-slate-500', 'bg-slate-800', 'text-green-400', 'bg-green-500/20', 'text-blue-400', 'bg-blue-500/20', 'text-red-400', 'bg-red-500/20');
    
    if (status === 'active') {
        stepEl.classList.add('text-blue-400', 'bg-blue-500/20');
    } else if (status === 'done') {
        stepEl.classList.add('text-green-400', 'bg-green-500/20');
    } else if (status === 'error') {
        stepEl.classList.add('text-red-400', 'bg-red-500/20');
    } else {
        stepEl.classList.add('text-slate-500', 'bg-slate-800');
    }
}


async function handleStart(e) {
    e.preventDefault();
    
    // Get form values
    const candidateName = document.getElementById('candidate-name').value.trim();
    const positionSelect = document.getElementById('job-position');
    const customPosition = document.getElementById('custom-position');
    const languageRadio = document.querySelector('input[name="language"]:checked');
    const positionError = document.getElementById('position-error');
    
    let position = positionSelect.value;
    
    // Frontend validation
    if (!candidateName) {
        alert('Please enter your name');
        return;
    }
    
    if (!position || position === '') {
        if (positionError) {
            positionError.classList.remove('hidden');
        }
        positionSelect.focus();
        return;
    } else {
        if (positionError) {
            positionError.classList.add('hidden');
        }
    }
    
    if (position === 'custom' && customPosition) {
        const customValue = customPosition.value.trim();
        if (!customValue) {
            alert('Please enter a custom position');
            customPosition.focus();
            return;
        }
        position = customValue;
    }
    
    const difficulty = document.querySelector('input[name="difficulty"]:checked').value;
    const language = languageRadio ? languageRadio.value : 'en';
    
    // Show/hide CV step indicator
    const cvStepEl = document.getElementById('step-cv');
    if (cvStepEl) {
        if (selectedCvFile) {
            cvStepEl.classList.remove('hidden');
        } else {
            cvStepEl.classList.add('hidden');
        }
    }
    
    // Show loading overlay
    showLoading('Preparing interview...', 'Checking microphone access');
    updateLoadingStep('mic', 'active');
    
    try {
        // Step 1: Check if we already have microphone permission, then request if needed
        showLoading('Checking microphone...', 'Please allow access if prompted');
        const micStream = await checkAndRequestMicrophone();
        updateLoadingStep('mic', 'done');
        
        // Step 2: Upload CV if provided
        let cvText = null;
        if (selectedCvFile) {
            showLoading('Processing CV...', 'Extracting text from your resume');
            updateLoadingStep('cv', 'active');
            
            try {
                cvText = await uploadAndProcessCv(selectedCvFile);
                updateLoadingStep('cv', 'done');
            } catch (cvError) {
                console.error('CV processing failed:', cvError);
                updateLoadingStep('cv', 'error');
                // Continue without CV - it's optional
            }
        }
        
        // Step 3: Store session data for WebSocket connection
        currentSession = {
            candidateName: candidateName || 'Candidate',
            position: position,
            difficulty: difficulty,
            language: language,
            cvText: cvText
        };
        
        // Store mic stream for later use
        window.preinitializedMicStream = micStream;
        
        // Update UI
        document.getElementById('session-role').innerText = position;
        
        // Clear previous transcript
        clearLiveTranscript();
        
        // Step 4: Connect to backend
        showLoading('Connecting...', 'Establishing secure connection');
        updateLoadingStep('connect', 'active');
        
        // Switch to interview view (loading will hide when connected)
        switchView('interview');
        startInterviewSimulation();
        
    } catch (error) {
        console.error('Failed to start interview:', error);
        hideLoading();
        
        if (error.name === 'NotAllowedError') {
            alert('Microphone access is required for the interview. Please allow microphone access and try again.');
        } else {
            alert('Failed to start interview: ' + error.message);
        }
    }
}


async function checkAndRequestMicrophone() {
    // Check if we already have permission using the Permissions API
    try {
        if (navigator.permissions && navigator.permissions.query) {
            const permissionStatus = await navigator.permissions.query({ name: 'microphone' });
            console.log('Microphone permission status:', permissionStatus.state);
            
            if (permissionStatus.state === 'granted') {
                // Already have permission, get the stream directly
                console.log('Microphone permission already granted, getting stream...');
                return await navigator.mediaDevices.getUserMedia({
                    audio: {
                        channelCount: 1,
                        sampleRate: 16000,
                        echoCancellation: true,
                        noiseSuppression: true,
                        autoGainControl: true
                    }
                });
            }
        }
    } catch (permError) {
        // Permissions API not supported or failed, fall through to request
        console.log('Permissions API not available or failed:', permError);
    }
    
    // Need to request permission
    return await navigator.mediaDevices.getUserMedia({
        audio: {
            channelCount: 1,
            sampleRate: 16000,
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        }
    });
}


async function requestMicrophonePermission() {
    return await navigator.mediaDevices.getUserMedia({
        audio: {
            channelCount: 1,
            sampleRate: 16000,
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        }
    });
}


async function uploadAndProcessCv(file) {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await fetch('/api/cv/upload', {
        method: 'POST',
        body: formData
    });
    
    const result = await response.json();
    
    if (!result.success) {
        throw new Error(result.error || 'Failed to process CV');
    }
    
    return result.text;
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
    
    // Clear CV data
    clearCvFile();
    cvExtractedText = null;
    selectedCvFile = null;
    
    // Clear report data
    reportData = null;
    
    // Reset connection overlay
    const connectionOverlay = document.getElementById('connection-overlay');
    if (connectionOverlay) {
        connectionOverlay.style.display = 'flex';
        connectionOverlay.style.opacity = '1';
        connectionOverlay.querySelector('p').innerText = 'Establishing Secure Websocket...';
    }
    
    // Reset loading steps
    ['mic', 'cv', 'connect'].forEach(step => updateLoadingStep(step, 'pending'));
    
    switchView('setup');
}


// CV file handling
function setupCvUpload() {
    const cvInput = document.getElementById('cv-file');
    const uploadArea = document.getElementById('cv-upload-area');
    
    if (!cvInput || !uploadArea) return;
    
    cvInput.addEventListener('change', handleCvFileSelect);
    
    // Drag and drop
    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('border-blue-500', 'bg-blue-500/10');
    });
    
    uploadArea.addEventListener('dragleave', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('border-blue-500', 'bg-blue-500/10');
    });
    
    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('border-blue-500', 'bg-blue-500/10');
        
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            cvInput.files = files;
            handleCvFileSelect({ target: cvInput });
        }
    });
}


function handleCvFileSelect(e) {
    const file = e.target.files[0];
    const errorEl = document.getElementById('cv-error');
    const uploadContent = document.getElementById('cv-upload-content');
    const fileInfo = document.getElementById('cv-file-info');
    const fileName = document.getElementById('cv-file-name');
    
    // Reset error
    if (errorEl) {
        errorEl.classList.add('hidden');
        errorEl.innerText = '';
    }
    
    if (!file) {
        clearCvFile();
        return;
    }
    
    // Validate file type
    const validTypes = ['application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'];
    if (!validTypes.includes(file.type)) {
        if (errorEl) {
            errorEl.innerText = 'Invalid file type. Please upload a PDF or DOCX file.';
            errorEl.classList.remove('hidden');
        }
        clearCvFile();
        return;
    }
    
    // Validate file size (2MB)
    if (file.size > 2 * 1024 * 1024) {
        if (errorEl) {
            errorEl.innerText = 'File too large. Maximum size is 2MB.';
            errorEl.classList.remove('hidden');
        }
        clearCvFile();
        return;
    }
    
    // Store file and update UI
    selectedCvFile = file;
    
    if (uploadContent) uploadContent.classList.add('hidden');
    if (fileInfo) fileInfo.classList.remove('hidden');
    if (fileName) fileName.innerText = file.name;
}


function clearCvFile(e) {
    if (e) e.stopPropagation();
    
    selectedCvFile = null;
    
    const cvInput = document.getElementById('cv-file');
    const uploadContent = document.getElementById('cv-upload-content');
    const fileInfo = document.getElementById('cv-file-info');
    const errorEl = document.getElementById('cv-error');
    
    if (cvInput) cvInput.value = '';
    if (uploadContent) uploadContent.classList.remove('hidden');
    if (fileInfo) fileInfo.classList.add('hidden');
    if (errorEl) {
        errorEl.classList.add('hidden');
        errorEl.innerText = '';
    }
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
document.addEventListener('DOMContentLoaded', () => {
    setupPositionSelect();
    setupCvUpload();
});

